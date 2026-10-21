package io.github.echu2237.sidebar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.echu2237.sidebar.dp
import kotlin.math.abs

/**
 * Right-edge trigger strip: summons the sidebar.
 *
 * Interaction (matches the feel of the native MIUI sidebar):
 * - Tap            -> [onTap]
 * - Double tap     -> [onDoubleTap] (only waited for when [isDoubleTapEnabled] is true, otherwise tap fires at once)
 * - Hold           -> [onHold] (only when [isHoldEnabled] is true)
 * - Drag left      -> panel follows the finger as a preview [onDragProgress]; on release, progress decides expand/collapse [onDragSettle]
 *
 * Visuals: one slim white pill with a faint hairline edge (visible on light and dark pages).
 *
 * Position: the window hugs the right edge of the screen.
 */
class EdgeTriggerView(
    context: Context,
    private val panelWidthPx: Int,
    /** true = handle sits on the bottom edge and is dragged up; false = right edge, dragged left */
    private val bottom: Boolean,
    private val onTap: () -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onHold: () -> Unit,
    private val isDoubleTapEnabled: () -> Boolean,
    private val isHoldEnabled: () -> Boolean,
    private val onDragStart: () -> Unit,
    private val onDragProgress: (Float) -> Unit,
    private val onDragSettle: (Boolean) -> Unit,
) : View(context) {

    /** Single clean pill: soft white fill with one hairline edge for contrast on any background */
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE8FFFFFF.toInt() }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x2E000000
    }
    private val pillRect = RectF()

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var lastFraction = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val gestureHandler = Handler(Looper.getMainLooper())
    private var holdFired = false
    private var secondTap = false
    private var pendingTap: Runnable? = null
    private val holdRunnable = Runnable {
        holdFired = true
        onHold()
    }

    private fun cancelHold() = gestureHandler.removeCallbacks(holdRunnable)

    private fun cancelPendingTap() {
        pendingTap?.let { gestureHandler.removeCallbacks(it) }
        pendingTap = null
    }

    override fun onDetachedFromWindow() {
        cancelHold()
        cancelPendingTap()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        edgePaint.strokeWidth = dp(0.75f).toFloat().coerceAtLeast(1f)
    }

    private val exclusion = listOf(android.graphics.Rect())

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateGestureExclusion()
    }

    /** Ask the system not to treat edge swipes inside this strip as the back gesture (Android caps this at 200dp tall per edge) */
    private fun updateGestureExclusion() {
        if (width == 0 || height == 0) return
        // Only the right-edge strip conflicts with the back gesture; the bottom edge belongs to home/recents, which apps cannot exclude
        if (bottom) {
            systemGestureExclusionRects = emptyList()
            return
        }
        val h = minOf(height, dp(200f))
        val top = (height - h) / 2
        exclusion[0].set(0, top, width, top + h)
        systemGestureExclusionRects = exclusion
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateGestureExclusion()
        // Pill scales with the window: length ~28% of the strip, thickness ~36% of the window depth (14dp -> 5dp)
        if (bottom) {
            val half = width * 0.28f / 2f
            val pillT = maxOf(dp(5f).toFloat(), height * 0.36f)
            val top = height * 0.92f - pillT
            val r = pillT / 2f
            pillRect.set(width / 2f - half, top, width / 2f + half, top + pillT)
            canvas.drawRoundRect(pillRect, r, r, pillPaint)
            canvas.drawRoundRect(pillRect, r, r, edgePaint)
            return
        }
        val half = height * 0.28f / 2f
        val pillW = maxOf(dp(5f).toFloat(), width * 0.36f)
        val right = width * 0.92f
        val left = right - pillW
        val r = pillW / 2f
        pillRect.set(left, height / 2f - half, right, height / 2f + half)
        canvas.drawRoundRect(pillRect, r, r, pillPaint)
        canvas.drawRoundRect(pillRect, r, r, edgePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                lastFraction = 0f
                holdFired = false
                // A tap is waiting for a possible second tap: this touch is that second tap
                secondTap = pendingTap != null
                cancelPendingTap()
                if (isHoldEnabled()) {
                    gestureHandler.postDelayed(holdRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                    cancelHold()
                }
                if (holdFired) return true
                // Progress along the drag axis: left for the right-edge handle, up for the bottom handle
                val dx = if (bottom) downY - event.rawY else downX - event.rawX
                if (!dragging && dx > touchSlop) {
                    dragging = true
                    secondTap = false
                    onDragStart()
                }
                if (dragging) {
                    lastFraction = (dx / panelWidthPx.toFloat()).coerceIn(0f, 1f)
                    onDragProgress(lastFraction)
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelHold()
                if (holdFired) {
                    holdFired = false
                    secondTap = false
                    return true
                }
                if (dragging) {
                    // Release: expand if dragged past 30%, otherwise snap back and collapse
                    onDragSettle(lastFraction >= 0.30f)
                } else if (abs(event.rawX - downX) < touchSlop &&
                    abs(event.rawY - downY) < touchSlop
                ) {
                    when {
                        secondTap -> onDoubleTap()
                        !isDoubleTapEnabled() -> onTap()
                        else -> {
                            // Wait briefly to see whether a second tap follows
                            val r = Runnable {
                                pendingTap = null
                                onTap()
                            }
                            pendingTap = r
                            gestureHandler.postDelayed(r, ViewConfiguration.getDoubleTapTimeout().toLong())
                        }
                    }
                }
                secondTap = false
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelHold()
                if (dragging) onDragSettle(false)
                dragging = false
                holdFired = false
                secondTap = false
            }
        }
        return true
    }
}
