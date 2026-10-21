package io.github.echu2237.sidebar.freeform

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import io.github.echu2237.sidebar.util.SbLog
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Freeform (floating window) launch WITHOUT Shizuku.
 *
 * Works the way Smart Edge does: the launch request carries ActivityOptions with the hidden
 * "launch windowing mode" set to freeform (5) plus explicit launch bounds. The hidden setter is
 * reached through the HiddenApiBypass library, so no special permission is needed.
 *
 * Requirement: the system must allow freeform windows. On stock Android that is
 * Developer options -> "Enable freeform windows" (and often "Force activities to be resizable").
 * Without it the system ignores the request and opens the app fullscreen.
 */
object FreeformLauncher {

    private const val WINDOWING_MODE_FREEFORM = 5

    /** True if any of the system freeform switches is on (all readable without permissions). */
    fun isFreeformSettingOn(context: Context): Boolean {
        val cr = context.contentResolver
        fun on(key: String) = runCatching { Settings.Global.getInt(cr, key, 0) != 0 }.getOrDefault(false)
        return on("enable_freeform_support") || on("freeform_window_management")
    }

    /** True if "Force activities to be resizable" is on. */
    fun isForceResizableOn(context: Context): Boolean =
        runCatching {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0) != 0
        }.getOrDefault(false)

    /** Opens Developer options so the user can turn the freeform switches on. */
    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { SbLog.e("open developer options failed", it) }
    }

    /**
     * Starts the app in a freeform window centered on screen (80% of the screen).
     * Returns false only if the launch call itself threw; the system may still decide to open
     * the app fullscreen when freeform is not enabled (it gives no feedback about that).
     */
    fun launch(context: Context, packageName: String, activityName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(packageName, activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            val options = ActivityOptions.makeBasic()
            HiddenApiBypass.invoke(
                ActivityOptions::class.java,
                options,
                "setLaunchWindowingMode",
                WINDOWING_MODE_FREEFORM,
            )
            val dm = context.resources.displayMetrics
            val w = dm.widthPixels
            val h = dm.heightPixels
            options.setLaunchBounds(Rect(w / 10, h / 10, w * 9 / 10, h * 9 / 10))
            SbLog.d("direct freeform launch: $packageName/$activityName")
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            SbLog.e("direct freeform launch failed", e)
            false
        }
    }
}
