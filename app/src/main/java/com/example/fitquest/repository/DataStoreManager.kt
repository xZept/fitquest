package com.example.fitquest.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey


// Single app-wide DataStore instance
val Context.dataStore by preferencesDataStore("user_prefs")

object DataStoreManager {

    private val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID")

    private val SHOW_WARMUP_TIPS_GLOBAL = booleanPreferencesKey("SHOW_WARMUP_TIPS")

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

    fun showWarmupTipsFlow(context: Context, userId: Int): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[warmupKey(userId)]
                ?: prefs[SHOW_WARMUP_TIPS_GLOBAL]
                ?: true
        }

    suspend fun shouldShowWarmupTips(context: Context, userId: Int): Boolean =
        showWarmupTipsFlow(context, userId).first()


    suspend fun setShowWarmupTips(context: Context, userId: Int, show: Boolean) {
        context.dataStore.edit { prefs -> prefs[warmupKey(userId)] = show }
    }

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
    private fun pinnedKey(userId: Int) =
        stringSetPreferencesKey("pinned_sessions_$userId")

    suspend fun getPinnedSessionIds(context: Context, userId: Int): Set<Long> {
        val raw = context.dataStore.data
            .map { it[pinnedKey(userId)] ?: emptySet() }
            .first()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setPinnedSessionIds(context: Context, userId: Int, ids: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[pinnedKey(userId)] = ids.map { it.toString() }.toSet()
        }
    }

}
