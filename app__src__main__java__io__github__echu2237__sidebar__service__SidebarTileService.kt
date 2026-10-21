package io.github.echu2237.sidebar.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.echu2237.sidebar.MainActivity

/**
 * Quick Settings tile: one-tap start/stop of the sidebar service.
 *
 * Why it exists: MIUI's "Clear all" force-stops this app (the OS's highest-privilege termination,
 * which the app cannot block or revive from). The tile reduces recovery to "pull down + one tap".
 */
class SidebarTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (SidebarService.isRunning) {
            startService(
                Intent(this, SidebarService::class.java).setAction(SidebarService.ACTION_STOP),
            )
        } else if (!Settings.canDrawOverlays(this)) {
            // Missing overlay permission: take the user into the app to guide granting it
            openApp()
        } else {
            try {
                startForegroundService(Intent(this, SidebarService::class.java))
            } catch (_: Exception) {
                // When starting a foreground service from the background is restricted, fall back to opening the app (opening the app auto-restores the service)
                openApp()
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        qsTile?.let {
            it.state = if (SidebarService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.updateTile()
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
