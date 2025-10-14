package com.example.fitquest.workout

import android.content.Context
import com.example.fitquest.models.QuestExercise
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs
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
     * - Enforces diversity (STRICT 1-per-movement-pattern)
     * Returns (initial list to preview/edit, addable pool).
     */
    fun buildStructuredPlan(
        context: Context,
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>,
        targetItems: Int = 8
    ): Pair<List<QuestExercise>, List<String>> {

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

        // Diversity accounting (STRICT: pattern cap = 1)
        val seenBase = mutableMapOf<String, Int>()
        val seenPattern = mutableMapOf<String, Int>()
        val seenMover = mutableMapOf<String, Int>()
        val chosen = mutableListOf<CsvExercise>()

        fun tryFillSlot(slot: Slot, soft: Boolean = false) {
            if (chosen.size >= targetItems) return

            val scored = filtered
                .asSequence()
                .filter { it !in chosen }
                .map { ex -> ex to scoreForSlot(ex, slot, canonFocus, ownedEquipCanonical) }
                .filter { it.second > 0.0 }
                .sortedByDescending { it.second + Random.nextDouble(0.0, 0.05) }
                .toList()

            for ((ex, _) in scored) {
                if (chosen.size >= targetItems) break
                if (!passesDiversity(ex, seenBase, seenPattern, seenMover, soft)) continue
                chosen += ex
                bumpDiversity(ex, seenBase, seenPattern, seenMover)
                if (countInSlot(chosen, slot) >= slot.count) break
            }
        }

        // Required first, then optional until target
        template.filter { it.required }.forEach { tryFillSlot(it, soft = false) }
        template.filter { !it.required }.forEach { tryFillSlot(it, soft = true) }

        // Respect the number of slots / requested size (no padding to 6+)
        val minNeeded = minOf(targetItems, template.sumOf { it.count })
        if (chosen.size < minNeeded) {
            val remaining = filtered.filter { it !in chosen }
            // Relax caps progressively, but KEEP pattern cap at 1
            val relaxLevels = listOf(
                Caps(base = 1, pattern = 1, mover = 2),
                Caps(base = 2, pattern = 1, mover = 2),
                Caps(base = 2, pattern = 1, mover = 3),
                Caps(base = 3, pattern = 1, mover = 4)
            )
            for (caps in relaxLevels) {
                if (chosen.size >= minNeeded) break
                for (ex in remaining) {
                    if (chosen.size >= minNeeded) break
                    if (!passesDiversity(ex, seenBase, seenPattern, seenMover, soft = true, caps = caps)) continue
                    chosen += ex
                    bumpDiversity(ex, seenBase, seenPattern, seenMover)
                }
            }
        }

        val (finalRepMin, finalRepMax, finalSets) = defaultScheme(focus)
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

        // Addable pool = remaining unique base movements (still filtered)
        val usedBase = start.map { baseKey(it.name) }.toSet()
        val addPool = filtered
            .filter { baseKey(it.name) !in usedBase }
            .distinctBy { baseKey(it.name) }
            .map { it.name }
            .sorted()

        return start to addPool
    }

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

    /** Per-split templates using your new movement_pattern values (+ legacy synonyms). */
    private fun templateForSplit(split: String): List<Slot> = when (split) {
        "push" -> listOf(
            Slot("Horizontal Push", required = true,  count = 1,
                patterns = setOf("horizontal push", "horizontal press"), movers = setOf("chest")),
            Slot("Vertical Push",   required = true,  count = 1,
                patterns = setOf("vertical push", "vertical press"),   movers = setOf("shoulders")),
            Slot("Triceps (Elbow Extension)", required = false, count = 1,
                patterns = setOf("elbow extension", "extension", "pressdown"), movers = setOf("triceps")),
            Slot("Shoulder Isolation", required = false, count = 1,
                patterns = setOf("shoulder abduction"), movers = setOf("shoulders")),
            Slot("Finisher (Locomotion/Core)", required = false, count = 1,
                patterns = setOf("locomotion", "rotate", "anti rotate"), movers = emptySet())
        )
        "pull" -> listOf(
            Slot("Horizontal Pull", required = true,  count = 1,
                patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Vertical Pull",   required = true,  count = 1,
                patterns = setOf("vertical pull", "pull-up"), movers = setOf("back","lats")),
            Slot("Biceps (Elbow Flexion)", required = false, count = 1,
                patterns = setOf("elbow flexion", "curl"), movers = setOf("biceps")),
            Slot("Rear Delt / Scap", required = false, count = 1,
                patterns = setOf("scapular retraction", "shoulder horizontal abduction"),
                movers = setOf("rear delts","shoulders","back")),
            Slot("Finisher (Locomotion/Core)", required = false, count = 1,
                patterns = setOf("locomotion", "rotate", "anti rotate"), movers = emptySet())
        )
        "legs", "lower" -> listOf(
            Slot("Squat / Knee", required = true, count = 1,
                patterns = setOf("squat","knee dominant"), movers = setOf("quads")),
            Slot("Hinge / Hip",  required = true, count = 1,
                patterns = setOf("hinge","hip hinge","deadlift"), movers = setOf("glutes","hamstrings")),
            Slot("Unilateral / Accessory", required = false, count = 1,
                patterns = emptySet(), movers = setOf("glutes","quads","hamstrings")),
            Slot("Finisher (Locomotion/Core)", required = false, count = 1,
                patterns = setOf("locomotion", "rotate", "anti rotate"), movers = emptySet())
        )
        "upper" -> listOf(
            Slot("Horizontal Push", required = true, count = 1,
                patterns = setOf("horizontal push","horizontal press"), movers = setOf("chest")),
            Slot("Horizontal Pull", required = true, count = 1,
                patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Vertical Push", required = false, count = 1,
                patterns = setOf("vertical push","vertical press"), movers = setOf("shoulders")),
            Slot("Vertical Pull", required = false, count = 1,
                patterns = setOf("vertical pull","pull-up"), movers = setOf("back","lats")),
            Slot("Arms / Rear Delts", required = false, count = 1,
                patterns = setOf("elbow flexion","elbow extension","shoulder horizontal abduction","scapular retraction"),
                movers = setOf("biceps","triceps","rear delts","shoulders"))
        )
        "full body" -> listOf(
            Slot("Squat / Knee", required = true, count = 1,
                patterns = setOf("squat","knee dominant"), movers = setOf("quads")),
            Slot("Hinge / Hip", required = true, count = 1,
                patterns = setOf("hinge","hip hinge","deadlift"), movers = setOf("glutes","hamstrings")),
            Slot("Horizontal Push", required = false, count = 1,
                patterns = setOf("horizontal push","horizontal press"), movers = setOf("chest")),
            Slot("Horizontal Pull", required = false, count = 1,
                patterns = setOf("horizontal pull"), movers = setOf("back")),
            Slot("Accessory / Core / Locomotion", required = false, count = 1,
                patterns = setOf("elbow flexion","elbow extension","scapular retraction","shoulder horizontal abduction","locomotion","rotate","anti rotate"),
                movers = setOf("core","abs","calves","rear delts","biceps","triceps"))
        )
        else -> templateForSplit("full body")
    }

    /** How many currently-chosen items satisfy this slot. */
    private fun countInSlot(chosen: List<CsvExercise>, slot: Slot): Int =
        chosen.count { ex ->
            val p = canonLabel(ex.movementPatternPretty)
            val m = canonLabel(ex.primaryMoverPretty)
            val patternOk = slot.patterns.isNotEmpty() && p != null && p in slot.patterns
            val moverOk   = slot.movers.isNotEmpty()   && m != null && m in slot.movers
            (slot.patterns.isEmpty() || patternOk) && (slot.movers.isEmpty() || moverOk)
        }

    /** Score candidate for a given slot. */
    private fun scoreForSlot(
        ex: CsvExercise,
        slot: Slot,
        focus: String,
        ownedEquip: Set<String>
    ): Double {
        var score = 0.0

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

    // Diversity (STRICT 1-per-pattern)
    private fun passesDiversity(
        ex: CsvExercise,
        seenBase: Map<String, Int>,
        seenPattern: Map<String, Int>,
        seenMover: Map<String, Int>,
        soft: Boolean,
        caps: Caps = Caps(base = 1, pattern = 1, mover = 2)
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
        var s = raw.lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        // Map common synonyms so duplicates collapse
        if (s == "horizontal press") s = "horizontal push"
        if (s == "vertical press") s = "vertical push"
        if (s == "anti-rotate") s = "anti rotate"

        return s.ifEmpty { null }
    }

    // ------------------------------------------------------------
    // Smart replacement helpers
    // ------------------------------------------------------------

    /** Single removed item → ranked alternatives. */
    fun suggestAlternatives(
        context: Context,
        removedExerciseName: String,
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>,
        limit: Int = 20
    ): List<String> {
        val all = loadCsv(context)
        val canonSplit = normSplit(split)
        val canonFocus = normFocus(focus)

        val filtered = all
            .asSequence()
            .filter { matchesSplit(it.split, canonSplit) }
            .filter { isAllowedByEquipment(it.equipment, ownedEquipCanonical) }
            .distinctBy { it.nameKey }
            .toList()

        val refNameKey = norm(removedExerciseName)
        val refBase = baseKey(removedExerciseName)
        val ref: CsvExercise? =
            all.firstOrNull { it.nameKey == refNameKey }
                ?: all.firstOrNull { baseKey(it.name) == refBase }

        if (ref == null) {
            return filtered.map { it.name }.sorted().take(limit)
        }

        val refPat = canonLabel(ref.movementPatternPretty)
        val refMover = canonLabel(ref.primaryMoverPretty)

        var candidates = filtered
            .filter { it.nameKey != ref.nameKey }
            .filter { ex -> refPat != null && canonLabel(ex.movementPatternPretty) == refPat }

        if (candidates.size < limit && refMover != null) {
            val moverMatches = filtered
                .filter { it.nameKey != ref.nameKey }
                .filter { ex -> canonLabel(ex.primaryMoverPretty) == refMover }
            val seen = candidates.map { it.nameKey }.toMutableSet()
            moverMatches.forEach { if (seen.add(it.nameKey)) (candidates as MutableList).add(it) }
        }

        if (candidates.size < limit) {
            val rest = filtered.filter { it.nameKey != ref.nameKey && it !in candidates }
            (candidates as MutableList).addAll(rest)
        }

        fun jaccard(a: List<String>, b: List<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            val sa = a.toSet(); val sb = b.toSet()
            val inter = sa.intersect(sb).size.toDouble()
            val union = sa.union(sb).size.toDouble().coerceAtLeast(1.0)
            return inter / union
        }

        fun simScore(refEx: CsvExercise, c: CsvExercise): Double {
            var s = 0.0
            val cPat = canonLabel(c.movementPatternPretty)
            val cMov = canonLabel(c.primaryMoverPretty)
            if (refPat != null && cPat == refPat) s += 1.2
            if (baseKey(refEx.name) == baseKey(c.name)) s += 1.0
            if (refMover != null && cMov == refMover) s += 0.6
            s += 0.6 * jaccard(refEx.equipment, c.equipment)
            s += when (c.focus) {
                canonFocus -> 0.3
                "general", "any" -> 0.2
                else -> 0.1
            }
            s += (0.6 - (kotlin.math.abs(refEx.complexity - c.complexity) * 0.2)).coerceAtLeast(0.0)
            val cBwOnly = c.equipment.size == 1 && c.equipment.first() == "bodyweight"
            if (ownedEquipCanonical.any { it != "bodyweight" }) {
                if (!cBwOnly) s += 0.1 else s -= 0.05
            }
            return s + Random.nextDouble(0.0, 0.03)
        }

        return candidates
            .sortedByDescending { simScore(ref, it) }
            .map { it.name }
            .distinctBy { norm(it) }
            .take(limit)
    }

    /** NEW: Multiple removed items → one combined, recency-weighted suggestion list. */
    fun buildAddablePoolForReplacementsBatch(
        context: Context,
        removedExerciseNames: List<String>,   // most-recent first is best
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>,
        currentPlanNames: List<String>,
        limit: Int = 50
    ): List<String> {
        val all = loadCsv(context)
        val canonSplit = normSplit(split)
        val canonFocus = normFocus(focus)

        val filtered = all
            .asSequence()
            .filter { matchesSplit(it.split, canonSplit) }
            .filter { isAllowedByEquipment(it.equipment, ownedEquipCanonical) }
            .distinctBy { it.nameKey }
            .toList()

        // Resolve removed refs
        val refs: List<CsvExercise> = removedExerciseNames.mapNotNull { name ->
            val nk = norm(name)
            val bk = baseKey(name)
            all.firstOrNull { it.nameKey == nk } ?: all.firstOrNull { baseKey(it.name) == bk }
        }
        if (refs.isEmpty()) {
            return filtered
                .map { it.name }
                .filter { f -> currentPlanNames.none { it.equals(f, ignoreCase = true) } }
                .sorted()
                .take(limit)
        }

        // Exclude what’s already in the plan or exactly removed
        val excludeKeys = (currentPlanNames + removedExerciseNames).map { norm(it) }.toSet()
        val candidates = filtered.filter { it.nameKey !in excludeKeys }

        fun jaccard(a: List<String>, b: List<String>): Double {
            if (a.isEmpty() && b.isEmpty()) return 1.0
            val sa = a.toSet(); val sb = b.toSet()
            val inter = sa.intersect(sb).size.toDouble()
            val union = sa.union(sb).size.toDouble().coerceAtLeast(1.0)
            return inter / union
        }
        fun simScoreTo(ref: CsvExercise, c: CsvExercise): Double {
            var s = 0.0
            val refPat = canonLabel(ref.movementPatternPretty)
            val refMov = canonLabel(ref.primaryMoverPretty)
            val cPat = canonLabel(c.movementPatternPretty)
            val cMov = canonLabel(c.primaryMoverPretty)
            if (refPat != null && cPat == refPat) s += 1.2
            if (baseKey(ref.name) == baseKey(c.name)) s += 1.0
            if (refMov != null && cMov == refMov) s += 0.6
            s += 0.6 * jaccard(ref.equipment, c.equipment)
            s += when (c.focus) {
                canonFocus -> 0.3
                "general", "any" -> 0.2
                else -> 0.1
            }
            s += (0.6 - (kotlin.math.abs(ref.complexity - c.complexity) * 0.2)).coerceAtLeast(0.0)
            val cBwOnly = c.equipment.size == 1 && c.equipment.first() == "bodyweight"
            if (ownedEquipCanonical.any { it != "bodyweight" }) {
                if (!cBwOnly) s += 0.1 else s -= 0.05
            }
            return s
        }

        // Recency weights: 1.0, 0.9, 0.8, ...
        val weights = refs.mapIndexed { idx, _ -> (1.0 - idx * 0.1).coerceAtLeast(0.6) }

        // Aggregate score = weighted max(sim) across refs (strong bias to latest)
        val scored = candidates.map { c ->
            var best = 0.0
            for (i in refs.indices) {
                val s = simScoreTo(refs[i], c) * weights[i]
                if (s > best) best = s
            }
            c to (best + Random.nextDouble(0.0, 0.03))
        }

        return scored
            .sortedByDescending { it.second }
            .map { it.first.name }
            .distinctBy { norm(it) }
            .take(limit)
    }

    /** Convenience: single removed fallback to generic pool if needed. */
    fun buildAddablePoolForReplacement(
        context: Context,
        removedExerciseName: String,
        split: String,
        focus: String,
        ownedEquipCanonical: Set<String>,
        limit: Int = 30
    ): List<String> {
        val alts = suggestAlternatives(context, removedExerciseName, split, focus, ownedEquipCanonical, limit)
        if (alts.isNotEmpty()) return alts
        val all = loadCsv(context)
        val canonSplit = normSplit(split)
        return all
            .asSequence()
            .filter { matchesSplit(it.split, canonSplit) }
            .filter { isAllowedByEquipment(it.equipment, ownedEquipCanonical) }
            .distinctBy { it.nameKey }
            .map { it.name }
            .sorted()
            .take(limit)
            .toList()
    }
}
