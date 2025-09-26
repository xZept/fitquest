package com.example.fitquest.database

import androidx.room.TypeConverter
import com.example.fitquest.models.QuestExercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Converters {
    private val gson = Gson()

    @TypeConverter
    @JvmStatic
    fun fromQuestExerciseList(value: List<QuestExercise>?): String {
        val safe: List<QuestExercise> = value ?: emptyList()
        return gson.toJson(safe)
    }

    @TypeConverter
    @JvmStatic
    fun toQuestExerciseList(value: String?): List<QuestExercise> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<QuestExercise>>() {}.type
        return gson.fromJson(value, type)
    }

    // Optional helpers if you ever need List<String> conversions
    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>?): String {
        val safe: List<String> = value ?: emptyList()
        return gson.toJson(safe)
    }

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }
}
