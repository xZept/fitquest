package com.example.fitquest.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property (created once for the whole app)
val Context.dataStore by preferencesDataStore("user_prefs")

object DataStoreManager {

    private val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID")

    // Save userId
    suspend fun saveUserId(context: Context, userId: Int) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
        }
    }

    // Read userId
    fun getUserId(context: Context): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ID_KEY] ?: -1
        }
    }

    // Clear all data (optional, e.g., for logout)
    suspend fun clear(context: Context) {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
