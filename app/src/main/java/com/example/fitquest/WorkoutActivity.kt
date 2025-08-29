package com.example.fitquest

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.random.Random

class WorkoutActivity : AppCompatActivity() {

    // ---------- Data models ----------
    data class Exercise(
        val id: Int,
        val name: String,
        val equipment: String,
        val variation: String,
        val utility: String,
        val mechanics: String,
        val force: String,
        val targetMuscles: String,
        val mainMuscle: String,
        val restrictedFor: List<String>, // now parsed into a list
        val difficulty: Int,
        val type: String,
        val description: String
    )


    data class WorkoutSplit(
        val splitName: String,
        val exercises: List<Exercise>
    )


    // Holds all exercises loaded from CSV
    private lateinit var dataset: List<Exercise>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)



        // hides the system navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        // Find the nav icons
        val navDashboard = findViewById<ImageView>(R.id.nav_icon_dashboard)
        val navShop = findViewById<ImageView>(R.id.nav_icon_shop)
        val navProfile = findViewById<ImageView>(R.id.nav_icon_profile)
        val navWorkout = findViewById<ImageView>(R.id.nav_icon_workout)
        val navMacro = findViewById<ImageView>(R.id.nav_icon_macro)

        // Set click listeners
        navDashboard.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navShop.setOnClickListener {
            val intent = Intent(this, ShopActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        navWorkout.setOnClickListener {
            // You're already on dashboard, optionally do nothing
        }

        navMacro.setOnClickListener {
            val intent = Intent(this, MacroActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        val startWorkoutBtn: Button = findViewById(R.id.btn_start_workout)
        startWorkoutBtn.setOnClickListener {
            val intent = Intent(this, WorkoutGeneratorActivity::class.java)
            startActivity(intent)
        }



        val container = findViewById<LinearLayout>(R.id.workout_container)

        // ---- Read user inputs passed from generator screen ----
        val splitDays     = intent.getIntExtra("SPLIT_DAYS", 0)
        val goal          = intent.getStringExtra("GOAL") ?: ""
        val activityLevel = intent.getStringExtra("ACTIVITY_LEVEL") ?: ""
        val healthCond    = intent.getStringExtra("HEALTH_CONDITION")?.lowercase() ?: ""
        val equipmentPref = intent.getStringExtra("EQUIPMENT_PREF")?.lowercase() ?: ""

        if (splitDays <= 0) {
            // Nothing to generate yet
            return
        }

        container.removeAllViews()

        // ---- Load datasets ----
        val allExercises = loadExercisesFromAssets("exercise_dataset.csv")
            // If you keep videos in separate file:
            //.attachVideoUrls(loadVideoMapIfExists("workout_videos.csv"))

        // ---- Apply rules ----
        val filtered = allExercises
            .filterByEquipment(equipmentPref)
            .filter { ex -> isSafeForUser(ex, healthCond.split(",").map { it.trim() }) }
            .filterByDifficulty(activityLevel)
            .prioritizeByGoal(goal)


        // ---- Allocate per split ----
        val dayBuckets = splitMap(splitDays) // e.g., [Upper, Lower, Upper, Lower]
        val pool = filtered.shuffled(Random(System.currentTimeMillis()))
        val bucketed = allocateToBuckets(pool, dayBuckets)

// ---- Render horizontal day cards ----
        bucketed.forEach { (dayName, exercisesForDay) ->
            container.addView(buildDayCard(dayName, exercisesForDay))
        }


        val exerciseList = loadExercisesFromCSV()

        if (exerciseList.isEmpty()) {
            Log.e("UI_ERROR", "No exercises loaded from CSV!")
        } else {
            Log.d("UI_DATA", "Loaded ${exerciseList.size} exercises")
        }

        val workoutContainer = findViewById<LinearLayout>(R.id.workout_container)


    }


    private fun loadExercisesFromCSV(): List<String> {
        val exercises = mutableListOf<String>()
        try {
            val inputStream = assets.open("exercise_dataset.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))

            // Skip header
            var line = reader.readLine()

            while (reader.readLine().also { line = it } != null) {
                val tokens = line.split(",")
                if (tokens.size > 1) {
                    val exerciseName = tokens[1] // second column is exercise_name
                    exercises.add(exerciseName)
                    Log.d("CSV_LOAD", "Loaded exercise: $exerciseName")
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("CSV_ERROR", "Error reading CSV", e)
        }
        return exercises
    }

    // ---------- CSV loading ----------
    private fun loadExercisesFromAssets(exercise_dataset: String): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val input = assets.open(exercise_dataset)
        BufferedReader(InputStreamReader(input)).use { br ->
            val header = br.readLine() ?: return emptyList()
            val cols = header.parseCsvLine()
            val idx = cols.withIndex().associate { it.value.trim().lowercase() to it.index }

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
            val iVideo   = col("video_url") // optional if added later

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
                    .split(",", "/")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                val diff  = safe(iDiff).toIntOrNull() ?: 1
                val type  = safe(iType)
                val desc  = safe(iDesc)
                val vid   = if (iVideo >= 0) safe(iVideo).ifBlank { null } else null

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
                        restrictedFor = restr, // 👈 now part of model
                        difficulty = diff,
                        type = type,
                        description = desc,
                        // videoUrl = vid (if you later add this to your Exercise class)
                    )
                )
            }
        }
        return list
    }


    // parse CSV with quotes handling
    private fun String.parseCsvLine(): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < this.length) {
            val c = this[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < this.length && this[i + 1] == '"') {
                        sb.append('"'); i++ // escaped quote
                    } else inQuotes = !inQuotes
                }
                ',' -> if (!inQuotes) { out.add(sb.toString()); sb.setLength(0) } else sb.append(c)
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    // Optional second CSV for videos
//    private fun loadVideoMapIfExists(filename: String): Map<Int, String> {
//        return try {
//            val input = assets.open(filename)
//            val map = mutableMapOf<Int, String>()
//            BufferedReader(InputStreamReader(input)).use { br ->
//                val header = br.readLine() ?: return emptyMap()
//                val cols = header.parseCsvLine()
//                val idx = cols.withIndex().associate { it.value.trim().lowercase() to it.index }
//                val iId = idx["exercise_id"] ?: return emptyMap()
//                val iUrl = idx["video_url"] ?: return emptyMap()
//                br.lineSequence().forEach { raw ->
//                    if (raw.isBlank()) return@forEach
//                    val row = raw.parseCsvLine()
//                    val id = row.getOrNull(iId)?.trim()?.toIntOrNull() ?: return@forEach
//                    val url = row.getOrNull(iUrl)?.trim().orEmpty()
//                    if (url.isNotEmpty()) map[id] = url
//                }
//            }
//            map
//        } catch (_: Exception) {
//            emptyMap()
//        }
//    }

//    private fun List<Exercise>.attachVideoUrls(videoMap: Map<Int, String>): List<Exercise> {
//        if (videoMap.isEmpty()) return this
//        return this.map { ex ->
//            if (videoMap.containsKey(ex.id)) ex.copy(videoUrl = videoMap[ex.id]) else ex
//        }
//    }



    // ---------- Rules ----------

    // Equipment filter
    private fun List<Exercise>.filterByEquipment(pref: String): List<Exercise> {
        if (pref.isBlank()) return this
        val p = pref.lowercase()

        fun String.containsAny(keys: List<String>): Boolean {
            val text = this.lowercase()
            return keys.any { key ->
                key.split(" ").all { text.contains(it) }
            }
        }

        return when {
            // Bodyweight only
            p.contains("bodyweight") || p.contains("body weight") -> {
                this.filter {
                    it.equipment.containsAny(listOf("body weight")) ||
                            it.type.lowercase() == "cardio" && it.equipment.containsAny(listOf("jump rope", "none"))
                }
            }

            // Home Gym
            p.contains("home") -> {
                val allowed = listOf(
                    "dumbbell", "barbell", "kettlebell",
                    "bench", "resistance band", "pull-up bar",
                    "body weight", "yoga mat", "medicine ball"
                )
                val banned = listOf(
                    "smith machine", "cable", "lever",
                    "pec deck", "hack squat", "leg press",
                    "machine", "sled", "treadmill", "elliptical", "stationary bike"
                )

                this.filter { ex ->
                    val equip = ex.equipment.lowercase()
                    val isAllowed = equip.containsAny(allowed) || ex.type.lowercase() == "cardio" && equip.containsAny(listOf("jump rope", "none", "body weight"))
                    val isBanned = equip.containsAny(banned)
                    isAllowed && !isBanned
                }
            }

            // Gym Equipment → return everything
            else -> this
        }
    }

    // Map user conditions to broader keywords
    private val conditionKeywords = mapOf(
        "wrist" to listOf("wrist", "carpal tunnel", "forearm"),
        "shoulder" to listOf("shoulder", "rotator cuff", "labral tear", "impingement"),
        "elbow" to listOf("elbow", "tennis elbow", "golfer"),
        "knee" to listOf("knee", "patellar", "acl", "mcl"),
        "back" to listOf("back", "spine", "disc", "lumbar"),
        "neck" to listOf("neck", "cervical"),
        "asthma" to listOf("asthma", "breathing"),
        "heart" to listOf("heart", "cardiac", "hypertension"),
        "stroke" to listOf("stroke"),
        "thyroid" to listOf("thyroid"),
        "diabetes" to listOf("diabetes"),
        "vertigo" to listOf("vertigo", "dizzy"),
        "cancer" to listOf("cancer"),
        "anemia" to listOf("anemia"),
        "arthritis" to listOf("arthritis", "osteoarthritis", "joint pain")
    )

    fun isSafeForUser(ex: Exercise, userConditions: List<String>): Boolean {
        val restrictions = ex.restrictedFor.map { it.lowercase() }

        return userConditions.none { cond ->
            val key = cond.lowercase()
            val keywords = conditionKeywords[key] ?: listOf(key) // fallback: direct text match
            keywords.any { kw -> restrictions.contains(kw) }
        }
    }


    // Difficulty by activity level
    private fun List<Exercise>.filterByDifficulty(activityLevel: String): List<Exercise> {
        val range = when (activityLevel.lowercase()) {
            "sedentary"   -> 1..2
            "light"       -> 1..2
            "moderate"    -> 2..4
            "active"      -> 3..5
            else          -> 1..5
        }
        return this.filter { it.difficulty in range }
    }

    // Goal-based prioritization (re-orders list; still keeps variety)
    private fun List<Exercise>.prioritizeByGoal(goal: String): List<Exercise> {
        val g = goal.lowercase()
        fun score(ex: Exercise): Int {
            var s = 0
            val mech = ex.mechanics.lowercase()
            val type = ex.type.lowercase()
            when {
                g.contains("build") || g.contains("muscle") -> { // muscle gain
                    if (mech == "compound") s += 3
                    if (ex.utility.lowercase() == "basic") s += 2
                }
                g.contains("lose") || g.contains("fat") -> { // fat loss
                    if (type == "cardio") s += 3
                    if (mech == "compound") s += 2
                }
                g.contains("maintain") -> {
                    if (mech == "compound") s += 2
                    if (ex.utility.lowercase() == "auxiliary") s += 1
                }
                g.contains("endurance") -> {
                    if (type == "cardio") s += 3
                }
            }
            return s
        }
        return this.sortedByDescending { score(it) }
    }

    // Split mapping (your exact mapping)
    private fun splitMap(days: Int): List<String> = when (days) {
        1 -> listOf("Full Body")
        2 -> listOf("Upper", "Lower")
        3 -> listOf("Push", "Pull", "Legs")
        4 -> listOf("Upper", "Lower", "Upper", "Lower")
        5 -> listOf("Push", "Pull", "Legs", "Upper", "Lower")
        6 -> listOf("Push", "Pull", "Legs", "Push", "Pull", "Legs")
        7 -> listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Full Body")
        else -> listOf("Full Body")
    }

    private fun generateWorkoutPlan(split: Int, allExercises: List<Exercise>): Map<String, List<Exercise>> {
        val days = splitMap(split)  // <- now we reuse the helper

        // Utility to filter exercises for a given focus
        fun filterForDay(focus: String): List<Exercise> {
            return allExercises.filter { ex ->
                when (focus.lowercase()) {
                    "full body" -> true // allow everything
                    "upper" -> ex.mainMuscle.lowercase() in listOf("chest", "back", "shoulders", "arms")
                    "lower" -> ex.mainMuscle.lowercase() in listOf("legs", "glutes", "hamstrings", "quads", "calves")
                    "push"  -> ex.force.equals("push", ignoreCase = true)
                    "pull"  -> ex.force.equals("pull", ignoreCase = true)
                    "legs"  -> ex.mainMuscle.lowercase() in listOf("legs", "glutes", "hamstrings", "quads", "calves")
                    else -> ex.mainMuscle.equals(focus, ignoreCase = true)
                }
            }.shuffled().take(6) // limit per day (adjust as you li ke)
        }

        // Build plan: Day 1 - Focus → Exercises
        return days.mapIndexed { idx, focus ->
            "Day ${idx + 1} - $focus" to filterForDay(focus)
        }.toMap()
    }


    // Assign exercises to each bucket name
    private fun allocateToBuckets(pool: List<Exercise>, dayBuckets: List<String>): Map<String, List<Exercise>> {
        val result = mutableMapOf<String, MutableList<Exercise>>()
        val used = mutableSetOf<Int>()

        fun addSome(
            name: String,
            pick: (Exercise) -> Boolean,
            minCount: Int = 5,
            maxCount: Int = 6,
            allowReuse: Boolean = false
        ) {
            val wanted = max(minCount, 4)
            val poolToUse = if (allowReuse) pool else pool.filter { it.id !in used }

            val selected = poolToUse.filter(pick).shuffled().take(maxCount).toMutableList()

            if (selected.size < wanted) {
                val fillers = poolToUse
                    .filter { it.id !in selected.map { s -> s.id } }
                    .sortedBy { it.mechanics.lowercase() != "compound" }
                    .take(wanted - selected.size)
                selected.addAll(fillers)
            }

            if (selected.isNotEmpty()) {
                result.getOrPut(name) { mutableListOf() }.addAll(selected)
                if (!allowReuse) used.addAll(selected.map { it.id })
            }
        }

        dayBuckets.forEachIndexed { idx, bucket ->
            val bucketName = if (dayBuckets.count { it == bucket } > 1) {
                // If the split has duplicates, make names unique
                "$bucket ${idx + 1}"
            } else {
                bucket
            }

            when (bucket) {
                "Upper" -> addSome(bucketName, { ex ->
                    ex.mainMuscle.containsAny(listOf("Chest","Back","Shoulder","Arm","Biceps","Triceps","Core")) &&
                            !ex.mainMuscle.contains("Leg", ignoreCase = true)
                }, allowReuse = true)

                "Lower" -> addSome(bucketName, { ex ->
                    ex.mainMuscle.containsAny(listOf("Leg","Glute","Hamstring","Quad","Calf"))
                }, allowReuse = true)

                "Push" -> addSome(bucketName, { ex ->
                    ex.force.equals("push", true) || ex.mainMuscle.containsAny(listOf("Chest","Shoulder","Triceps"))
                })

                "Pull" -> addSome(bucketName, { ex ->
                    ex.force.equals("pull", true) || ex.mainMuscle.containsAny(listOf("Back","Biceps","Rear Delts"))
                })

                "Legs" -> addSome(bucketName, { ex ->
                    ex.mainMuscle.containsAny(listOf("Leg","Glute","Hamstring","Quad","Calf"))
                })

                "Chest","Back","Shoulders","Arms","Core" -> addSome(bucketName, { ex ->
                    ex.mainMuscle.contains(bucket, ignoreCase = true)
                })

                "Full Body" -> addSome(bucketName, { ex ->
                    ex.mechanics.equals("compound", true) || ex.type.equals("cardio", true)
                })

                else -> addSome(bucketName, { true })
            }
        }
        Log.d("WorkoutDebug", "Buckets = $dayBuckets, Result = ${result.keys}")
        return result
    }


    // Extension function for containsAny
    fun String.containsAny(keywords: List<String>): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }

    // ---------- UI builders ----------

    private var currentlyExpanded: View? = null
    private fun buildDayCard(dayName: String, exercises: List<Exercise>): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.active_quest_goblin) // your custom container
            val p = dp(24)
            setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(
                dp(340),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(2), dp(8), dp(2), dp(8))
            lp.gravity = Gravity.CENTER_HORIZONTAL  // <-- centers the container
            layoutParams = lp
        }

        val title = TextView(this).apply {
            text = dayName
            setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
            ellipsize = TextUtils.TruncateAt.END
            maxLines = 1
        }
        card.addView(title)

        exercises.forEach { ex ->
            val exerciseCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.item_container)
                val p = dp(12)
                setPadding(p, p, p, p)
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(dp(12), dp(6), dp(18), dp(6))
                layoutParams = lp
            }

            // Row: exercise name + expand button
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val name = TextView(this).apply {
                text = ex.name
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.white))
                textSize = 16f
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val expandBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.arrow_down_float) // default icon
                setBackgroundColor(Color.TRANSPARENT)
            }

            headerRow.addView(name)
            headerRow.addView(expandBtn)

            // Hidden details section
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            val detailText = TextView(this).apply {
                text = "Muscle: ${ex.mainMuscle}\nEquipment: ${ex.equipment}\nMechanics: ${ex.mechanics}\nForce: ${ex.force}"
                setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.darker_gray))
                textSize = 14f
            }
            details.addView(detailText)

            // Toggle expand/collapse with only one open at a time
            expandBtn.setOnClickListener {
                if (details.visibility == View.GONE) {
                    // Collapse currently expanded
                    currentlyExpanded?.let { expanded ->
                        expanded.visibility = View.GONE
                        val parent = expanded.parent as ViewGroup
                        val btn = parent.findViewById<ImageButton>(expandBtn.id)
                        btn?.setImageResource(android.R.drawable.arrow_down_float)
                    }

                    // Expand this one
                    details.visibility = View.VISIBLE
                    expandBtn.setImageResource(android.R.drawable.arrow_up_float)
                    currentlyExpanded = details
                } else {
                    // Collapse this one
                    details.visibility = View.GONE
                    expandBtn.setImageResource(android.R.drawable.arrow_down_float)
                    currentlyExpanded = null
                }
            }

            exerciseCard.addView(headerRow)
            exerciseCard.addView(details)
            card.addView(exerciseCard)
        }

        return card
    }

    // tiny “Watch” link if video available
//            (ex.videoUrl ?: "").takeIf { it.startsWith("http", true) }?.let { url ->
//                val btn = TextView(this).apply {
//                    text = "Watch"
//                    setTextColor(ContextCompat.getColor(this@WorkoutActivity, android.R.color.holo_blue_light))
//                    textSize = 13f
//                    setPadding(0, dp(2), 0, 0)
//                    setOnClickListener {
//                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
//                    }
//                }
//                card.addView(btn)
//            }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun groupExercisesBySplit(exercises: List<Exercise>): Map<String, List<Exercise>> {
        return exercises.groupBy { it.mainMuscle }
    }

    private fun displayWorkout(
        workoutPlan: Map<String, List<Exercise>>,
        workoutContainer: LinearLayout
    ) {
        val inflater = LayoutInflater.from(this)
        workoutContainer.removeAllViews()

        for ((day, exercises) in workoutPlan) {
            val dayView = inflater.inflate(R.layout.item_workout_day, workoutContainer, false)

            val dayTitle = dayView.findViewById<TextView>(R.id.tv_day_title)
            dayTitle.text = day

            val exerciseContainer = dayView.findViewById<LinearLayout>(R.id.exercise_list)

            for (exercise in exercises) {
                val exerciseText = TextView(this)
                exerciseText.text = "• ${exercise.name}"
                exerciseText.textSize = 16f
                exerciseContainer.addView(exerciseText)
            }

            workoutContainer.addView(dayView)
        }
    }

}
