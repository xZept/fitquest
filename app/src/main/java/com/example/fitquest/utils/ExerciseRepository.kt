package com.example.fitquest.utils

import android.content.Context
import android.util.Log
import com.example.fitquest.models.Exercise
import com.example.fitquest.models.ExerciseVideo
import kotlin.math.max
import kotlin.random.Random

object ExerciseRepository {

    // Load exercises (matches your CSV header)
    fun loadExercises(context: Context, assetName: String = "exercise_dataset.csv"): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val input = context.assets.open(assetName)
        input.bufferedReader().use { br ->
            val header = br.readLine() ?: return emptyList()
            val headerCols = header.parseCsvLine().map { it.trim().lowercase() }
            val idx = headerCols.withIndex().associate { it.value to it.index }

            fun col(name: String) = idx[name.lowercase()] ?: -1

            val iId      = col("exercise_id")
            val iName    = col("exercise_name")
            val iEquip   = col("equipment")
            val iVar     = col("variation")
            val iUtil    = col("utility")
            val iMech    = col("mechanics")
            val iForce   = col("force")
            val iTargets = col("target_muscles")
            val iMain    = col("main_muscle")
            val iRestr   = col("restricted_for")
            val iDiff    = col("difficulty_(1-5)")
            val iType    = col("type")
            val iDesc    = col("description")

            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val row = raw.parseCsvLine()
                fun safe(i: Int) = if (i in row.indices) row[i].trim() else ""

                val id    = safe(iId).toIntOrNull() ?: return@forEach
                val name  = safe(iName)
                val equip = safe(iEquip)
                val vari  = safe(iVar)
                val util  = safe(iUtil)
                val mech  = safe(iMech)
                val force = safe(iForce)
                val targ  = safe(iTargets)
                val main  = safe(iMain)
                val restr = safe(iRestr)
                    .split(Regex("[,/]"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val diff  = safe(iDiff).toIntOrNull() ?: 1
                val type  = safe(iType)
                val desc  = safe(iDesc)

                list.add(
                    Exercise(
                        id = id,
                        name = name,
                        equipment = equip,
                        variation = vari,
                        utility = util,
                        mechanics = mech,
                        force = force,
                        targetMuscles = targ,
                        mainMuscle = main,
                        restrictedFor = restr,
                        difficulty = diff,
                        type = type,
                        description = desc
                    )
                )
            }
        }
        return list
    }

    // Load videos
    fun loadExerciseVideos(context: Context, assetName: String = "exercise_video_dataset.csv"): List<ExerciseVideo> {
        val out = mutableListOf<ExerciseVideo>()
        val input = context.assets.open(assetName)
        input.bufferedReader().use { br ->
            val header = br.readLine() ?: return emptyList()
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = raw.parseCsvLine()
                // expected: video_id,exercise_id,exercise_name,equipment,youtube_link
                val vid = cols.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val exId = cols.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val exName = cols.getOrNull(2) ?: ""
                val equip = cols.getOrNull(3) ?: ""
                val link = cols.getOrNull(4) ?: ""
                out.add(ExerciseVideo(vid, exId, exName, equip, link))
            }
        }
        return out
    }

    // --- Filters / helpers (kept simple and documented for team) ---

    // Equipment filter: returns same list if pref is blank or "gym"
    fun List<Exercise>.filterByEquipment(pref: String): List<Exercise> {
        if (pref.isBlank()) return this
        val p = pref.lowercase()
        fun String.containsAny(keys: List<String>): Boolean {
            val text = this.lowercase()
            return keys.any { key -> key.split(" ").all { text.contains(it) } }
        }
        return when {
            p.contains("bodyweight") || p.contains("body weight") -> {
                this.filter {
                    it.equipment.lowercase().contains("body") || it.type.lowercase() == "cardio"
                }
            }
            p.contains("home") -> {
                val banned = listOf("machine", "leg press", "smith machine", "treadmill", "elliptical")
                this.filter { ex ->
                    val e = ex.equipment.lowercase()
                    !banned.any { e.contains(it) }
                }
            }
            else -> this
        }
    }

    // Convert condition string to list and check against exercise.restrictedFor
    private val conditionKeywords = mapOf(
        "strength" to listOf("wrist", "carpal tunnel", "forearm"),
        "beginner friendly" to listOf("shoulder", "rotator cuff", "labral tear", "impingement"),
        "low impact" to listOf("elbow", "tendinitis"),
        "knee" to listOf("knee", "patellar", "acl"),
        "back" to listOf("back", "spine", "disc"),
        "neck" to listOf("neck"),
        "asthma" to listOf("asthma"),
        "heart" to listOf("heart", "cardiac", "hypertension"),
        "diabetes" to listOf("diabetes")
    )



    fun isSafeForUser(ex: Exercise, userConditions: List<String>): Boolean {
        val restrictions = ex.restrictedFor.map { it.lowercase() }
        return userConditions.none { cond ->
            val key = cond.lowercase().trim()
            val kws = conditionKeywords[key] ?: listOf(key)
            kws.any { kw -> restrictions.any { it.contains(kw) } }
        }
    }

    fun List<Exercise>.filterByDifficulty(activityLevel: String): List<Exercise> {
        val range = when (activityLevel.lowercase()) {
            "sedentary" -> 1..2
            "light" -> 1..2
            "moderate" -> 2..4
            "active" -> 3..5
            else -> 1..5
        }
        return this.filter { it.difficulty in range }
    }

    fun List<Exercise>.prioritizeByGoal(goal: String): List<Exercise> {
        val g = goal.lowercase()
        fun score(ex: Exercise): Int {
            var s = 0
            val mech = ex.mechanics.lowercase()
            val type = ex.type.lowercase()
            if (g.contains("build") || g.contains("muscle")) {
                if (mech == "compound") s += 3
                if (ex.utility.lowercase() == "basic") s += 2
            } else if (g.contains("lose") || g.contains("fat")) {
                if (type == "cardio") s += 3
                if (mech == "compound") s += 2
            } else if (g.contains("maintain") || g.contains("endurance")) {
                if (type == "cardio") s += 2
            }
            return s
        }
        return this.sortedByDescending { score(it) }
    }

    // split mapping
    fun splitMap(days: Int): List<String> = when (days) {
        1 -> listOf("Full Body")
        2 -> listOf("Upper", "Lower")
        3 -> listOf("Push", "Pull", "Legs")
        4 -> listOf("Upper", "Lower", "Upper", "Lower")
        5 -> listOf("Push", "Pull", "Legs", "Upper", "Lower")
        6 -> listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs")
        else -> listOf("Full Body")
    }

    // allocate to buckets (keeps simple, deterministic selection)
    fun allocateToBuckets(pool: List<Exercise>, dayBuckets: List<String>): Map<String, List<Exercise>> {
        val result = mutableMapOf<String, MutableList<Exercise>>()
        val used = mutableSetOf<Int>()

        fun addSome(name: String, pick: (Exercise) -> Boolean, minCount: Int = 5, maxCount: Int = 6, allowReuse: Boolean = false) {
            val wanted = max(minCount, 4)
            val poolToUse = if (allowReuse) pool else pool.filter { it.id !in used }
            val selected = poolToUse.filter(pick).shuffled(Random(System.currentTimeMillis())).take(maxCount).toMutableList()
            if (selected.size < wanted) {
                val fillers = poolToUse.filter { it.id !in selected.map { s -> s.id } }.take(wanted - selected.size)
                selected.addAll(fillers)
            }
            if (selected.isNotEmpty()) {
                result.getOrPut(name) { mutableListOf() }.addAll(selected)
                if (!allowReuse) used.addAll(selected.map { it.id })
            }
        }

        dayBuckets.forEachIndexed { idx, bucket ->
            val bucketName = if (dayBuckets.count { it == bucket } > 1) "$bucket ${idx + 1}" else bucket
            when (bucket.lowercase()) {
                "upper" -> addSome(bucketName, { ex -> listOf("chest","back","shoulder","arm").any { ex.mainMuscle.contains(it, true) } }, allowReuse = true)
                "lower", "legs" -> addSome(bucketName, { ex -> listOf("leg","glute","hamstring","quad","calf").any { ex.mainMuscle.contains(it, true) } }, allowReuse = true)
                "push" -> addSome(bucketName, { ex -> ex.force.equals("push", true) || listOf("chest","shoulder","triceps").any { ex.mainMuscle.contains(it, true) } })
                "pull" -> addSome(bucketName, { ex -> ex.force.equals("pull", true) || listOf("back","biceps").any { ex.mainMuscle.contains(it, true) } })
                "full body" -> addSome(bucketName, { ex -> ex.mechanics.equals("compound", true) || ex.type.equals("cardio", true) })
                else -> addSome(bucketName, { true })
            }
        }
        Log.d("ExerciseRepo", "Allocated buckets=${result.keys}")
        return result
    }

    fun getExerciseByName(context: Context, name: String): Exercise? {
        return loadExercises(context).find { it.name.equals(name, ignoreCase = true) }
    }

    fun getVideosByExercise(context: Context, name: String): List<ExerciseVideo> {
        return loadExerciseVideos(context).filter { it.exerciseName.equals(name, ignoreCase = true) }
    }

}
