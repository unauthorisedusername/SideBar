package io.github.echu2237.sidebar.util

import android.util.Log
import io.github.echu2237.sidebar.BuildConfig

/**
 * Debug log wrapper: outputs only in debug builds, completely silent in release builds.
 * When troubleshooting Shizuku binding / floating-window launch / panel lifecycle, view with `adb logcat -s Sidebar`.
 */
object SbLog {

    private const val TAG = "Sidebar"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(TAG, message, throwable)
    }
}
