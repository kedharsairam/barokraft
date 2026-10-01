package com.krafttools.barokraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.krafttools.barokraft.core.NowcastPoint
import com.krafttools.barokraft.core.fmt1

/**
 * The nowcast, drawn as what it is: a band with a line in it.
 *
 * ## Why this is the most important drawing in the app
 *
 * Because the README opens by saying "the band is the product, the centre
 * line is a convenience", and for two releases nothing drew either. The
 * 64-member ensemble was computed, tested, carried in the state record, and
 * rendered as a row of sample dots — so the app's central claim was
 * invisible.
 *
 * Everything else on this screen is a reading. This is the only place the
 * app shows an *estimate*, and an estimate that does not display its own
 * uncertainty is a forecast wearing a measurement's clothes.
 *
 * ## Why the band is filled and not two lines
 *
 * Two boundary lines need the reader to judge which is which and to
 * interpolate between them. A filled region reads as one quantity with
 * width, which is what it is. The fill is a vertical gradient so the top
 * and bottom edges — the actual bounds — stay crisp while the interior is
 * soft, and the centre line is drawn *over* the fill at partial opacity so
 * it is visibly a summary rather than a peer of the bounds.
 *
 * ## Why the y-axis is the ensemble's own scale
 *
 * Because a band plotted against a shared axis with the samples compresses
 * into nothing: six hours of weather is 10–40 hPa and the chip's noise is
 * 0.012. The band has its own scale, which is stated in the caption, and the
 * samples are drawn *below* it rather than in it — they are measurements and
 * it is an estimate, and drawing them in the same space implies a
 * relationship that does not exist.
 */
@Composable
fun NowcastBand(
    points: List<NowcastPoint>,
    currentHpa: Float,
    horizonHours: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    // The bounds, plus the starting pressure, because the band is a *change*
    // from now and a reader needs both ends to place it.
    // lowHpa and highHpa are the edges already. `uncertaintyHpa` is the
    // full width between them, so halving it would draw a band half the
    // width the ensemble actually agreed on.
    val lows = points.map { it.lowHpa }
    val highs = points.map { it.highHpa }
    val from = currentHpa
    val min = minOf(lows.min(), from)
    val max = maxOf(highs.max(), from)

    // A floor on the span, so a flat band is drawn as a thin ribbon rather
    // than being stretched to full height — which would make "no change" look
    // like "enormous uncertainty".
    val span = (max - min).coerceAtLeast(0.6f)
    val mid = (max + min) / 2f

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stepX = if (points.size > 1) w / (points.size - 1) else w

            fun y(v: Float): Float = {
                val frac = ((v - (mid - span / 2f)) / span).coerceIn(0f, 1f)
                // Inverted: pressure rises upward.
                h * (1f - frac)
            }()

            // The upper bound.
            val upper = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                val vy = y(p.highHpa)
                if (i == 0) upper.moveTo(x, vy) else upper.lineTo(x, vy)
            }
            // The lower bound, back to the left.
            for (i in points.indices.reversed()) {
                val p = points[i]
                upper.lineTo(i * stepX, y(p.lowHpa))
            }
            upper.close()

            drawPath(
                path = upper,
                brush = Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.32f),
                    0.5f to tint.copy(alpha = 0.12f),
                    1f to tint.copy(alpha = 0.32f),
                ),
            )
            // A hairline on the bounds themselves, so the edges are crisp.
            drawPath(path = upper, color = tint.copy(alpha = 0.55f), style = Stroke(width = 1f))

            // The centre line, over the fill and dimmer than the bounds.
            val centre = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                val vy = y(p.centreHpa)
                if (i == 0) centre.moveTo(x, vy) else centre.lineTo(x, vy)
            }
            drawPath(path = centre, color = tint, style = Stroke(width = 1.5f))

            // Where the band starts: now.
            drawLine(
                color = tint.copy(alpha = 0.7f),
                start = Offset(0f, y(from)),
                end = Offset(0f, h),
                strokeWidth = 1f,
            )
        }
    }
}

/**
 * The caption under the band, which states what is being shown.
 *
 * ## Why the change comes from the core rather than from here
 *
 * Because `NowcastPoint.centreHpa` is an *absolute* pressure, and a first
 * draft subtracted nothing — producing "Centre rising by 1007.5 hPa", which
 * is the same class of nonsense as the 976 hPa projection this file's
 * neighbours live next to. `nowcastTotalChange` already does the subtraction
 * correctly, so it is used rather than reimplemented.
 *
 * ## The fifth `$points.size` in this codebase
 *
 * The first version interpolated `$points.size` inside a template, which
 * prints the whole list followed by the text ".size". Braces are required
 * for a call. This is the same mistake as `$HORIZON_HOURS.toInt()` and
 * `$x.toInt()`, and it is now the fifth time — which is a fact about the
 * habit, not about Kotlin.
 */
internal fun nowcastCaption(points: List<NowcastPoint>, horizonHours: Float): String {
    if (points.isEmpty()) return ""
    val change = com.krafttools.barokraft.core.nowcastTotalChange(points, points.first().centreHpa)
        ?: 0f
    val end = points.last()
    val magnitude = kotlin.math.abs(change)
    val direction = when {
        magnitude < 0.3f -> "about level"
        change < 0 -> "falling"
        else -> "rising"
    }
    val spoken = if (magnitude < 0.3f) "Centre about level" else {
        "Centre $direction by ${fmt1(magnitude)} hPa"
    }
    return "Nowcast, ${horizonHours.toInt()} hours. $spoken, and the shaded band " +
        "is where most of the ${points.size} runs land — plus or minus " +
        "${fmt1(end.uncertaintyHpa / 2f)} hPa at the far end. " +
        "The band's width is the honest part."
}

/** A horizontal rule under the band, drawn in the same tint. */
@Composable
internal fun BandBaseline(modifier: Modifier = Modifier, tint: Color = Hairline) {
    Canvas(modifier) {
        drawRect(
            color = tint,
            size = Size(size.width, 1f),
        )
    }
}
