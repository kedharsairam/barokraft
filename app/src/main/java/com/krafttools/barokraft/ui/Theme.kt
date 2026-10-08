package com.krafttools.barokraft.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The palette, measured rather than chosen.
 *
 * ## Why these values and not nicer-looking ones
 *
 * Contrast ratios were computed against the ink colours they sit on, and
 * the accents were checked for both WCAG AA and for behaviour under
 * deuteranopia. That last check is the one that decided the palette: the
 * conventional "green means good, red means bad" pairing is invisible to
 * roughly one man in twelve, and a weather app that signals severity with
 * a colour half its users cannot see has failed at its one job.
 *
 * So severity is carried by **the words and the weight**, and the colour
 * is reinforcement rather than the message. The trend accents are
 * deliberately a blue-to-violet spread, which survives both protanopia
 * and deuteranopia, and the warning accent is amber rather than red so it
 * is not mistaken for a red/green pairing at a glance.
 *
 * Dark only. A barometer is read in a tent, in a car at night, and before
 * dawn, and a light theme for those is a theme nobody asked for.
 */

/** The page. Very slightly blue-black rather than pure black. */
val Ink = Color(0xFF0B0B10)

/** Raised surfaces. Three steps, so depth is a value rather than a shadow. */
val Surface1 = Color(0xFF13131A)
val Surface2 = Color(0xFF1A1A24)
val Surface3 = Color(0xFF232330)

/** Text. [InkMuted] is 4.6:1 on [Ink] — AA for body text, not just large. */
val TextPrimary = Color(0xFFF2F2F7)
val TextSecondary = Color(0xFFA8A8B8)
val TextMuted = Color(0xFF7A7A8C)

/** Hairlines and dividers. */
val Hairline = Color(0xFF2A2A38)

/**
 * The accent. Blue-violet, because this is an instrument and the colour
 * should read as measurement rather than as weather.
 */
val Accent = Color(0xFF8B7BF7)

/** The accent, dimmed for large fills. */
val AccentDim = Color(0xFF4A3F9E)

/**
 * Trend accents. A blue-to-violet spread, chosen so that "falling" and
 * "rising" remain distinguishable with any form of colour blindness.
 *
 * Falling is cooler and rising is warmer, which survives every common
 * dichromacy because the distinction is *blue versus orange* rather than
 * *red versus green*. The arrow glyph in [com.krafttools.barokraft.core.BaroTrend]
 * is the primary signal; this is the reinforcement.
 */
val Falling = Color(0xFF6BA8FF)
val Rising = Color(0xFFFFB067)
val Steady = Color(0xFF8A8A9C)

/**
 * Severity. Amber, not red.
 *
 * Red would be the obvious choice and the wrong one: paired against a
 * green-family "improving" accent it becomes the exact red/green pairing
 * that deuteranopia erases. Amber against blue-violet does not.
 */
val Warning = Color(0xFFFFC14D)

/** Destructive or unavailable. Desaturated so it never reads as "rising". */
val Critical = Color(0xFFE86A6A)

/** The confidence band fill. Low alpha so the trace stays readable through it. */
val BandFill = Color(0x338B7BF7)

/**
 * Rain probability, when it is high enough to matter.
 *
 * Blue rather than the accent violet, so it reads as *water* rather than as
 * app chrome and is distinct from the pressure accents on the same screen.
 */
val WaterBlue = Color(0xFF5FB0F5)

/** The two ends of a daily temperature range bar. */
val Cool = Color(0xFF6BA8FF)
val Warm = Color(0xFFFFB067)

/**
 * The app's colour scheme, assembled from the palette above.
 *
 * This file previously declared colours and nothing else — no theme composable existed
 * anywhere, so every MaterialTheme.colorScheme reference in the app resolved to Material's
 * own defaults and the app was inconsistent with itself without reporting it. That is the
 * defect `type.m3-wrapper-present` exists for, and this app was the app it was written for.
 *
 * The mapping is structural where possible, semantic where it must be: surfaces and text
 * follow the M3 roles directly, while Falling/Rising/Steady and Warning/Critical stay
 * outside the scheme as status colours (like the foundation's own per-app accent rule —
 * a speed test and a barometer should not look like the same product, and neither should
 * two different pressure trends).
 */
private val BaroColors = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    primaryContainer = AccentDim,
    onPrimaryContainer = TextPrimary,
    secondary = Cool,
    onSecondary = Ink,
    tertiary = Warm,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    error = Critical,
    onError = TextPrimary,
)

/**
 * The theme wrapper. Every screen enters through here; without it, Material colours and
 * type resolve to defaults silently.
 */
@Composable
fun BaroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BaroColors,
        typography = BaroTypography,
        content = content,
    )
}
