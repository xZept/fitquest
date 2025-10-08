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
    private val byName = mutableMapOf<String, ExerciseHelp>() // fallback by name

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val vids = loadVideos(context)
                val infos = loadInfos(context)

                // Merge by exerciseId when possible
                val allIds = vids.keys + infos.keys
                allIds.forEach { id ->
                    val v = vids[id]
                    val i = infos[id]
                    val merged = ExerciseHelp(
                        exerciseId = id,
                        youtubeUrl = v?.second,
                        description = i?.first,
                        instructions = i?.second
                    )
                    byId[id] = merged
                    // Also index by *name* when available (from either file)
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

    /**
     * Find help by id (preferred) or by name (fallback).
     */
    fun find(context: Context, exerciseId: String?, exerciseName: String?): ExerciseHelp? {
        ensureLoaded(context)
        val idKey = exerciseId?.trim().orEmpty()
        if (idKey.isNotEmpty()) {
            byId[idKey]?.let { return it }
        }
        val nameKey = normalizeName(exerciseName ?: "")
        return if (nameKey.isNotEmpty()) byName[nameKey] else null
    }

    // --- CSV loaders ---

    /**
     * Returns map: exerciseId -> Pair(name, youtubeUrl)
     * Tries to auto-detect columns: exercise_id, name, youtube_url/video
     */
    private fun loadVideos(context: Context): Map<String, Pair<String?, String?>> {
        val out = mutableMapOf<String, Pair<String?, String?>>()
        context.assets.open(FILE_VIDEOS).use { input ->
            CSVReader(InputStreamReader(input)).use { r ->
                val header = r.readNext() ?: return emptyMap()
                val cols = indexColumns(header)
                val idIdx  = firstOf(cols, "exercise_id","id","exerciseId","exercise id")
                val nameIdx= firstOf(cols, "name","exercise_name","exercise")
                val urlIdx = firstOf(cols, "youtube_url","youtube","video","video_url","url")
                var row = r.readNext()
                while (row != null) {
                    val id   = row.getOrNull(idIdx)?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        val nm  = row.getOrNull(nameIdx)?.trim()
                        val url = row.getOrNull(urlIdx)?.trim()
                        out[id] = nm to url
                        if (!nm.isNullOrBlank()) {
                            // also index by name later when merging
                        }
                    }
                    row = r.readNext()
                }
            }
        }
        return out
    }

    /**
     * Returns map: exerciseId -> Triple(description, instructions, name?)
     * Tries to auto-detect columns: exercise_id, description, instructions, name
     */
    private fun loadInfos(context: Context): Map<String, Triple<String?, String?, String?>> {
        val out = mutableMapOf<String, Triple<String?, String?, String?>>()
        context.assets.open(FILE_INFO).use { input ->
            CSVReader(InputStreamReader(input)).use { r ->
                val header = r.readNext() ?: return emptyMap()
                val cols = indexColumns(header)
                val idIdx   = firstOf(cols, "exercise_id","id","exerciseId","exercise id")
                val descIdx = firstOf(cols, "description","desc")
                val instIdx = firstOf(cols, "instructions","instruction","how_to","how to")
                val nameIdx = firstOf(cols, "name","exercise_name","exercise")
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

    private fun indexColumns(header: Array<String>): Map<String, Int> =
        header.mapIndexed { i, h -> h.lowercase(Locale.US).trim() to i }.toMap()

    private fun firstOf(map: Map<String, Int>, vararg keys: String): Int {
        for (k in keys) {
            map[k.lowercase(Locale.US)]?.let { return it }
        }
        // if nothing matches, return -1 so getOrNull() is safe
        return -1
    }

    private fun normalizeName(s: String): String =
        s.lowercase(Locale.US).trim()
            .replace("\\s+".toRegex(), " ")
}
