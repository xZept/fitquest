package com.example.fitquest.database

import androidx.room.TypeConverter
import com.example.fitquest.database.MeasurementType
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

    //  List<String> conversions
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
    @TypeConverter @JvmStatic
    fun measurementTypeFromString(v: String?) =
        v?.let { runCatching { MeasurementType.valueOf(it) }.getOrNull() }

    @TypeConverter @JvmStatic
    fun measurementTypeToString(t: MeasurementType?) = t?.name

    @TypeConverter @JvmStatic
    fun instantFromLong(v: Long?) = v?.let(java.time.Instant::ofEpochMilli)

    @TypeConverter @JvmStatic
    fun instantToLong(i: java.time.Instant?) = i?.toEpochMilli()

}


