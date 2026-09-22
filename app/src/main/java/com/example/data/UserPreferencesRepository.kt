package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val APP_THEME = stringPreferencesKey("app_theme")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val THEME_OPACITY = floatPreferencesKey("theme_opacity")
        val SESSION_TOKEN = stringPreferencesKey("session_token")
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
    }

    val appTheme: Flow<String?> = dataStore.data.map { prefs -> prefs[APP_THEME] }
    val isDarkTheme: Flow<Boolean> = dataStore.data.map { prefs -> prefs[IS_DARK_THEME] ?: true }
    val themeOpacity: Flow<Float> = dataStore.data.map { prefs -> prefs[THEME_OPACITY] ?: 1.0f }
    val sessionToken: Flow<String?> = dataStore.data.map { prefs -> prefs[SESSION_TOKEN] }
    val activeAccountId: Flow<String?> = dataStore.data.map { prefs -> prefs[ACTIVE_ACCOUNT_ID] }
    val lastSyncTime: Flow<Long> = dataStore.data.map { prefs -> prefs[LAST_SYNC_TIME] ?: 0L }
    val batterySaverEnabled: Flow<Boolean> = dataStore.data.map { prefs -> prefs[BATTERY_SAVER] ?: false }

    suspend fun saveAppTheme(theme: String) {
        dataStore.edit { prefs -> prefs[APP_THEME] = theme }
    }

    suspend fun saveDarkTheme(isDark: Boolean) {
        dataStore.edit { prefs -> prefs[IS_DARK_THEME] = isDark }
    }

    suspend fun saveThemeOpacity(opacity: Float) {
        dataStore.edit { prefs -> prefs[THEME_OPACITY] = opacity }
    }
    
    suspend fun saveSessionToken(token: String?) {
        dataStore.edit { prefs -> 
            if (token == null) prefs.remove(SESSION_TOKEN)
            else prefs[SESSION_TOKEN] = token 
        }
    }
    
    suspend fun saveActiveAccountId(accountId: String?) {
        dataStore.edit { prefs -> 
            if (accountId == null) prefs.remove(ACTIVE_ACCOUNT_ID)
            else prefs[ACTIVE_ACCOUNT_ID] = accountId 
        }
    }
    
    suspend fun saveLastSyncTime(time: Long) {
        dataStore.edit { prefs -> prefs[LAST_SYNC_TIME] = time }
    }
    
    suspend fun saveBatterySaverEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[BATTERY_SAVER] = enabled }
    }
}
