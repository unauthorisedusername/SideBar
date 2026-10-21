package io.github.echu2237.sidebar.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process

/**
 * Recent apps, built from the system usage history.
 * Needs the special "Usage access" permission (Settings -> Special app access -> Usage access).
 */
object RecentAppsRepository {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Most recently used launchable apps first (home screen and this app excluded). Call off the main thread. */
    fun load(context: Context, limit: Int): List<AppEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3L * 24 * 60 * 60 * 1000, now)
        val lastUsed = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastUsed[e.packageName] = e.timeStamp
            }
        }
        val homePackage = context.packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        val launchable = AppRepository.loadLaunchableApps(context).associateBy { it.packageName }
        return lastUsed.entries
            .sortedByDescending { it.value }
            .mapNotNull { launchable[it.key] }
            .filter { it.packageName != homePackage }
            .take(limit)
    }
}
