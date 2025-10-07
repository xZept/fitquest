package com.example.fitquest.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Single app-wide DataStore instance
val Context.dataStore by preferencesDataStore("user_prefs")

object DataStoreManager {

    private val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID")

    // Warm-up tips visibility flag (default true = show tips)
    private val SHOW_WARMUP_TIPS_KEY = booleanPreferencesKey("SHOW_WARMUP_TIPS")

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

    // -------------------------
    // Warm-up tips preference
    // -------------------------

    /** Flow for whether we should show warm-up tips (default true). */
    fun showWarmupTipsFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[SHOW_WARMUP_TIPS_KEY] ?: true }

    /** Get once (suspending) whether to show warm-up tips. */
    suspend fun shouldShowWarmupTips(context: Context): Boolean =
        showWarmupTipsFlow(context).first()

    /** Persist whether to show warm-up tips going forward. */
    suspend fun setShowWarmupTips(context: Context, show: Boolean) {
        context.dataStore.edit { it[SHOW_WARMUP_TIPS_KEY] = show }
    }
}
