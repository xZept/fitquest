package com.example.fitquest.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

// Single app-wide DataStore instance
val Context.dataStore by preferencesDataStore("user_prefs")

object DataStoreManager {

    private val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID")

    // Legacy (pre-user) warmup key – kept for backward compatibility
    private val SHOW_WARMUP_TIPS_GLOBAL = booleanPreferencesKey("SHOW_WARMUP_TIPS")

    // Per-user warmup key
    private fun warmupKey(userId: Int) =
        booleanPreferencesKey("SHOW_WARMUP_TIPS_U_$userId")

    /** Read userId (returns -1 when logged out) */
    fun getUserId(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[USER_ID_KEY] ?: -1 }

    /** Save userId (on successful login) */
    suspend fun saveUserId(context: Context, userId: Int) {
        context.dataStore.edit { prefs -> prefs[USER_ID_KEY] = userId }
    }

    /** Remove only the userId (logout) */
    suspend fun clearUserId(context: Context) {
        context.dataStore.edit { prefs -> prefs.remove(USER_ID_KEY) }
    }

    /** Clear everything (optional utility) */
    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    /* -------------------- Warmup tips (per-user) -------------------- */

    /**
     * Observe whether to show warmup tips for a specific user.
     * Defaults to TRUE (show). Falls back to the old global flag if present.
     */
    fun showWarmupTipsFlow(context: Context, userId: Int): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[warmupKey(userId)]
                ?: prefs[SHOW_WARMUP_TIPS_GLOBAL]  // legacy fallback for old installs
                ?: true
        }

    /** Convenience suspend getter. */
    suspend fun shouldShowWarmupTips(context: Context, userId: Int): Boolean =
        showWarmupTipsFlow(context, userId).first()

    /**
     * Set whether to show warmup tips for a specific user.
     * Pass show=false when the user ticks "Don’t show again".
     */
    suspend fun setShowWarmupTips(context: Context, userId: Int, show: Boolean) {
        context.dataStore.edit { prefs -> prefs[warmupKey(userId)] = show }
    }

    /** Optional: remove the per-user flag (forces default=true next time) */
    suspend fun clearWarmupTipsForUser(context: Context, userId: Int) {
        context.dataStore.edit { prefs -> prefs.remove(warmupKey(userId)) }
    }

    // ---- Generic string helpers ----
    fun getString(context: Context, key: String): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { prefs -> prefs[prefKey] }
    }

    suspend fun setString(context: Context, key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { prefs -> prefs[prefKey] = value }
    }

}
