package com.example.fitquest.guides

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object ExerciseGuides {
    data class Guide(
        val exerciseId: Int,
        val name: String,
        val youtube: String?,
        val description: String?,
        val instructions: String?
    )

    private var loaded = false
    private val byName = HashMap<String, Guide>(512)

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        try {
            val infoRows = readCsv(ctx, "exercises_info.csv")
            val infoCols = infoRows.firstOrNull()?.keys?.associateBy { it.trim().lowercase(Locale.US) } ?: emptyMap()
            val idKeyInfo = infoCols.keys.firstOrNull { it.startsWith("exercise_id") } ?: "exercise_id (fk)"
            val nameKey   = infoCols.keys.firstOrNull { it.contains("exercise_name") } ?: "exercise_name"
            val descKey   = infoCols.keys.firstOrNull { it.contains("description") } ?: "description"
            val instrKey  = infoCols.keys.firstOrNull { it.contains("instruction") } ?: "instructions"
            val vidRows = readCsv(ctx, "exercise_video_dataset.csv")
            val vidCols = vidRows.firstOrNull()?.keys?.associateBy { it.trim().lowercase(Locale.US) } ?: emptyMap()
            val idKeyVid = vidCols.keys.firstOrNull { it.startsWith("exercise_id") } ?: "exercise_id"
            val linkKey  = vidCols.keys.firstOrNull { it.contains("youtube") } ?: "youtube_link"
            val idToYoutube = HashMap<Int, String?>()
            for (r in vidRows) {
                val id = r[idKeyVid]!!.trim().toIntOrNull() ?: continue
                val link = r[linkKey]?.trim().takeUnless { it.isNullOrBlank() }
                idToYoutube[id] = link
            }
            var added = 0
            for (r in infoRows) {
                val id = r[idKeyInfo]?.trim()?.toIntOrNull() ?: continue
                val name = r[nameKey]?.trim().orEmpty()
                if (name.isEmpty()) continue
                val guide = Guide(
                    exerciseId   = id,
                    name         = name,
                    youtube      = idToYoutube[id],
                    description  = r[descKey]?.trim(),
                    instructions = r[instrKey]?.trim()
                )
                byName[normalize(name)] = guide
                added++
            }
            loaded = true
            Log.d("ExerciseGuides", "Loaded $added guides.")
        } catch (t: Throwable) {
            Log.e("ExerciseGuides", "Failed to load guides", t)
        }
    }

    fun findByName(name: String): Guide? {
        val norm = normalize(name)
        byName[norm]?.let { return it }
        val candidates = byName.keys.filter { it.contains(norm) || norm.contains(it) }
        return candidates.firstOrNull()?.let { byName[it] }
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.US)
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun readCsv(ctx: Context, assetName: String): List<Map<String, String>> {
        val rows = mutableListOf<Map<String, String>>()
        ctx.assets.open(assetName).use { ins ->
            BufferedReader(InputStreamReader(ins)).use { br ->
                val header = parseCsvLine(br.readLine() ?: return emptyList()).map { it.trim() }
                br.lineSequence().forEach { line ->
                    if (line.isBlank()) return@forEach
                    val cols = parseCsvLine(line)
                    val fixed = if (cols.size < header.size)
                        cols + List(header.size - cols.size) { "" }
                    else cols.take(header.size)
                    rows += header.indices.associate { header[it].lowercase(Locale.US) to fixed[it] }
                }
            }
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out.add(sb.toString()); sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }
}
