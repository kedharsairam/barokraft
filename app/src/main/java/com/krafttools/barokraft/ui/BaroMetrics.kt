package com.krafttools.barokraft.ui

import androidx.compose.ui.unit.dp

/**
 * The dimensions that belong to this app and to no other.
 *
 * Spacing, radius, type and touch targets come from kraft-foundation and are shared across the
 * portfolio. The values here are sky geometry and layout caps that would be wrong in any
 * other app — the same test that decides an app's accent belongs to the app and not to the
 * foundation.
 *
 * Each was a literal at its point of use, most carrying a comment explaining why that
 * number. The comments move with the values; a metric separated from its reason is just a
 * number with an alibi.
 */
object BaroMetrics {

    /** Sky background height: the sky fills the top of the forecast screen. */
    val SkyHeight = 560.dp

    /**
     * Contrast-guarantee scrim height. Sits over the sky, under the hero text: without it a
     * light sky puts white text at 1.6:1, unreadable. Palettes cannot all go darker without
     * making noon look like dusk, so the text colour is fixed and the background adjusts.
     */
    val SkyScrimHeight = 400.dp

    /** Day-label width cap in the week strip: long day names ellipsize instead of pushing. */
    val DayLabelMaxWidth = 76.dp

    /** Nowcast card minimum: keeps its shape while data loads. */
    val NowcastMinHeight = 88.dp

    /** Place picker sheet height: results list with room to scroll. */
    val PlacePickerHeight = 280.dp
}
