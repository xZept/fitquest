package com.example.fitquest.utils

import com.example.fitquest.models.Tips
import java.util.Locale

object TipsHelper {

    // ---------- Normalizers (UI → CSV) ----------
    fun mapGoalToCsv(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "lose fat", "fat loss", "weight loss", "cut" -> "lose fat"
        "build muscle", "muscle gain", "bulk", "gain" -> "build muscle"
        "maintain", "maintenance", "recomp" -> "maintain"
        else -> "any"
    }

    fun mapActivityToCsv(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "sedentary" -> "sedentary"
        "lightly active", "light" -> "lightly active"
        "moderately active", "moderate" -> "moderately active"
        "very active", "very" -> "very active"
        else -> "any"
    }

    fun mapSplitToCsv(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "push" -> "push"
        "pull" -> "pull"
        "legs" -> "legs"
        "upper" -> "upper"
        else -> "any"
    }

    fun mapFocusToCsv(raw: String?): String = when (raw?.trim()?.lowercase(Locale.ROOT)) {
        "hypertrophy" -> "hypertrophy"
        "strength" -> "strength"
        "general" -> "general"
        else -> "any"
    }

    // Handy predicate with wildcard support
    private fun match(field: String, want: String) =
        field == "any" || want == "any" || field == want

    // ---------- Filters ----------
    fun getGeneralTips(all: List<Tips>): List<Tips> =
        all.filter { it.category == "general" }

    fun getMacroTips(all: List<Tips>, goalRaw: String?, activityRaw: String?): List<Tips> {
        val goal = mapGoalToCsv(goalRaw)
        val act  = mapActivityToCsv(activityRaw)
        return all.filter { it.category == "macro" }
            .filter { match(it.goal, goal) && match(it.activityLevel, act) }
    }

    fun getWorkoutTips(all: List<Tips>, splitRaw: String?, focusRaw: String?): List<Tips> {
        val split = mapSplitToCsv(splitRaw)
        val focus = mapFocusToCsv(focusRaw)
        return all.filter { it.category == "workout" }
            .filter { match(it.split, split) && match(it.focus, focus) }
    }

    fun getRecoveryTips(all: List<Tips>, splitRaw: String?, focusRaw: String?): List<Tips> {
        val split = mapSplitToCsv(splitRaw)
        val focus = mapFocusToCsv(focusRaw)
        return all.filter { it.category == "recovery" }
            .filter { match(it.split, split) && match(it.focus, focus) }
    }

    // Overload for multiple splits (if the user picked a combo)
    fun getWorkoutTips(all: List<Tips>, splits: List<String>, focusRaw: String?): List<Tips> {
        val splitSet = splits.map { mapSplitToCsv(it) }.toSet()
        val focus = mapFocusToCsv(focusRaw)
        return all.filter { it.category == "workout" }
            .filter { (it.split == "any" || it.split in splitSet) && match(it.focus, focus) }
    }

    fun getRecoveryTips(all: List<Tips>, splits: List<String>, focusRaw: String?): List<Tips> {
        val splitSet = splits.map { mapSplitToCsv(it) }.toSet()
        val focus = mapFocusToCsv(focusRaw)
        return all.filter { it.category == "recovery" }
            .filter { (it.split == "any" || it.split in splitSet) && match(it.focus, focus) }
    }
}
