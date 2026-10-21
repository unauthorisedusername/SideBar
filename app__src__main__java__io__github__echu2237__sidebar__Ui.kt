package io.github.echu2237.sidebar

import android.content.Context
import android.view.View

/** dp -> px (Context version) */
fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** dp -> px (View version) */
fun View.dp(value: Float): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
