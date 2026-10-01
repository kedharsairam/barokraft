package com.krafttools.barokraft.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * ## Two decisions that are not stylistic
 *
 * **Tabular figures on every number.** A pressure reading updates every
 * ten minutes and a temperature every hour, and with proportional
 * figures a value that changes reflows every glyph after it — the digits
 * appear to shimmer, and a number that shimmers is harder to read than a
 * number that does not. Monospace is not a choice here so much as the
 * absence of a problem.
 *
 * **A large figure, capped.** The headline numbers scale with the user's
 * font size, because that is what Dynamic Type is for, but the figure is
 * capped at a size that still fits the column at 1.35x. A display that
 * reflows off the edge of the screen at 2x is a broken instrument rather
 * than an accessible one, and there is no way to fit "31.4°" and
 * "1013.2 hPa" on one line at arbitrary scale. The detail below the
 * figure carries everything the cap pushed out.
 */
internal val label = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = TextPrimary,
)

/** The hero figure. Tabular, and the only place the size is allowed to grow. */
internal val figure = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 44.sp,
    lineHeight = 48.sp,
    color = TextPrimary,
)

/** A secondary figure, for a value that is not the headline. */
internal val smallFigure = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    color = TextPrimary,
)

/** The hero figure, scaled for the user's font size up to a cap. */
@Composable
internal fun FigureText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = figure, modifier = modifier)
}
