package com.example.fitquest.repo

import android.content.Context
import android.util.Log
import com.opencsv.CSVReader
import java.io.InputStreamReader
import java.util.Locale

data class ExerciseHelp(
    val exerciseId: String? = null,
    val youtubeUrl: String? = null,
    val description: String? = null,
    val instructions: String? = null
)

object ExerciseHelpRepo {
    private const val TAG = "ExerciseHelpRepo"
    private const val FILE_VIDEOS = "exercise_video_dataset.csv"
    private const val FILE_INFO   = "exercises_info.csv"

    private var loaded = false
    private val byId   = mutableMapOf<String, ExerciseHelp>()
    private val byName = mutableMapOf<String, ExerciseHelp>()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val vids  = loadVideos(context)
                val infos = loadInfos(context)

                // Merge by exerciseId when possible
                val allIds = vids.keys + infos.keys
                allIds.forEach { id ->
                    val v = vids[id]
                    val i = infos[id]
                    val merged = ExerciseHelp(
                        exerciseId   = id,
                        youtubeUrl   = v?.second ?: null,
                        description  = i?.first ?: null,
                        instructions = i?.second ?: null
                    )
                    byId[id] = merged

                    val nameFromV = v?.first
                    val nameFromI = i?.third
                    val name = nameFromV ?: nameFromI
                    if (!name.isNullOrBlank()) {
                        byName[normalizeName(name)] = merged
                    }
                }
                loaded = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load CSVs", t)
            }
        }
    }

    fun find(context: Context, id: String?, name: String?): ExerciseHelp? {
        ensureLoaded(context)
        id?.let { byId[it] }?.let { return it }
        val nameKey = name?.let { normalizeName(it) }.orEmpty()
        return if (nameKey.isNotEmpty()) byName[nameKey] else null
    }

    // --- CSV loaders ---

    private fun loadVideos(context: Context): Map<String, Pair<String?, String?>> {
        val out = mutableMapOf<String, Pair<String?, String?>>()
        context.assets.open(FILE_VIDEOS).use { input ->
            CSVReader(InputStreamReader(input)).use { r ->
                val header = r.readNext() ?: return emptyMap()
                val cols = indexColumns(header)

                val idIdx   = firstOf(cols, "exercise_id", "id", "exerciseId", "exercise id")
                val nameIdx = firstOf(cols, "name", "exercise_name", "exercise")
                val urlIdx  = firstOf(
                    cols,
                    "youtube_url", "youtube_link", "youtube",
                    "video_url", "video_link", "video",
                    "url", "link"
                )

                var row = r.readNext()
                while (row != null) {
                    val id  = row.getOrNull(idIdx)?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        val nm  = row.getOrNull(nameIdx)?.trim()
                        val url = row.getOrNull(urlIdx)?.trim()
                        out[id] = nm to url
                    }
                    row = r.readNext()
                }
            }
        }
        return out
    }

    private fun loadInfos(context: Context): Map<String, Triple<String?, String?, String?>> {
        val out = mutableMapOf<String, Triple<String?, String?, String?>>()
        context.assets.open(FILE_INFO).use { input ->
            CSVReader(InputStreamReader(input)).use { r ->
                val header = r.readNext() ?: return emptyMap()
                val cols = indexColumns(header)

                val idIdx   = firstOf(cols, "exercise_id", "id", "exerciseId", "exercise id")
                val descIdx = firstOf(cols, "description", "desc")
                val instIdx = firstOf(cols, "instructions", "instruction", "how_to", "how to")
                val nameIdx = firstOf(cols, "name", "exercise_name", "exercise")

                var row = r.readNext()
                while (row != null) {
                    val id = row.getOrNull(idIdx)?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        val desc = row.getOrNull(descIdx)?.trim()
                        val inst = row.getOrNull(instIdx)?.trim()
                        val name = row.getOrNull(nameIdx)?.trim()
                        out[id] = Triple(desc, inst, name)
                    }
                    row = r.readNext()
                }
            }
        }
        return out
    }

    // --- helpers ---
    private fun normalizeName(s: String): String =
        s.lowercase(Locale.US).trim()
            .replace("\\s+".toRegex(), " ")

    private fun normHeader(h: String): String =
        h.lowercase(Locale.US).trim().replace("[^a-z0-9]".toRegex(), "")

    private fun indexColumns(header: Array<String>): Map<String, Int> =
        header.mapIndexed { i, h -> normHeader(h) to i }.toMap()


    private fun firstOf(map: Map<String, Int>, vararg keys: String): Int {
        for (k in keys) {
            val keyNorm = normHeader(k)
            map[keyNorm]?.let { return it }
            map.entries.firstOrNull { it.key.contains(keyNorm) }?.let { return it.value }
        }
        return -1
    }
}
