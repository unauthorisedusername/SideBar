package io.github.echu2237.sidebar.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.text.Collator
import java.util.Locale

data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: Drawable,
)

object AppRepository {

    /** Query all launchable third-party/system apps (excluding this app) */
    @Suppress("DEPRECATION")
    fun loadLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        val own = context.packageName
        val collator = Collator.getInstance(Locale.CHINA)
        return resolveInfos
            .map {
                AppEntry(
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadLabel(pm).toString(),
                    it.activityInfo.loadIcon(pm),
                )
            }
            .filter { it.packageName != own }
            .sortedWith(compareBy(collator) { it.label })
    }
}
