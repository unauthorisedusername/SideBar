package io.github.echu2237.sidebar.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.echu2237.sidebar.dp
import kotlin.math.abs

/**
 * Right-edge trigger strip: summons the sidebar.
 *
 * Interaction (matches the feel of the native MIUI sidebar):
 * - Tap            -> [onTap] expands directly
 * - Drag left      -> panel follows the finger as a preview [onDragProgress]; on release, progress decides expand/collapse [onDragSettle]
 *
 * Visuals: two-tone handle — dark outer stroke + light inner core.
 * On light pages (e.g. WeChat) the dark outline keeps it visible; on dark pages the light core does.
 * A soft dark halo adds separation on both kinds of background.
 *
 * Position: the window hugs the right edge of the screen.
 */
class EdgeTriggerView(
    context: Context,
    private val panelWidthPx: Int,
    private val onTap: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragProgress: (Float) -> Unit,
    private val onDragSettle: (Boolean) -> Unit,
) : View(context) {

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = null }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    /** Dark outline: the source of visibility on light backgrounds */
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66000000
    }
    /** Soft dark halo: makes the handle "float" off any background */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x1F000000
    }
    private val pillRect = RectF()
    private val innerRect = RectF()

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var lastFraction = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        pillPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xF5FFFFFF.toInt(), 0xD9EEF4F9.toInt(),
            Shader.TileMode.CLAMP,
        )
        pillStrokePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            0xFFFFFFFF.toInt(), 0x66FFFFFF.toInt(),
            Shader.TileMode.CLAMP,
        )
        outlinePaint.strokeWidth = dp(1.5f).toFloat()
        haloPaint.strokeWidth = dp(4f).toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pillH = dp(120f)
        val half = pillH / 2f
        // The handle hugs the right screen edge (window right edge = screen right edge)
        val pillW = dp(7f)
        val right = width * 0.92f
        val left = right - pillW
        val r = pillW / 2f
        pillRect.set(left, height / 2f - half, right, height / 2f + half)

        // 1) Soft dark halo (extends outward, low-opacity overlay, hardware-draw friendly)
        for (i in 3 downTo 1) {
            val off = dp(i.toFloat() * 1.2f)
            canvas.drawRoundRect(
                pillRect.left - off, pillRect.top - off,
                pillRect.right + off, pillRect.bottom + off,
                r + off, r + off, haloPaint,
            )
        }
        // 2) Dark outline: the source of visibility on light pages
        canvas.drawRoundRect(pillRect, r, r, outlinePaint)
        // 3) Light inner core (gradient + top highlight)
        val inset = dp(1.2f)
        innerRect.set(
            pillRect.left + inset, pillRect.top + inset,
            pillRect.right - inset, pillRect.bottom - inset,
        )
        val ir = (r - inset).coerceAtLeast(1f)
        canvas.drawRoundRect(innerRect, ir, ir, pillPaint)
        canvas.drawRoundRect(innerRect, ir, ir, pillStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragging = false
                lastFraction = 0f
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = downX - event.rawX
                if (!dragging && dx > touchSlop) {
                    dragging = true
                    onDragStart()
                }
                if (dragging) {
                    lastFraction = (dx / panelWidthPx.toFloat()).coerceIn(0f, 1f)
                    onDragProgress(lastFraction)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    // Release: expand if dragged past 30%, otherwise snap back and collapse
                    onDragSettle(lastFraction >= 0.30f)
                } else if (abs(event.rawX - downX) < touchSlop &&
                    abs(event.rawY - downY) < touchSlop
                ) {
                    onTap()
                }
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragSettle(false)
                dragging = false
            }
        }
        return true
    }
}
