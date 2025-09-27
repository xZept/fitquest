package com.example.fitquest.database

import androidx.room.TypeConverter
import com.example.fitquest.models.Food.MeasurementType
import com.example.fitquest.models.QuestExercise
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.Instant

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

    // Converters for food
    @TypeConverter fun toType(name: String) = MeasurementType.valueOf(name)
    @TypeConverter fun fromType(type: MeasurementType) = type.name
    @TypeConverter fun fromInstant(i: Instant?) = i?.toEpochMilli()
    @TypeConverter fun toInstant(millis: Long?) = millis?.let { Instant.ofEpochMilli(it) }

}


