package io.github.echu2237.sidebar.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.echu2237.sidebar.data.FavoritesRepository
import io.github.echu2237.sidebar.service.SidebarService

/** Start on boot: restores the sidebar service if the user enabled autostart and the overlay permission is still granted */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Settings.canDrawOverlays(context)) return
        if (!FavoritesRepository.autostartEnabledBlocking(context)) return
        context.startForegroundService(
            Intent(context, SidebarService::class.java),
        )
    }
}
