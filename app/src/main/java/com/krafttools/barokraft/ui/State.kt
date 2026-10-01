package com.krafttools.barokraft.ui

import com.krafttools.barokraft.core.Capabilities
import com.krafttools.barokraft.core.PressureSample
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SeaLevel
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.Verdict
import com.krafttools.barokraft.net.Failure
import com.krafttools.barokraft.net.OpenMeteo
import com.krafttools.barokraft.net.Protocol

/**
 * The state contract, in one place.
 *
 * ## Why the screen is a function of this and nothing else
 *
 * Because the alternative is a screen that reads from sensors, from a
 * network client, from DataStore and from a clock, and can therefore only
 * be tested on a device with all four present. This record has no Android
 * in it, so every state below — including all four of
 * [SourceState], a stale reference, a divergent pair, a rate-limited
 * network and a hard failure — is constructible in a test in a
 * millisecond.
 *
 * That is the same split PulseKraft uses, and the reason twenty-one
 * instrumented tests there reach states that would otherwise need a
 * roaming SIM and forty seconds of patience each.
 */
data class MeasureState(
    /** What the device has and whether it has signal. */
    val sourceState: SourceState,

    /** True when the sensor layer is currently delivering. */
    val sampling: Boolean = false,

    /** The local pressure history, oldest first. */
    val samples: List<PressureSample> = emptyList(),

    /** The latest reading, for the figure. */
    val currentHpa: Float? = null,

    /** The user's sea-level reference, if they have set one. */
    val reference: SeaLevel? = null,

    val staleness: ReferenceStaleness = ReferenceStaleness.UNSET,

    /** Altitude in metres, withheld when the reference is not usable. */
    val altitudeMetres: Float? = null,

    /**
     * The nowcast band, when there is one worth drawing.
     *
     * Kept on the state rather than recomputed in the view so the verdict
     * and the plot are guaranteed to be describing the *same* ensemble.
     * Two calls to [com.krafttools.barokraft.core.nowcast] with the same
     * inputs are deterministic, but recomputing in a composable is a
     * place for the two to drift apart, and a plot that disagrees with
     * the headline above it is worse than no plot.
     */
    val nowcast: List<com.krafttools.barokraft.core.NowcastPoint> = emptyList(),

    /** The computed headline. Never assembled by the view. */
    val verdict: Verdict? = null,

    /** The forecast, when one has been fetched or restored from cache. */
    val forecast: Protocol.Forecast? = null,

    /** When the forecast was fetched, for the age label. */
    val forecastAtMillis: Long? = null,

    /**
     * The model's sea-level pressure for the current hour, kept so the
     * drift figure and the pressure it came from are on screen together.
     */
    val modelPressure: Float? = null,

    /** A place the user picked, or the one they saved. */
    val place: OpenMeteo.Place? = null,

    /** The failure, if the last fetch did not produce data. */
    val failure: Failure? = null,

    /** True while a fetch is in flight. */
    val fetching: Boolean = false,

    /** The city search query and its results. */
    val searchQuery: String = "",
    val searchResults: List<OpenMeteo.Place> = emptyList(),
    val searching: Boolean = false,

    /** The city picker is open. */
    val pickingPlace: Boolean = false,

    /** The method disclosure is open. */
    val showingMethod: Boolean = false,

    /** The about sheet is open. */
    val showingAbout: Boolean = false,

    /** The sea-level reference sheet is open. */
    val showingReference: Boolean = false,

    /** The city search has never returned anything for this query. */
    val searchEmpty: Boolean = false,
) {
    val capabilities: Capabilities get() = Capabilities.of(sourceState)

    /** True when there is a forecast and it is old enough to label. */
    fun forecastAgeHours(nowMillis: Long): Float? =
        forecastAtMillis?.let { (nowMillis - it) / 3_600_000f }

    /**
     * The model's sea-level pressure for the current hour, which is what
     * the drift audit compares against.
     *
     * Null when there is no forecast or the hour cannot be matched —
     * which is the honest answer, because a mismatch would produce a drift
     * figure computed against the wrong hour and that is worse than no
     * figure.
     */
    fun modelSeaLevelPressure(nowMillis: Long): Float? =
        forecast?.hourAt(nowMillis)?.seaLevelPressureHpa

    val isEmpty: Boolean
        get() = currentHpa == null && forecast == null
}
