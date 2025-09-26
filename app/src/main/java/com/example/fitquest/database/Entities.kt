package com.example.fitquest.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey(autoGenerate = true) val userId: Int = 0,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "birthday") val birthday: String,
    @ColumnInfo(name = "age") val age: Int,
    @ColumnInfo(name = "sex") val sex: String,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "password") val password: String,
)

@Entity(tableName = "userProfile",
    foreignKeys = [
        ForeignKey(
            entity = User::class,                // parent table
            parentColumns = ["userId"],          // PK in parent
            childColumns = ["userId"],           // FK in child
            onDelete = ForeignKey.CASCADE
        )], indices = [androidx.room.Index("userId")]
)
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val profileId: Int = 0,
    @ColumnInfo(name = "userId") val userId: Int,
    @ColumnInfo(name = "height") val height: Int, // cm
    @ColumnInfo(name = "weight") val weight: Int, // kg
    @ColumnInfo(name = "activity_level") val activityLevel: String? = null,
    @ColumnInfo(name = "goal") val goal: String? = null,
    @ColumnInfo(name = "equipment") val equipment: String? = null
)
