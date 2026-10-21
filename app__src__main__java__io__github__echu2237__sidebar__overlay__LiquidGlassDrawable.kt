package io.github.echu2237.sidebar.overlay

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * iPhone Liquid Glass style implementation (works with window-level FLAG_BLUR_BEHIND for real background blur).
 *
 * Layers (after WWDC25 "Meet Liquid Glass"):
 * 1. Glass base      translucent light gradient (background blur provides the material feel, so opacity can be lower)
 * 2. Brightening     vibrancy: slight white lift, simulating iOS brightness/saturation boost of the background
 * 3. Edge lensing    bright bands bending inward on the left and right sides
 * 4. Top rim         rim light: bright at the top, fading downward
 * 5. Specular sheen  diagonal soft light from upper-left to mid-right
 * 6. Bottom inner shadow  gives a sense of glass thickness
 *
 * Corners use a continuous-curvature (squircle) path — iOS corners are not true circles but smoothly blended continuous curves.
 */
class LiquidGlassDrawable(
    private val cornerRadius: Float,
) : Drawable() {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vibrancyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    /** Outermost dark defining edge: keeps the panel boundary visible on light pages (WeChat/TIM etc. with white backgrounds) */
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x59000000
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val footPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val basePath = Path()
    private val lensPath = Path()
    private val rimPath = Path()
    private val rimInnerPath = Path()
    private val sheenPath = Path()
    private val footPath = Path()
    private var rimStroke = 2f

    /** Continuous-curvature rounded-corner path (squircle): control points at 0.5523x the radius, close to iOS smooth corners */
    private fun buildSquircle(path: Path, r: RectF, radius: Float) {
        path.reset()
        val c = radius * 0.5523f
        path.moveTo(r.left + radius, r.top)
        path.lineTo(r.right - radius, r.top)
        path.cubicTo(r.right - c, r.top, r.right, r.top + c, r.right, r.top + radius)
        path.lineTo(r.right, r.bottom - radius)
        path.cubicTo(r.right, r.bottom - c, r.right - c, r.bottom, r.right - radius, r.bottom)
        path.lineTo(r.left + radius, r.bottom)
        path.cubicTo(r.left + c, r.bottom, r.left, r.bottom - c, r.left, r.bottom - radius)
        path.lineTo(r.left, r.top + radius)
        path.cubicTo(r.left, r.top + c, r.left + c, r.top, r.left + radius, r.top)
        path.close()
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rect.set(bounds)
        rimStroke = bounds.height() * 0.003f + 1.5f

        buildSquircle(basePath, rect, cornerRadius)

        // 1. Glass base (opacity without system blur — based on the pure-drawing version the user approved)
        basePaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            0x8DFFFFFF.toInt(), 0x74EFF4FA.toInt(),
            Shader.TileMode.CLAMP,
        )

        // 2. Vibrancy brightening (simulates iOS background brightening/saturation)
        vibrancyPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            0x1FFFFFFF, 0x0AFFFFFF,
            Shader.TileMode.CLAMP,
        )

        // 3. Edge lensing: bright bands bending inward on the left and right sides
        val lensInset = rimStroke * 3f
        buildSquircle(
            lensPath,
            RectF(
                rect.left + lensInset, rect.top + lensInset,
                rect.right - lensInset, rect.bottom - lensInset,
            ),
            (cornerRadius - lensInset).coerceAtLeast(1f),
        )
        lensPaint.shader = LinearGradient(
            rect.left, 0f, rect.right, 0f,
            intArrayOf(0x66FFFFFF.toInt(), 0x00FFFFFF, 0x3DFFFFFF.toInt(), 0x00FFFFFF, 0x66FFFFFF.toInt()),
            floatArrayOf(0f, 0.20f, 0.5f, 0.80f, 1f),
            Shader.TileMode.CLAMP,
        )
        lensPaint.strokeWidth = rimStroke * 4f

        // 4. Top rim light (drawn inside the dark defining edge so the edge stays visible on light pages)
        buildSquircle(
            rimPath,
            RectF(
                rect.left + rimStroke / 2, rect.top + rimStroke / 2,
                rect.right - rimStroke / 2, rect.bottom - rimStroke / 2,
            ),
            cornerRadius,
        )
        edgePaint.strokeWidth = rimStroke * 1.9f

        val rimInnerInset = rimStroke * 2f
        buildSquircle(
            rimInnerPath,
            RectF(
                rect.left + rimInnerInset, rect.top + rimInnerInset,
                rect.right - rimInnerInset, rect.bottom - rimInnerInset,
            ),
            (cornerRadius - rimInnerInset).coerceAtLeast(1f),
        )
        rimPaint.shader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            0xFAFFFFFF.toInt(), 0x22FFFFFF.toInt(),
            Shader.TileMode.CLAMP,
        )
        rimPaint.strokeWidth = rimStroke

        // 5. Diagonal specular sheen (limited to the upper half)
        val sheenInset = rimStroke * 5
        buildSquircle(
            sheenPath,
            RectF(
                rect.left + sheenInset, rect.top + sheenInset,
                rect.right - sheenInset, rect.top + rect.height() * 0.52f,
            ),
            (cornerRadius - sheenInset).coerceAtLeast(1f),
        )
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right * 0.9f, rect.top + rect.height() * 0.55f,
            intArrayOf(0x8AFFFFFF.toInt(), 0x2EFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )

        // 6. Bottom inner shadow
        val footInset = rimStroke * 2
        buildSquircle(
            footPath,
            RectF(
                rect.left + footInset, rect.bottom - rect.height() * 0.16f,
                rect.right - footInset, rect.bottom - footInset,
            ),
            (cornerRadius - footInset).coerceAtLeast(1f),
        )
        footPaint.shader = LinearGradient(
            0f, rect.bottom - rect.height() * 0.16f, 0f, rect.bottom,
            0x00000000, 0x1A000000.toInt(),
            Shader.TileMode.CLAMP,
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(basePath, basePaint)
        canvas.drawPath(basePath, vibrancyPaint)
        canvas.drawPath(lensPath, lensPaint)
        canvas.drawPath(rimPath, edgePaint)      // dark defining edge (visible on light pages)
        canvas.drawPath(rimInnerPath, rimPaint)  // inner bright rim (visible on dark pages)
        canvas.drawPath(sheenPath, sheenPaint)
        canvas.drawPath(footPath, footPaint)
    }

    override fun setAlpha(alpha: Int) {
        basePaint.alpha = alpha
        vibrancyPaint.alpha = alpha
        lensPaint.alpha = alpha
        edgePaint.alpha = alpha
        rimPaint.alpha = alpha
        sheenPaint.alpha = alpha
        footPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
