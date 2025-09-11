package com.example.fitquest.models

data class ExerciseVideo(
    val videoId: Int,
    val exerciseId: Int,
    val exerciseName: String,
    val equipment: String,
    val youtubeLink: String
)
