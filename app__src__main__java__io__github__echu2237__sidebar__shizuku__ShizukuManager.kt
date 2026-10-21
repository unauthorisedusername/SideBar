package io.github.echu2237.sidebar.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import io.github.echu2237.sidebar.util.SbLog

/**
 * Shizuku integration wrapper:
 * - State detection (not installed / not active / active but not authorized / authorized)
 * - UserService binding (runs am start --windowingMode 5 as the shell identity)
 */
object ShizukuManager {

    private var binder: ISidebarShell? = null

    val isShizukuInstalled: Boolean
        get() = try {
            Shizuku.getVersion() >= 0
        } catch (_: Exception) {
            false
        }

    val isShizukuActive: Boolean
        get() = try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }

    val isPermissionGranted: Boolean
        get() = try {
            isShizukuActive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

    /** Whether the shell service is bound and alive (used for "re-bind on demand") */
    val isBound: Boolean
        get() = try {
            binder?.asBinder()?.isBinderAlive == true
        } catch (_: Exception) {
            false
        }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    /** Bind the UserService; returns whether it is ready (may complete asynchronously) */
    fun bindShell(context: Context) {
        if (!isPermissionGranted) {
            SbLog.d("bindShell: no permission")
            return
        }
        if (binder != null && binder?.asBinder()?.isBinderAlive == true) {
            SbLog.d("bindShell: already bound")
            return
        }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, SidebarShellService::class.java),
            ).processNameSuffix("sidebar_shell").tag("sidebar_shell").daemon(false)
            Shizuku.bindUserService(args, serviceConnection)
            SbLog.d("bindShell: bindUserService called")
        } catch (e: Exception) {
            SbLog.e("bindShell failed", e)
        }
    }

    fun unbindShell(context: Context) {
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, SidebarShellService::class.java),
            ).processNameSuffix("sidebar_shell").tag("sidebar_shell").daemon(false)
            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (_: Exception) {
        }
        binder = null
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            SbLog.d("shell service connected")
            binder = ISidebarShell.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            SbLog.d("shell service disconnected")
            binder = null
        }
    }

    /**
     * Run a command as the shell identity (e.g. a freeform launch).
     * Returns null = Shizuku not ready; otherwise returns the command output.
     */
    fun exec(command: String): String? {
        val b = binder
        if (b == null) {
            SbLog.d("exec: binder null (not bound yet?)")
            return null
        }
        if (!b.asBinder().isBinderAlive) {
            SbLog.d("exec: binder dead")
            return null
        }
        return try {
            b.exec(command)
        } catch (e: Exception) {
            SbLog.e("exec failed", e)
            null
        }
    }

    /** Freeform floating-window launch command */
    fun freeformStartCommand(packageName: String, activityName: String): String =
        "am start --windowingMode 5 -n $packageName/$activityName"
}
