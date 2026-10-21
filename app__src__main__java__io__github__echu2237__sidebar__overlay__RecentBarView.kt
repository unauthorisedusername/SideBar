package io.github.echu2237.sidebar.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import io.github.echu2237.sidebar.data.AppEntry
import io.github.echu2237.sidebar.dp

/**
 * Horizontal bar of recently used app icons, shown at the bottom of the screen.
 * Tap an icon -> [onAppClick]; tap anywhere outside the bar -> [onDismiss].
 */
class RecentBarView(
    context: Context,
    apps: List<AppEntry>,
    private val onAppClick: (AppEntry) -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    init {
        background = GradientDrawable().apply {
            setColor(0xF2151515.toInt())
            cornerRadius = dp(18f).toFloat()
            setStroke(dp(1f), 0x44FFFFFF)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10f), dp(10f), dp(10f), dp(10f))
        }
        for (app in apps) {
            val icon = ImageView(context).apply {
                setImageDrawable(app.icon)
                contentDescription = app.label
                setOnClickListener { onAppClick(app) }
            }
            row.addView(
                icon,
                LinearLayout.LayoutParams(dp(52f), dp(52f)).apply {
                    marginStart = dp(5f)
                    marginEnd = dp(5f)
                },
            )
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(row)
        }
        addView(
            scroll,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    fun animateIn() {
        alpha = 0f
        translationY = dp(40f).toFloat()
        animate().alpha(1f).translationY(0f).setDuration(180L).start()
    }

    fun animateOut(onEnd: () -> Unit) {
        animate().alpha(0f).translationY(dp(40f).toFloat()).setDuration(140L)
            .withEndAction(onEnd).start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }
}
