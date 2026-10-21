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

    @Volatile private var cachedLaunchable: Map<String, AppEntry>? = null
    @Volatile private var cachedAt = 0L

    private fun launchable(context: Context): Map<String, AppEntry> {
        val now = System.currentTimeMillis()
        cachedLaunchable?.let { if (now - cachedAt < 120_000L) return it }
        val m = AppRepository.loadLaunchableApps(context).associateBy { it.packageName }
        cachedLaunchable = m
        cachedAt = now
        return m
    }

    /** Warm the cache so the first bar opens instantly. Call off the main thread. */
    fun prewarm(context: Context) {
        runCatching { launchable(context) }
    }

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
        val homePackage = context.packageManager
            .resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        val launchable = launchable(context)
        // Short window first (fast); widen only if there are not enough apps
        var result = query(context, 12L * 60 * 60 * 1000, launchable, homePackage, limit)
        if (result.size < limit) {
            result = query(context, 3L * 24 * 60 * 60 * 1000, launchable, homePackage, limit)
        }
        return result
    }

    private fun query(
        context: Context,
        windowMs: Long,
        launchable: Map<String, AppEntry>,
        homePackage: String?,
        limit: Int,
    ): List<AppEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - windowMs, now)
        val lastUsed = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastUsed[e.packageName] = e.timeStamp
            }
        }
        return lastUsed.entries
            .sortedByDescending { it.value }
            .mapNotNull { launchable[it.key] }
            .filter { it.packageName != homePackage }
            .take(limit)
    }
}
