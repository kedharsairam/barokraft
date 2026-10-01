package com.krafttools.barokraft.core

/**
 * What this app is allowed to say, given what it actually has.
 *
 * ## The four states
 *
 * The obvious design assumes two: with a network you get a forecast,
 * without one you get nothing. That is wrong, and wrong in a way that
 * would make the app broken on a large class of device.
 *
 * A great many phones have **no barometer** — every budget handset, most
 * tablets, some laptops — and a perfectly good network. That combination
 * is not a degraded mode. It is an ordinary phone showing an ordinary
 * forecast, and treating it as a failure would be both wrong and insulting
 * to a very large number of people.
 *
 * So neither source is a prerequisite. The app always works with at least
 * one, and the four states are:
 *
 * | Barometer | Network | State |
 * |---|---|---|
 * | yes | yes | [BOTH] — forecast, and a barometer that can check it |
 * | yes | no | [BAROMETER_ONLY] — nowcast, with its error band |
 * | no | yes | [NETWORK_ONLY] — forecast, no nowcast |
 * | no | no | [NEITHER] — and the app says so plainly |
 *
 * ## What never happens
 *
 * A forecast and a nowcast are never averaged into one number. They are
 * different claims about different things — one is a 9–25 km grid cell
 * six hours from now, the other is a pressure extrapolation from your own
 * sensor — and blending them would produce a number that is confidently
 * attributed to neither.
 */
enum class SourceState {
    /** Forecast plus a local barometer that can audit the calibration. */
    BOTH,

    /** No network. Tendency, altitude and a banded nowcast. */
    BAROMETER_ONLY,

    /** No pressure sensor. The forecast, at its own resolution. */
    NETWORK_ONLY,

    /** Neither. The app says what it would need, in those words. */
    NEITHER,
}

/** Classify the state from what the device has and whether it has signal. */
fun sourceState(hasBarometer: Boolean, online: Boolean): SourceState = when {
    hasBarometer && online -> SourceState.BOTH
    hasBarometer -> SourceState.BAROMETER_ONLY
    online -> SourceState.NETWORK_ONLY
    else -> SourceState.NEITHER
}

/**
 * The capabilities a state unlocks.
 *
 * Every one of these is queried by the screen rather than inferred from
 * which objects happen to be non-null, so that "the app cannot say this"
 * is a decision recorded in one place and testable, rather than an
 * accident of which branch ran.
 */
data class Capabilities(
    val canShowPressure: Boolean,
    val canShowAltitude: Boolean,
    val canShowTendency: Boolean,
    val canShowNowcast: Boolean,
    val canShowForecast: Boolean,
    /** Can the network be used to check the local sea-level reference? */
    val canAuditReference: Boolean,
) {
    companion object {
        fun of(state: SourceState): Capabilities = when (state) {
            SourceState.BOTH -> Capabilities(
                canShowPressure = true,
                canShowAltitude = true,
                canShowTendency = true,
                canShowNowcast = true,
                canShowForecast = true,
                canAuditReference = true,
            )

            SourceState.BAROMETER_ONLY -> Capabilities(
                canShowPressure = true,
                canShowAltitude = true,
                canShowTendency = true,
                canShowNowcast = true,
                canShowForecast = false,
                canAuditReference = false,
            )

            SourceState.NETWORK_ONLY -> Capabilities(
                canShowPressure = false,
                canShowAltitude = false,
                canShowTendency = false,
                canShowNowcast = false,
                canShowForecast = true,
                canAuditReference = false,
            )

            SourceState.NEITHER -> Capabilities(
                canShowPressure = false,
                canShowAltitude = false,
                canShowTendency = false,
                canShowNowcast = false,
                canShowForecast = false,
                canAuditReference = false,
            )
        }
    }
}

/**
 * What the app does with a request it cannot satisfy.
 *
 * Modelled on PulseKraft's [Policy], and for the same reason: the part of
 * an app capable of refusing to spend the user's data or print a number
 * it does not believe is the part that has to be reachable from a test.
 * Every rule here is a pure function of its inputs, including a device
 * that reports nothing, because a revoked permission throws and some
 * devices lie about their capabilities — and where the platform cannot be
 * asked, the cautious reading is the only safe default.
 */
object Policy {

    /**
     * Whether to spend the network at all.
     *
     * Refused offline, and refused when the forecast is fresh enough that
     * a request would be a waste. The second rule is the one that matters
     * on a metered connection: a weather app that refetches a forecast
     * every time the screen opens is a data bill nobody agreed to.
     */
    fun shouldFetch(
        online: Boolean,
        lastFetchedMillis: Long?,
        nowMillis: Long,
        maxAgeMillis: Long = 3L * 3_600_000L,
    ): Boolean {
        if (!online) return false
        if (lastFetchedMillis == null) return true
        return nowMillis - lastFetchedMillis > maxAgeMillis
    }

    /**
     * Whether a barometer reading should be recorded.
     *
     * Requires a sensor. Does *not* require the screen to be in the
     * foreground, because the sampling loop only runs when the screen is
     * open anyway — this is here so that the rule is stated where it can
     * be tested rather than implied by a lifecycle callback somewhere.
     */
    fun shouldSample(hasBarometer: Boolean): Boolean = hasBarometer

    /**
     * Whether an altitude may be printed at all.
     *
     * Requires a usable reference, not merely a present one. A stale QNH
     * produces a plausible-looking number that is hundreds of metres
     * wrong, and a plausible-looking wrong number is worse than no number
     * because it is believed.
     */
    fun mayShowAltitude(staleness: ReferenceStaleness): Boolean =
        staleness == ReferenceStaleness.FRESH || staleness == ReferenceStaleness.AGING

    /**
     * Whether a nowcast band may be shown.
     *
     * Refused when there is not enough history, and refused when the
     * ensemble band is so wide that printing a centre value would be
     * inventing precision. "Wide" is a real answer; the centre of a very
     * wide band is not.
     */
    fun mayShowNowcast(points: List<NowcastPoint>): Boolean {
        if (points.isEmpty()) return false
        val last = points.last()
        return !last.isUncertain
    }

    /**
     * Whether the two sources may be compared.
     *
     * Only ever true in [SourceState.BOTH], and only when the local
     * reference is good enough for the comparison to mean anything. An
     * audit performed with a stale reference would "discover" drift that
     * is its own error.
     */
    fun mayCompareSources(
        state: SourceState,
        staleness: ReferenceStaleness,
    ): Boolean = state == SourceState.BOTH && staleness != ReferenceStaleness.STALE
}
