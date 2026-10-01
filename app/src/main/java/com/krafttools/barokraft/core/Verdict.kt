package com.krafttools.barokraft.core

/**
 * The sentence the screen leads with.
 *
 * ## Why this is one pure function
 *
 * Every weather app puts its conclusion in a headline, and almost all of
 * them compute that headline in a view model where the branches are
 * impossible to enumerate. A claim that cannot be reached by a test is a
 * claim nobody has checked, and this app's entire premise is that its
 * claims are checkable.
 *
 * So the verdict is a pure function of a [VerdictInput], the precedence is
 * written out as an ordered `when`, and every branch is a test case. There
 * is no fourth state, no hidden fallback, and no path that reaches the UI
 * without having been through here.
 *
 * ## Precedence, and why it is this order
 *
 * 1. **Nothing works.** Before anything else, say what is missing. A
 *    headline about weather on a screen that cannot see weather is worse
 *    than an honest blank.
 * 2. **Severe weather forecast.** A safety statement outranks a
 *    convenience one. Nothing below this line matters if there is hail
 *    forecast and the user is outside.
 * 3. **The app does not trust its own altitude.** This app telling you
 *    that its own calibration has gone stale is more important than
 *    anything it could tell you about the weather, and burying it under a
 *    sun-and-cloud icon would be a misuse of the one advantage this app
 *    has over the four that merely relay a model.
 * 4. **The model and the ground disagree.** The single most useful thing
 *    this app can say, and the reason two sources exist.
 * 5. **The nowcast.** What the barometer expects, with its band.
 * 6. **Steady.** Nothing to report, said plainly.
 */
enum class VerdictTone {
    /** Nothing to work with. */
    BLOCKED,

    /** Severe weather in the forecast. */
    WARNING,

    /** The app's own reference has gone stale. */
    CALIBRATION,

    /** The two sources disagree. */
    DIVERGENCE,

    /** The nowcast, said with its uncertainty. */
    NOWCAST,

    /** Nothing notable. */
    QUIET,
}

/** Everything the verdict is allowed to consider. */
data class VerdictInput(
    val state: SourceState,
    val nowMillis: Long,
    val staleness: ReferenceStaleness = ReferenceStaleness.UNSET,
    val severeWeatherCode: Int? = null,
    val nowcastPoints: List<NowcastPoint> = emptyList(),
    val currentHpa: Float? = null,
    val referenceDriftHpa: Float? = null,
    val tendencyHpaPerHour: Float? = null,
)

/** The headline: a tone, a sentence, and the figure behind it. */
data class Verdict(
    val tone: VerdictTone,
    val headline: String,
    val detail: String?,
)

/**
 * Build the headline.
 *
 * Every branch returns a sentence that is derivable from the inputs, and
 * `VerdictTest` asserts each one against the number that produced it. A
 * test that greps the sentence for a word would pass while the sentence
 * said something false, which is the specific failure this project has
 * already made once and written a guard against.
 */
fun verdict(input: VerdictInput): Verdict = when (input.state) {

    // 1. Nothing to work with.
    SourceState.NEITHER -> Verdict(
        tone = VerdictTone.BLOCKED,
        headline = "No barometer, no connection",
        detail = "This app reads the pressure sensor and fetches a forecast. " +
            "Neither is available, so there is nothing it can honestly tell you.",
    )

    // 2. Severe weather outranks everything below.
    SourceState.BOTH, SourceState.NETWORK_ONLY -> {
        val severe = input.severeWeatherCode?.let { Wmo.isSevere(it) } == true
        if (severe) {
            val code = input.severeWeatherCode ?: -1
            Verdict(
                tone = VerdictTone.WARNING,
                headline = Wmo.label(code) ?: "Severe weather forecast",
                detail = Wmo.describe(code),
            )
        } else {
            steadyOrNowcast(input)
        }
    }

    SourceState.BAROMETER_ONLY -> steadyOrNowcast(input)
}

/**
 * The shared tail: calibration, divergence, nowcast, or quiet.
 *
 * Calibration and divergence only apply where there are two sources to
 * compare — a single-source app has nothing to compare and must not
 * pretend otherwise, which is why these are checked after the state has
 * already established that a barometer is present.
 */
private fun steadyOrNowcast(input: VerdictInput): Verdict {
    val capabilities = Capabilities.of(input.state)

    // 3. The app does not trust its own altitude.
    if (capabilities.canShowAltitude && input.staleness == ReferenceStaleness.STALE) {
        return Verdict(
            tone = VerdictTone.CALIBRATION,
            headline = "Altitude not shown",
            detail = "The sea-level reference is more than " +
                "${REFERENCE_USABLE_HOURS.toInt()} hours old, and sea-level pressure moves with " +
                "the weather. A number derived from it would be plausible and wrong, so it is " +
                "withheld. Set it again to restore altitude.",
        )
    }

    // 4. The two sources disagree.
    if (capabilities.canAuditReference) {
        val drift = input.referenceDriftHpa
        if (drift != null && kotlin.math.abs(drift) > REFERENCE_DRIFT_STALE_HPA) {
            val direction = if (drift > 0) "above" else "below"
            return Verdict(
                tone = VerdictTone.DIVERGENCE,
                headline = "Pressure is tracking the forecast",
                detail = "Your barometer reduced to sea level is " +
                    "${fmt1(kotlin.math.abs(drift))} hPa ${direction} the model. " +
                    "Either the reference has drifted or the model is behind — a barometer " +
                    "watching a front usually sees it first.",
            )
        }
    }

    // 5. The nowcast, with its band stated.
    if (capabilities.canShowNowcast && Policy.mayShowNowcast(input.nowcastPoints)) {
        val points = input.nowcastPoints
        val current = input.currentHpa
        if (points.isNotEmpty() && current != null) {
            val change = nowcastTotalChange(points, current) ?: 0f
            val band = nowcastChangeBand(points, current)
            val confidence = nowcastConfidence(points.last().uncertaintyHpa)
            val trend = classifyTendency(change / HORIZON_HOURS)
            return Verdict(
                tone = VerdictTone.NOWCAST,
                headline = "${trend.arrow} ${trend.label.lowercase()}",
                detail = buildString {
                    append(horizonWording(change, band))
                    append(" Uncertainty ")
                    append(confidence.label.lowercase())
                    append(", ±")
                    append(fmt1(points.last().uncertaintyHpa))
                    append(" hPa at six hours.")
                },
            )
        }
    }

    // 6. Nothing notable.
    return Verdict(
        tone = VerdictTone.QUIET,
        headline = quietHeadline(input),
        detail = quietDetail(input),
    )
}

private fun quietHeadline(input: VerdictInput): String {
    val tendency = input.tendencyHpaPerHour
    return if (tendency == null) {
        "Reading the barometer"
    } else {
        val trend = classifyTendency(tendency)
        "${trend.arrow} ${trend.label.lowercase()}"
    }
}

private fun quietDetail(input: VerdictInput): String? {
    val parts = mutableListOf<String>()
    input.currentHpa?.let { parts += "${fmt1(it)} hPa" }
    when (input.staleness) {
        ReferenceStaleness.AGING -> parts += "sea-level reference is ageing, so altitude may drift"
        ReferenceStaleness.UNSET -> parts += "no sea-level reference set, so no altitude"
        else -> Unit
    }
    if (input.state == SourceState.NETWORK_ONLY) {
        parts += "no barometer on this device, so no local nowcast"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun horizonWording(change: Float, band: ClosedFloatingPointRange<Float>?): String {
    val magnitude = kotlin.math.abs(change)
    val direction = if (change < 0) "lower" else "higher"
    val quantised = when {
        magnitude < 0.5f -> "About level"
        else -> "About ${fmt1(magnitude)} hPa $direction"
    }
    return if (band == null) {
        "$quantised in $HORIZON_HOURS.toInt() hours."
    } else {
        val width = kotlin.math.abs(band.endInclusive - band.start)
        "$quantised in $HORIZON_HOURS.toInt() hours, somewhere between " +
            "${fmt1(band.start)} and ${fmt1(band.endInclusive)} hPa, a spread of ${fmt1(width)}."
    }
}

/** One decimal place, and no thousands separator on a four-digit field. */
internal fun fmt1(v: Float): String {
    val r = kotlin.math.round(v * 10f) / 10f
    return if (r == r.toInt().toFloat()) "${r.toInt()}.0" else r.toString()
}
