package com.krafttools.barokraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.krafttools.barokraft.core.Glyph

/**
 * Weather marks, drawn rather than shipped.
 *
 * ## Why no icon font and no bitmaps
 *
 * Three reasons, and the second is the one that decides it.
 *
 * The first is size: a glyph set is tens of kilobytes of vector data or a
 * comparable amount of PNG, and this app ships neither because it does not
 * need to.
 *
 * The second is **night**. A tintable mark takes the colour of whatever sky
 * it is drawn on, so a cloud at 3 a.m. is a light shape on a dark sky and a
 * cloud at noon is a dark shape on a light one. A shipped icon is one fixed
 * image and looks wrong on one of them — which is why so many weather apps
 * are unusable at night.
 *
 * The third is that a drawn shape can be *described*. A `Canvas` announces
 * nothing, so [WeatherIcon] attaches [description] to the focusable
 * element rather than to the drawing, and a blind reader gets "Rain" where
 * a sighted reader gets a cloud.
 *
 * ## Why these are clouds and not photoreal
 *
 * Because a photoreal sky is decoration and this screen's job is
 * information. A flat mark reads at a glance in peripheral vision, which is
 * how a forecast is actually read while walking.
 */
@Composable
fun WeatherIcon(
    glyph: Glyph?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    /** The words a screen reader gets. Announced instead of the drawing. */
    description: String? = null,
) {
    Box(
        modifier
            .then(
                if (description != null) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.size(size)) {
            drawGlyph(glyph, tint)
        }
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph?, tint: Color) {
    // Captured into a local because the helper lambdas below cannot see the
    // DrawScope receiver as a named value.
    val stroke = tint
    val w = size.width
    val h = size.height
    val unit = minOf(w, h) / 32f

    fun cloud(
        cx: Float,
        cy: Float,
        scale: Float,
        fill: Color?,
        stroke: Color,
    ) {
        val s = unit * scale
        val path = Path().apply {
            // Three arcs and a base line. A cloud drawn as arcs reads as a
            // cloud at 24 dp and at 96 dp; a cloud drawn as circles does not.
            moveTo(cx - 9f * s, cy + 6f * s)
            cubicTo(cx - 12f * s, cy + 6f * s, cx - 13f * s, cy + 1f * s, cx - 10f * s, cy - 1f * s)
            cubicTo(cx - 10f * s, cy - 7f * s, cx - 3f * s, cy - 9f * s, cx - 1f * s, cy - 5f * s)
            cubicTo(cx + 3f * s, cy - 11f * s, cx + 13f * s, cy - 9f * s, cx + 12f * s, cy - 2f * s)
            cubicTo(cx + 16f * s, cy - 2f * s, cx + 16f * s, cy + 6f * s, cx + 11f * s, cy + 6f * s)
            close()
        }
        if (fill != null) {
            drawPath(path, fill)
        } else {
            drawPath(path, stroke, style = Stroke(width = 1.6f * unit))
        }
    }

    fun sun(cx: Float, cy: Float, scale: Float) {
        val s = unit * scale
        drawCircle(stroke, radius = 5f * s, center = Offset(cx, cy), style = Stroke(width = 1.8f * unit))
        for (i in 0 until 8) {
            rotate(i * 45f, Offset(cx, cy)) {
                drawLine(
                    stroke,
                    Offset(cx, cy - 8f * s),
                    Offset(cx, cy - 11f * s),
                    strokeWidth = 1.8f * unit,
                )
            }
        }
    }

    fun drops(count: Int, len: Float, slant: Float) {
        val s = unit
        val startX = w / 2f - (count - 1) * 3.5f * s
        for (i in 0 until count) {
            val x = startX + i * 7f * s
            drawLine(
                stroke,
                Offset(x, h * 0.60f),
                Offset(x - slant * s, h * 0.60f + len * s),
                strokeWidth = 1.9f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }

    fun flakes() {
        val s = unit
        for (i in 0 until 3) {
            val x = w / 2f + (i - 1) * 7f * s
            drawCircle(stroke, radius = 1.5f * s, center = Offset(x, h * 0.70f))
        }
    }

    fun bolt() {
        val s = unit
        val path = Path().apply {
            moveTo(w / 2f + 1f * s, h * 0.60f)
            lineTo(w / 2f - 3f * s, h * 0.80f)
            lineTo(w / 2f + 0.5f * s, h * 0.80f)
            lineTo(w / 2f - 1.5f * s, h * 0.98f)
            lineTo(w / 2f + 4f * s, h * 0.76f)
            lineTo(w / 2f + 0.5f * s, h * 0.76f)
            close()
        }
        drawPath(path, stroke)
    }

    // An unknown code draws nothing rather than a guess. A placeholder mark
    // would be a claim that the app knows something it does not.
    if (glyph == null) return

    when (glyph) {
        Glyph.SUN -> sun(w / 2f, h / 2f, 1f)
        Glyph.MOSTLY_SUNNY -> {
            sun(w * 0.30f, h * 0.30f, 0.78f)
            cloud(w * 0.58f, h * 0.62f, 0.78f, null, stroke)
        }
        Glyph.PARTLY_CLOUDY -> {
            sun(w * 0.30f, h * 0.30f, 0.72f)
            cloud(w * 0.58f, h * 0.62f, 0.92f, null, stroke)
        }
        Glyph.CLOUD -> cloud(w / 2f, h * 0.45f, 1f, null, stroke)
        Glyph.FOG -> {
            cloud(w / 2f, h * 0.36f, 0.9f, null, stroke)
            for (i in 0 until 3) {
                val y = h * (0.60f + i * 0.13f)
                val inset = if (i == 1) w * 0.18f else w * 0.08f
                drawLine(
                    stroke,
                    Offset(inset, y),
                    Offset(w - inset, y),
                    strokeWidth = 1.7f * unit,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        Glyph.DRIZZLE -> {
            cloud(w / 2f, h * 0.40f, 0.95f, null, stroke)
            drops(count = 3, len = 3.2f, slant = 0.6f)
        }
        Glyph.RAIN -> {
            cloud(w / 2f, h * 0.38f, 1f, null, stroke)
            drops(count = 3, len = 6f, slant = 1.2f)
        }
        Glyph.SNOW -> {
            cloud(w / 2f, h * 0.40f, 0.95f, null, stroke)
            flakes()
        }
        Glyph.STORM -> {
            cloud(w / 2f, h * 0.38f, 1f, null, stroke)
            bolt()
        }
    }
}

/**
 * The sky behind the screen.
 *
 * ## Why a gradient and not an image
 *
 * Because a photograph of a sky is a claim about somewhere else. A gradient
 * derived from the actual condition and the actual sunrise and sunset for
 * the chosen city is a claim about *this* forecast, and it is three colours
 * and two offsets rather than 200 KB of JPEG.
 *
 * ## Why it stops partway down
 *
 * The forecast rows below are read against this, and body text on a
 * gradient that is still moving is text nobody can read — the contrast
 * against the darkest part of a night sky is below AA for anything under
 * 18 px. So the wash reaches the page colour by about 55% of the height and
 * the content below sits on a flat field.
 *
 * Three stops rather than two, so the fade to the page colour is a separate
 * band instead of one long interpolation from "sky" to "background" — with
 * two stops the middle of the screen is a colour that belongs to neither.
 */
@Composable
fun SkyBackground(
    top: Color,
    bottom: Color,
    page: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to top,
                0.55f to bottom,
                0.72f to page,
                1f to page,
                startY = 0f,
                endY = size.height * 0.78f,
            ),
            size = size,
        )
    }
}
