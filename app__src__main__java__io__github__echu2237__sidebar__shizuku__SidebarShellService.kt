package io.github.echu2237.sidebar.shizuku

import android.os.Process
import android.os.SystemClock
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService: this service runs as the shell (adb) identity and can execute shell commands such as am.
 * Loaded by Shizuku in a separate process; called through ISidebarShell after binding.
 *
 * Idle self-exit (leak prevention): when the host app is force-stopped by the system's one-tap cleanup, onDestroy never runs,
 * the unbind code gets no chance to execute, and this process would become a resident orphan (about 100MB RSS).
 * So it carries its own watchdog: it exits by itself if no exec request arrives within [IDLE_TIMEOUT_MS].
 * The host side re-binds on demand each time the panel opens, so floating-window launch is unaffected.
 */
class SidebarShellService : ISidebarShell.Stub() {

    @Volatile
    private var lastExecAt = SystemClock.elapsedRealtime()

    init {
        Thread {
            while (true) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (SystemClock.elapsedRealtime() - lastExecAt > IDLE_TIMEOUT_MS) {
                    Process.killProcess(Process.myPid())
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
    }

    override fun exec(command: String): String {
        lastExecAt = SystemClock.elapsedRealtime()
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).forEachLine { output.appendLine(it) }
            BufferedReader(InputStreamReader(process.errorStream)).forEachLine { output.appendLine(it) }
            process.waitFor()
            "exit=${process.exitValue()} $output"
        } catch (e: Exception) {
            "exit=-1 exception=${e.message}"
        }
    }

    private companion object {
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        const val CHECK_INTERVAL_MS = 60 * 1000L
    }
}
