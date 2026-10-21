package io.github.echu2237.sidebar.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "sidebar_settings")

/**
 * Persistence for favorites and settings (DataStore).
 * - selectedApps: set of package names chosen for the sidebar; empty = show all apps
 * - autostartEnabled: start-on-boot switch, on by default
 */
object FavoritesRepository {

    private val KEY_SELECTED = stringSetPreferencesKey("selected_apps")
    private val KEY_AUTOSTART = booleanPreferencesKey("autostart_enabled")
    private val KEY_PREFER_SHIZUKU = booleanPreferencesKey("prefer_shizuku")

    fun selectedAppsFlow(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { it[KEY_SELECTED] ?: emptySet() }

    fun autostartEnabledFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTOSTART] ?: true }

    /** Use Shizuku for floating windows before the direct (no-Shizuku) method; off by default */
    fun preferShizukuFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PREFER_SHIZUKU] ?: false }

    suspend fun setPreferShizuku(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_PREFER_SHIZUKU] = enabled }
    }

    suspend fun setSelectedApps(context: Context, packages: Set<String>) {
        context.dataStore.edit { it[KEY_SELECTED] = packages }
    }

    suspend fun setAutostartEnabled(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTOSTART] = enabled }
    }

    /** Synchronous read for the service on a background thread (millisecond-level local read, does not block the main thread) */
    fun selectedAppsBlocking(context: Context): Set<String> =
        runBlocking { selectedAppsFlow(context).first() }

    fun preferShizukuBlocking(context: Context): Boolean =
        runBlocking { preferShizukuFlow(context).first() }

    fun autostartEnabledBlocking(context: Context): Boolean =
        runBlocking { autostartEnabledFlow(context).first() }
}
