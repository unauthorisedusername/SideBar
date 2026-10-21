package io.github.echu2237.sidebar.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.echu2237.sidebar.data.AppEntry
import io.github.echu2237.sidebar.dp

/**
 * Bar of recently used app icons. Horizontal (bottom of the screen) or vertical (at the right edge).
 * Tap an icon -> [onAppClick]; tap anywhere outside the bar -> [onDismiss].
 */
class RecentBarView(
    context: Context,
    apps: List<AppEntry>,
    private val vertical: Boolean,
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
            orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (vertical) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
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
                    if (vertical) {
                        topMargin = dp(5f)
                        bottomMargin = dp(5f)
                    } else {
                        marginStart = dp(5f)
                        marginEnd = dp(5f)
                    }
                },
            )
        }
        val scroller = if (vertical) {
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                addView(row)
            }
        } else {
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                addView(row)
            }
        }
        addView(
            scroller,
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** Fast open */
    fun animateIn() {
        alpha = 0f
        if (vertical) translationX = dp(30f).toFloat() else translationY = dp(30f).toFloat()
        animate().alpha(1f).translationX(0f).translationY(0f).setDuration(90L).start()
    }

    /** Close: one smooth fade + small slide, no blinking */
    fun animateOut(onEnd: () -> Unit) {
        animate().cancel()
        val d = dp(24f).toFloat()
        animate()
            .alpha(0f)
            .translationX(if (vertical) d else 0f)
            .translationY(if (vertical) 0f else d)
            .setDuration(120L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                visibility = INVISIBLE // hide before the window is removed, so no leftover frame shows
                onEnd()
            }
            .start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onDismiss()
            return true
        }
        return super.onTouchEvent(event)
    }
}
