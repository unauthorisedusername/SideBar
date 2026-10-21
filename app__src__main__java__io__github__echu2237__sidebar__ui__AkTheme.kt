package io.github.echu2237.sidebar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Arknights-style UI design system.
 *
 * Style notes:
 * - Deep black industrial base + high-contrast white text
 * - Everything is right-angled / 45° chamfered, never rounded
 * - Thin white strokes + corner marks + diagonal stripes
 * - Yellow (#FFD400) as the accent color, for title indices and key states
 * - Monospace font + wide letter spacing + all-caps labels for a terminal/HUD feel
 */
object AkColors {
    val Bg = Color(0xFF0B0B0B)
    val Panel = Color(0xFF151515)
    val PanelAlt = Color(0xFF1D1D1D)
    val Line = Color(0x33FFFFFF)
    val LineBright = Color(0x99FFFFFF)
    val Text = Color(0xFFEDEDED)
    val TextDim = Color(0xFF868686)
    val Accent = Color(0xFFFFD400)
    val Ok = Color(0xFF6FCF8E)
    val Warn = Color(0xFFFF8A3D)
    val Danger = Color(0xFFFF5252)
}

enum class AkCorner { TopLeft, TopRight, BottomLeft, BottomRight, None }

/** 45° chamfered shape (the signature "cut corner" panel of Arknights). Call inside a Composable; pixel values are precomputed internally */
@Composable
fun akShape(cut: Dp = 14.dp, corner: AkCorner = AkCorner.BottomRight): Shape {
    val cutPx = with(LocalDensity.current) { cut.toPx() }
    return remember(cutPx, corner) {
        GenericShape { s, _ ->
            val c = cutPx.coerceAtMost(min(s.width, s.height) / 2f)
            val w = s.width
            val h = s.height
            when (corner) {
                AkCorner.BottomRight -> {
                    moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h - c)
                    lineTo(w - c, h); lineTo(0f, h)
                }
                AkCorner.TopRight -> {
                    moveTo(0f, 0f); lineTo(w - c, 0f); lineTo(w, c)
                    lineTo(w, h); lineTo(0f, h)
                }
                AkCorner.TopLeft -> {
                    moveTo(c, 0f); lineTo(w, 0f); lineTo(w, h)
                    lineTo(0f, h); lineTo(0f, c)
                }
                AkCorner.BottomLeft -> {
                    moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h)
                    lineTo(c, h); lineTo(0f, h - c)
                }
                AkCorner.None -> {
                    moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(0f, h)
                }
            }
            close()
        }
    }
}

/** Diagonal stripe decoration at the panel's top-right corner (HUD element) */
fun Modifier.akHatch(color: Color = AkColors.LineBright, lines: Int = 4, gap: Float = 6f): Modifier =
    drawBehind {
        for (i in 0 until lines) {
            val x = size.width - i * gap
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x - gap, gap),
                strokeWidth = 1.5f,
            )
        }
    }

@Composable
fun AkText(
    text: String,
    color: Color = AkColors.Text,
    size: Int = 13,
    mono: Boolean = true,
    bold: Boolean = false,
    spacing: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.sp,
        letterSpacing = spacing.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Title panel with index number and yellow indicator bar */
@Composable
fun AkPanel(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = akShape(16.dp, AkCorner.BottomRight)
    Column(
        modifier
            .fillMaxWidth()
            .background(AkColors.Panel, shape)
            .border(1.dp, AkColors.Line, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(AkColors.Accent),
            )
            Spacer(Modifier.width(10.dp))
            AkText(index, color = AkColors.Accent, size = 11, bold = true)
            Spacer(Modifier.width(8.dp))
            AkText(title.uppercase(), size = 12, spacing = 2f)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(width = 26.dp, height = 8.dp)
                    .akHatch(),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AkColors.Line))
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Status row: label on the left / value on the right + status color block */
@Composable
fun AkStatusRow(label: String, value: String, status: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AkText(label.uppercase(), color = AkColors.TextDim, size = 11, spacing = 1.5f)
        Spacer(Modifier.weight(1f))
        if (status != null) {
            Box(Modifier.size(9.dp).background(status))
            Spacer(Modifier.width(8.dp))
        }
        AkText(value, size = 12)
    }
}

/** Arknights-style button: primary = white background with black text, secondary = black background with white border; always chamfered */
@Composable
fun AkButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = akShape(12.dp, AkCorner.BottomRight)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (primary) AkColors.Text else AkColors.PanelAlt, shape)
            .border(1.dp, if (primary) AkColors.Text else AkColors.LineBright, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(if (primary) AkColors.Bg else AkColors.Accent),
        )
        Spacer(Modifier.width(10.dp))
        AkText(
            text.uppercase(),
            color = if (primary) AkColors.Bg else AkColors.Text,
            size = 13,
            bold = true,
            spacing = 2f,
        )
        Spacer(Modifier.weight(1f))
        AkText("▶", color = if (primary) AkColors.Bg else AkColors.TextDim, size = 10, mono = false)
    }
}

/** Arknights-style switch: chamfered track + square thumb */
@Composable
fun AkSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = akShape(6.dp, AkCorner.BottomRight)
    Row(
        modifier = Modifier
            .width(56.dp)
            .height(26.dp)
            .background(if (checked) AkColors.Accent else AkColors.PanelAlt, shape)
            .border(1.dp, if (checked) AkColors.Accent else AkColors.LineBright, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .width(18.dp)
                .fillMaxHeight()
                .background(if (checked) AkColors.Bg else AkColors.LineBright),
        )
    }
}

/** Arknights-style checkbox: chamfered square */
@Composable
fun AkCheckbox(checked: Boolean, onClick: () -> Unit) {
    val shape = akShape(6.dp, AkCorner.BottomRight)
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(if (checked) AkColors.Accent else Color.Transparent, shape)
            .border(1.dp, if (checked) AkColors.Accent else AkColors.LineBright, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            AkText("✓", color = AkColors.Bg, size = 13, bold = true, mono = false)
        }
    }
}

/** Page header bar */
@Composable
fun AkHeader(crumb: String, title: String, right: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(18.dp).background(AkColors.Accent))
            Spacer(Modifier.width(10.dp))
            AkText(crumb.uppercase(), color = AkColors.Accent, size = 11, bold = true, spacing = 2f)
            Spacer(Modifier.weight(1f))
            if (right != null) AkText(right, color = AkColors.TextDim, size = 10)
        }
        Spacer(Modifier.height(8.dp))
        AkText(title, size = 24, bold = true, spacing = 3f)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(AkColors.Line),
        )
        Box(
            Modifier
                .width(64.dp)
                .height(2.dp)
                .background(AkColors.Accent),
        )
    }
}

/** Thin-stroke container (search box, etc.) */
@Composable
fun AkField(
    modifier: Modifier = Modifier,
    cut: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val shape = akShape(cut, AkCorner.BottomRight)
    Box(
        modifier
            .fillMaxWidth()
            .background(AkColors.PanelAlt, shape)
            .border(1.dp, AkColors.Line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { content() }
}

/** Decorative divider: thin line + short yellow line */
@Composable
fun AkDivider() {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(AkColors.Line))
    }
}

/** Circular tick decoration (HUD ring), purely decorative */
fun Modifier.akFrame(): Modifier = drawBehind {
    drawRect(
        color = AkColors.LineBright.copy(alpha = 0.35f),
        style = Stroke(width = 1.5f),
    )
    val p = Path().apply {
        moveTo(0f, 18f); lineTo(18f, 0f)
        moveTo(0f, 34f); lineTo(34f, 0f)
    }
    drawPath(p, AkColors.Accent.copy(alpha = 0.55f), style = Stroke(width = 2f))
}
