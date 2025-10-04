package com.example.fitquest.workout

import android.content.Context
import com.example.fitquest.models.QuestExercise
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

object gitWorkoutEngine {

    // ------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------

    fun defaultScheme(focus: String): Triple<Int, Int, Int> = when (focus.trim().lowercase()) {
        "hypertrophy" -> Triple(8, 12, 3)
        "strength"    -> Triple(4, 6, 5)
        "endurance"   -> Triple(12, 20, 3)
        else          -> Triple(8, 12, 3)
    }

    /**
     * Structured, slot-driven plan builder:
     * - Uses split-specific templates (required + optional slots)
     * - Scores candidates by pattern/mover match, focus, complexity, equipment
     * - Enforces diversity and guaranteed minimum items
     * Returns (initial list to preview/edit, addable pool).
     */
    fun buildStructuredPlan(
        context: Context,
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>,
        targetItems: Int = 8
    ): Pair<List<QuestExercise>, List<String>> {

        val (repMin, repMax, sets) = defaultScheme(focus)

        val all = loadCsv(context)
        val canonSplit = normSplit(split)
        val canonFocus = normFocus(focus)

        // Pre-filter by split & equipment only; focus is a scoring factor later.
        val filtered = all
            .asSequence()
            .filter { matchesSplit(it.split, canonSplit) }
            .filter { isAllowedByEquipment(it.equipment, ownedEquipCanonical) }
            .distinctBy { it.nameKey }
            .toList()

        // Template for this split
        val template = templateForSplit(canonSplit)

        // Diversity accounting
        val seenBase = mutableMapOf<String, Int>()
        val seenPattern = mutableMapOf<String, Int>()
        val seenMover = mutableMapOf<String, Int>()
        val chosen = mutableListOf<CsvExercise>()

        // Selection flow: required slots first, then optionals until targetItems
        fun tryFillSlot(slot: Slot, soft: Boolean = false) {
            if (chosen.size >= targetItems) return

            // Score & pick the best matching candidates for this slot
            val scored = filtered
                .asSequence()
                .filter { it !in chosen }
                .map { ex -> ex to scoreForSlot(ex, slot, canonFocus, ownedEquipCanonical) }
                .filter { it.second > 0.0 } // keep only helpful matches
                .sortedByDescending { it.second + Random.nextDouble(0.0, 0.05) } // tiny jitter
                .toList()

            for ((ex, _) in scored) {
                if (chosen.size >= targetItems) break
                if (!passesDiversity(ex, seenBase, seenPattern, seenMover, soft)) continue
                chosen += ex
                bumpDiversity(ex, seenBase, seenPattern, seenMover)
                if (countInSlot(chosen, slot) >= slot.count) break
            }
        }

        // Required first
        template.filter { it.required }.forEach { tryFillSlot(it, soft = false) }
        // Optional, balanced until target
        template.filter { !it.required }.forEach { tryFillSlot(it, soft = true) }

        // If we’re short, do a permissive top-up while keeping diversity reasonable.
        val MIN_ITEMS = maxOf(6, template.count { it.required }) // at least required slots or 6
        if (chosen.size < MIN_ITEMS) {
            val remaining = filtered.filter { it !in chosen }
                .sortedBy { ex ->
                    // prefer non-bodyweight if equipment exists
                    val isBw = ex.equipment.size == 1 && ex.equipment.first() == "bodyweight"
                    if (isBw) 1 else 0
                }

            // progressively relax the caps
            val relaxLevels = listOf(
                Caps(1, 2, 2),
                Caps(2, 2, 2),
                Caps(2, 3, 3),
                Caps(3, 4, 4)
            )
            for (caps in relaxLevels) {
                if (chosen.size >= MIN_ITEMS) break
                for (ex in remaining) {
                    if (chosen.size >= MIN_ITEMS) break
                    if (!passesDiversity(ex, seenBase, seenPattern, seenMover, soft = true, caps = caps)) continue
                    chosen += ex
                    bumpDiversity(ex, seenBase, seenPattern, seenMover)
                }
            }
        }

        // Build QuestExercise list
        val (finalRepMin, finalRepMax, finalSets) = defaultScheme(focus) // keep scheme consistent
        val start = chosen.take(targetItems).mapIndexed { idx, eX ->
            QuestExercise(
                name = eX.name,
                sets = finalSets,
                repsMin = finalRepMin,
                repsMax = finalRepMax,
                order = idx,
                equipment = eX.equipmentPretty,
                movementPattern = eX.movementPatternPretty,
                primaryMover = eX.primaryMoverPretty
            )
        }

        // Addable pool = all remaining (split+equipment filtered), deduped by base movement
        val usedBase = start.map { baseKey(it.name) }.toSet()
        val addPool = filtered
            .filter { baseKey(it.name) !in usedBase }
            .distinctBy { baseKey(it.name) }
            .map { it.name }
            .sorted()

        return start to addPool
    }

    // (kept, in case you still use it elsewhere)
    fun buildBasicPlan(
        context: Context,
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>
    ): List<QuestExercise> {
        val (repMin, repMax, sets) = defaultScheme(focus)
        val all = loadCsv(context)
        val canonSplit = normSplit(split)
        val canonFocus = normFocus(focus)

        val pool = all
            .asSequence()
            .filter { it.complexity == 1 }
            .filter { matchesSplit(it.split, canonSplit) }
            .filter { matchesFocusLoose(it.focus, canonFocus) }
            .filter { isAllowedByEquipment(it.equipment, ownedEquipCanonical) }
            .distinctBy { it.nameKey }
            .shuffled(Random(System.currentTimeMillis()))

        val maxItems = 6
        return pool.take(maxItems).mapIndexed { idx, e ->
            QuestExercise(
                name = e.name,
                sets = sets,
                repsMin = repMin,
                repsMax = repMax,
                order = idx,
                equipment = e.equipmentPretty,
                movementPattern = e.movementPatternPretty,
                primaryMover = e.primaryMoverPretty
            )
        }.toList()
    }

    // ------------------------------------------------------------
    // Slot templates & scoring
    // ------------------------------------------------------------

    private data class Slot(
        val key: String,
        val required: Boolean,
        val count: Int,
        val patterns: Set<String> = emptySet(),
        val movers: Set<String> = emptySet()
    )

    private data class Caps(val base: Int, val pattern: Int, val mover: Int)

    /** Per-split templates. Tune to taste. */
    private fun templateForSplit(split: String): List<Slot> = when (split) {
        "push" -> listOf(
            Slot("Horizontal Press", required = true,  count = 1, patterns = setOf("horizontal press"), movers = setOf("chest")),
            Slot("Vertical Press",   required = true,  count = 1, patterns = setOf("vertical press"),   movers = setOf("shoulders")),
            Slot("Secondary Press",  required = false, count = 1, patterns = setOf("horizontal press","vertical press"), movers = setOf("chest","shoulders")),
            Slot("Triceps",          required = false, count = 1, patterns = setOf("extension","pressdown"), movers = setOf("triceps")),
            Slot("Chest/Shoulder Iso", required=false, count = 1, movers = setOf("chest","shoulders"))
        )
        "pull" -> listOf(
            Slot("Horizontal Pull",  required = true,  count = 1, patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Vertical Pull",    required = true,  count = 1, patterns = setOf("vertical pull","pull-up"), movers = setOf("back","lats")),
            Slot("Row Variant",      required = false, count = 1, patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Rear Delt",        required = false, count = 1, movers = setOf("rear delts","shoulders")),
            Slot("Biceps",           required = false, count = 1, patterns = setOf("curl"), movers = setOf("biceps"))
        )
        "legs", "lower" -> listOf(
            Slot("Squat",            required = true,  count = 1, patterns = setOf("squat","knee dominant"), movers = setOf("quads")),
            Slot("Hinge",            required = true,  count = 1, patterns = setOf("hinge","hip hinge","deadlift"), movers = setOf("glutes","hamstrings")),
            Slot("Unilateral",       required = false, count = 1, patterns = setOf("lunge","split squat","step-up"), movers = setOf("glutes","quads")),
            Slot("Posterior Chain",  required = false, count = 1, movers = setOf("glutes","hamstrings")),
            Slot("Calves/Core",      required = false, count = 1, movers = setOf("calves","core","abs"))
        )
        "upper" -> listOf(
            Slot("Horizontal Press", required = true,  count = 1, patterns = setOf("horizontal press"), movers = setOf("chest")),
            Slot("Horizontal Pull",  required = true,  count = 1, patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Vertical Press",   required = false, count = 1, patterns = setOf("vertical press"), movers = setOf("shoulders")),
            Slot("Vertical Pull",    required = false, count = 1, patterns = setOf("vertical pull","pull-up"), movers = setOf("back","lats")),
            Slot("Arm/Delts",        required = false, count = 1, movers = setOf("biceps","triceps","rear delts","shoulders"))
        )
        "full body" -> listOf(
            Slot("Squat/Knee",       required = true,  count = 1, patterns = setOf("squat","knee dominant"), movers = setOf("quads")),
            Slot("Hinge",            required = true,  count = 1, patterns = setOf("hinge","hip hinge","deadlift"), movers = setOf("glutes","hamstrings")),
            Slot("Horizontal Press", required = false, count = 1, patterns = setOf("horizontal press"), movers = setOf("chest")),
            Slot("Horizontal Pull",  required = false, count = 1, patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Accessory",        required = false, count = 1, movers = setOf("core","abs","calves","rear delts","biceps","triceps"))
        )
        else -> templateForSplit("full body")
    }

    /** How many currently-chosen items plausibly satisfy this slot. */
    private fun countInSlot(chosen: List<CsvExercise>, slot: Slot): Int =
        chosen.count { ex ->
            val p = canonLabel(ex.movementPatternPretty)
            val m = canonLabel(ex.primaryMoverPretty)
            (slot.patterns.isEmpty() || (p != null && p in slot.patterns)) ||
                    (slot.movers.isEmpty() || (m != null && m in slot.movers))
        }

    /** Score candidate for a given slot. */
    private fun scoreForSlot(
        ex: CsvExercise,
        slot: Slot,
        focus: String,
        ownedEquip: Set<String>
    ): Double {
        var score = 0.0

        // Pattern / mover match
        val p = canonLabel(ex.movementPatternPretty)
        val m = canonLabel(ex.primaryMoverPretty)
        if (slot.patterns.isNotEmpty() && p != null && p in slot.patterns) score += 1.2
        if (slot.movers.isNotEmpty()   && m != null && m in slot.movers)  score += 1.0

        // Focus: prefer requested but allow others
        score += when (ex.focus) {
            focus -> 0.8
            "general", "any" -> 0.5
            else -> 0.3
        }

        // Complexity bias by focus (General/Hypertrophy ≈ 2, Strength ≈ 3)
        val targetCx = when (focus) {
            "strength" -> 3
            "hypertrophy" -> 2
            else -> 2
        }
        val cxPenalty = abs(ex.complexity - targetCx) * 0.25
        score += 0.8 - cxPenalty

        // Equipment: if user owns equipment, gently de-prioritize pure bodyweight
        val isBodyweightOnly = ex.equipment.size == 1 && ex.equipment.first() == "bodyweight"
        if (ownedEquip.any { it != "bodyweight" }) {
            if (isBodyweightOnly) score -= 0.2 else score += 0.1
        }

        // Small random jitter
        score += Random.nextDouble(0.0, 0.05)

        return score
    }

    // Diversity
    private fun passesDiversity(
        ex: CsvExercise,
        seenBase: Map<String, Int>,
        seenPattern: Map<String, Int>,
        seenMover: Map<String, Int>,
        soft: Boolean,
        caps: Caps = Caps(base = 1, pattern = 2, mover = 2)
    ): Boolean {
        val base = baseKey(ex.name)
        if ((seenBase[base] ?: 0) >= caps.base) return false
        val p = canonLabel(ex.movementPatternPretty)
        if (p != null && (seenPattern[p] ?: 0) >= caps.pattern) return false
        val m = canonLabel(ex.primaryMoverPretty)
        if (m != null && (seenMover[m] ?: 0) >= caps.mover) return false
        return true
    }

    private fun bumpDiversity(
        ex: CsvExercise,
        seenBase: MutableMap<String, Int>,
        seenPattern: MutableMap<String, Int>,
        seenMover: MutableMap<String, Int>
    ) {
        val base = baseKey(ex.name)
        seenBase[base] = (seenBase[base] ?: 0) + 1
        canonLabel(ex.movementPatternPretty)?.let { k -> seenPattern[k] = (seenPattern[k] ?: 0) + 1 }
        canonLabel(ex.primaryMoverPretty)?.let { k -> seenMover[k] = (seenMover[k] ?: 0) + 1 }
    }

    // ------------------------------------------------------------
    // CSV model & loader
    // ------------------------------------------------------------

    private data class CsvExercise(
        val name: String,
        val equipment: List<String>,
        val equipmentPretty: String?,
        val movementPatternPretty: String?,
        val primaryMoverPretty: String?,
        val complexity: Int,
        val focus: String,
        val split: String,
        val nameKey: String
    )

    private fun loadCsv(context: Context): List<CsvExercise> {
        val out = mutableListOf<CsvExercise>()
        val splitter = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()

        context.assets.open("exercises.csv").use { stream ->
            BufferedReader(InputStreamReader(stream)).useLines { lines ->
                val it = lines.iterator()
                if (!it.hasNext()) return@useLines

                val header = it.next()
                val cols = header.split(splitter).map { c -> c.trim().trim('"').lowercase() }

                val idxName     = cols.indexOfFirst { c -> c.contains("exercise_name") || c == "name" }
                val idxEquip    = cols.indexOfFirst { c -> c == "equipment" || c == "required_equipment" }
                val idxMove     = cols.indexOfFirst { c -> c == "movement_pattern" || c == "movement" }
                val idxMover    = cols.indexOfFirst { c -> c == "primary_mover" || c == "mover" }
                val idxComplex  = cols.indexOfFirst { c -> c == "complexity" }
                val idxFocus    = cols.indexOfFirst { c -> c == "focus" }
                val idxSplit    = cols.indexOfFirst { c -> c == "split" }

                while (it.hasNext()) {
                    val row = it.next().split(splitter)
                    fun cell(i: Int): String? =
                        if (i in cols.indices) row.getOrNull(i)?.trim()?.trim('"')?.takeIf { s -> s.isNotEmpty() } else null

                    val name = cell(idxName) ?: continue
                    val equipPretty = cell(idxEquip)
                    val movePretty  = cell(idxMove)
                    val moverPretty = cell(idxMover)
                    val complexity  = cell(idxComplex)?.toIntOrNull() ?: 1
                    val focusCanon  = normFocus(cell(idxFocus) ?: "general")
                    val splitCanon  = normSplit(cell(idxSplit) ?: "full body")

                    val equipCanon = (equipPretty ?: "")
                        .split('|', '/', ';')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { canonEquip(it) }
                        .ifEmpty { listOf("bodyweight") }

                    out += CsvExercise(
                        name = name,
                        equipment = equipCanon,
                        equipmentPretty = equipPretty,
                        movementPatternPretty = movePretty,
                        primaryMoverPretty = moverPretty,
                        complexity = complexity,
                        focus = focusCanon,
                        split = splitCanon,
                        nameKey = norm(name)
                    )
                }
            }
        }
        return out
    }

    // ------------------------------------------------------------
    // Matching & canonicalization helpers
    // ------------------------------------------------------------

    private fun matchesSplit(csv: String, want: String): Boolean {
        if (want == "any") return true
        if (csv == "any") return true
        if (csv == want) return true
        return when (want) {
            "upper" -> csv in setOf("upper", "push", "pull")
            "lower" -> csv in setOf("lower", "legs")
            "full body" -> csv in setOf("full body", "any")
            else -> false
        }
    }

    /** Only for Basic; structured builder mixes focuses explicitly. */
    private fun matchesFocusLoose(csv: String, want: String): Boolean {
        if (want == "any" || want == "general") return true
        if (csv == "any" || csv == "general") return true
        return csv == want
    }

    private fun isAllowedByEquipment(required: List<String>, owned: Set<String>): Boolean {
        if (required.isEmpty()) return true
        if (required.size == 1 && required.first() == "bodyweight") return true
        if ("bodyweight" in required) return true
        return required.any { it in owned }
    }

    private fun norm(s: String): String =
        s.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun normSplit(s: String): String {
        val x = s.trim().lowercase()
        return when {
            x.contains("push") -> "push"
            x.contains("pull") -> "pull"
            x.contains("leg")  -> "legs"
            x.contains("upper")-> "upper"
            x.contains("lower")-> "lower"
            x.contains("full") && x.contains("body") -> "full body"
            else -> x
        }
    }

    private fun normFocus(s: String): String {
        val x = s.trim().lowercase()
        return when {
            x.startsWith("hyper") -> "hypertrophy"
            x.startsWith("streng")-> "strength"
            x.startsWith("endur") -> "endurance"
            x == "general" || x == "any" -> x
            else -> "general"
        }
    }

    private fun canonEquip(raw: String): String {
        val s = raw.trim().replace('_', ' ').replace(Regex("\\s+"), " ").lowercase()
        return when (s) {
            "dumbbells" -> "dumbbell"
            "kettlebells" -> "kettlebell"
            "resistance bands" -> "resistance band"
            "bands" -> "band"
            "barbel" -> "barbell"
            "pull up bar", "pullup bar" -> "pull-up bar"
            "trx" -> "trx"
            "bodyweight" -> "bodyweight"
            else -> s
        }
    }

    /** Collapse naming variants to a base movement key. */
    private fun baseKey(name: String): String {
        var s = name.lowercase().trim()
        s = s.replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^]]]*\\]"), "")
            .replace(Regex("\\{[^}]*\\}"), "")
        s = s.replace(Regex("\\btempo\\b.*$"), "")
            .replace(Regex("\\bunilateral\\b|\\bbilateral\\b"), "")
            .replace(Regex("\\bwide grip(?:/stance)?\\b|\\bnarrow grip\\b|\\bclose[- ]?grip\\b"), "")
            .replace(Regex("\\bdeficit\\b|\\bpaused?\\b|\\belevated\\b"), "")
        s = s.replace('-', ' ').replace('_', ' ')
            .replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("\\bpike\\s+push\\s*up\\b"), "push up")
            .replace(Regex("\\bknee\\s+push\\s*up\\b"), "push up")
            .replace(Regex("\\bpushup\\b"), "push up")
            .replace(Regex("\\bpush\\s*up\\b"), "push up")
        s = s.replace(Regex("\\bbench\\s+press\\b"), "bench press")
            .replace(Regex("\\boverhead\\s+press\\b"), "overhead press")
        return s
    }

    private fun canonLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return s.ifEmpty { null }
    }
}
