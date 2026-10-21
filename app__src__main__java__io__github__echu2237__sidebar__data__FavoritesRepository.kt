package io.github.echu2237.sidebar.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "sidebar_settings")

/** What a gesture on the edge handle can do */
object GestureActions {
    const val PANEL = "panel"
    const val RECENTS = "recents"
    const val FLASHLIGHT = "flashlight"
    const val SETTINGS = "settings"
    const val NONE = "none"

    /** id to label, in the order shown in the settings dropdowns */
    val all: List<Pair<String, String>> = listOf(
        PANEL to "Open sidebar",
        RECENTS to "Recent apps bar",
        FLASHLIGHT to "Flashlight",
        SETTINGS to "Open Sidebar settings",
        NONE to "Nothing",
    )

    fun label(id: String): String = all.firstOrNull { it.first == id }?.second ?: "Nothing"
}

data class GestureSettings(
    val tap: String = GestureActions.PANEL,
    val doubleTap: String = GestureActions.RECENTS,
    val hold: String = GestureActions.NONE,
    val haptics: Boolean = true,
    val recentVertical: Boolean = false,
    val recentHeightDp: Int = 48,
)

/**
 * Persistence for favorites and settings (DataStore).
 * - selectedApps: set of package names chosen for the sidebar; empty = show all apps
 * - autostartEnabled: start-on-boot switch, on by default
 */
object FavoritesRepository {

    private val KEY_SELECTED = stringSetPreferencesKey("selected_apps")
    private val KEY_AUTOSTART = booleanPreferencesKey("autostart_enabled")
    private val KEY_PREFER_SHIZUKU = booleanPreferencesKey("prefer_shizuku")
    private val KEY_TAP = stringPreferencesKey("gesture_tap")
    private val KEY_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")
    private val KEY_HOLD = stringPreferencesKey("gesture_hold")
    private val KEY_HAPTICS = booleanPreferencesKey("gesture_haptics")
    private val KEY_RECENT_VERTICAL = booleanPreferencesKey("recent_vertical")
    private val KEY_RECENT_HEIGHT = intPreferencesKey("recent_height_dp")

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

    fun gestureSettingsFlow(context: Context): Flow<GestureSettings> =
        context.dataStore.data.map {
            val d = GestureSettings()
            GestureSettings(
                tap = it[KEY_TAP] ?: d.tap,
                doubleTap = it[KEY_DOUBLE_TAP] ?: d.doubleTap,
                hold = it[KEY_HOLD] ?: d.hold,
                haptics = it[KEY_HAPTICS] ?: d.haptics,
                recentVertical = it[KEY_RECENT_VERTICAL] ?: d.recentVertical,
                recentHeightDp = it[KEY_RECENT_HEIGHT] ?: d.recentHeightDp,
            )
        }

    suspend fun setTapAction(context: Context, id: String) {
        context.dataStore.edit { it[KEY_TAP] = id }
    }

    suspend fun setDoubleTapAction(context: Context, id: String) {
        context.dataStore.edit { it[KEY_DOUBLE_TAP] = id }
    }

    suspend fun setHoldAction(context: Context, id: String) {
        context.dataStore.edit { it[KEY_HOLD] = id }
    }

    suspend fun setGestureHaptics(context: Context, enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun setRecentVertical(context: Context, vertical: Boolean) {
        context.dataStore.edit { it[KEY_RECENT_VERTICAL] = vertical }
    }

    suspend fun setRecentHeightDp(context: Context, dp: Int) {
        context.dataStore.edit { it[KEY_RECENT_HEIGHT] = dp }
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
