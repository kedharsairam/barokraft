package com.krafttools.barokraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.krafttools.barokraft.core.BaroScale

/**
 * The pressure trace.
 *
 * ## Why the scale is not zero-based
 *
 * A barometer chip is good to about 0.012 hPa and a day's weather is
 * 10–40 hPa. Drawn against a zero axis, 1004.0 hPa is a flat line pinned
 * at the top of the panel and the entire trend is invisible. So the scale
 * is centred on the median and sized to the variation, which is
 * [BaroScale]'s job, and this file only draws.
 *
 * ## Why the line is thinner than the points
 *
 * Because the line between two samples ten minutes apart is an
 * interpolation, and the sample itself is a measurement. Drawing them at
 * the same weight states both as the same kind of claim, which is the same
 * overstatement as printing a fitted forecast with no error band. The
 * points are solid; the line between them is not.
 *
 * ## Accessibility
 *
 * The description is supplied by the caller on the element a screen reader
 * focuses, not here. A `Canvas` announces nothing, so a description set
 * inside this composable would attach to an element that is never
 * focused and the plot would stay invisible to a blind reader — which is
 * a bug a sibling project had and fixed.
 */
@Composable
fun PressureTrace(
    values: List<Float>,
    scale: BaroScale,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (values.isEmpty()) return@Canvas

            val w = size.width
            val h = size.height
            val stepX = if (values.size > 1) w / (values.size - 1) else w

            // A hairline at the centre, so a flat series reads as flat
            // rather than as an absence of data.
            drawLine(
                color = Hairline,
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1f,
            )

            if (values.size > 1) {
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = h * (1f - scale.norm(v))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Accent.copy(alpha = 0.5f),
                    style = Stroke(width = 1.5f),
                )
            }

            // The measurements, over the interpolation.
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h * (1f - scale.norm(v))
                drawCircle(
                    color = Accent,
                    radius = 2.5f,
                    center = Offset(x, y),
                )
            }
        }
    }
}
