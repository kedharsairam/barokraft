package com.krafttools.barokraft.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Sea-level reference: the number that turns a station pressure into an
 * altitude, and the single largest source of wrongness in any barometer
 * app.
 *
 * ## The problem this file exists to solve
 *
 * A phone's pressure sensor reports *station* pressure — the pressure at
 * the phone, which anywhere near sea level is close to sea
 * level and at 3,000 m in the Himalaya is about 700 hPa. To say anything
 * about altitude you have to reduce that to a sea-level reference, and
 * every app that skips the reduction is quietly reporting nonsense.
 *
 * ## The part nobody gets right
 *
 * **A sea-level reference is not a constant. It is a current value.**
 *
 * It is tempting to treat "my altitude is 15 m, so my QNH is X" as a
 * property of the *place* and to store it forever. It is not. Sea-level
 * pressure over any given spot rises and falls with the weather by tens of
 * hPa — that is the entire signal this app is built on. A reference set
 * three months ago is not a calibration, it is a wrong number with a
 * confident label on it, and it will make altitude wrong by hundreds of
 * metres while looking entirely plausible.
 *
 * So a reference here carries its age, and [referenceStaleness] is
 * willing to say the number should be discarded.
 */

/** How old a sea-level reference is allowed to be before it is a guess. */
const val REFERENCE_FRESH_HOURS = 3f

/** Beyond this, the reference is not a calibration and must not be used. */
const val REFERENCE_USABLE_HOURS = 12f

/**
 * A sea-level reference and when it was established.
 *
 * Deliberately carries no age and no freshness verdict of its own. Age is
 * relative to *now*, and a type that computed it would have to read a
 * clock, which would make every staleness question a side effect rather
 * than a pure function — and staleness is the single most consequential
 * decision in this app, so it is asked explicitly through
 * [referenceStaleness] with a clock the caller supplies.
 */
data class SeaLevel(
    val hpa: Float,
    val atMillis: Long,
)

/** Injected clock, so staleness is a pure function and not a global read. */
data class SeaLevelContext(val nowMillis: Long)

/** Age of a reference in hours, against an injected clock. */
fun SeaLevel.ageHours(context: SeaLevelContext): Float =
    (context.nowMillis - atMillis) / 3_600_000f

/**
 * What to do about a reference, as a thing the screen can act on.
 *
 * The distinction between [FRESH] and [AGING] and [STALE] is the whole
 * point: they produce different *behaviour*, not different wording.
 */
enum class ReferenceStaleness {
    /** Within [REFERENCE_FRESH_HOURS]. Trust the altitude. */
    FRESH,

    /** Past fresh, inside usable. Altitude shown, with an age warning. */
    AGING,

    /**
     * Past [REFERENCE_USABLE_HOURS]. The reference is not used at all and
     * the app says so rather than printing a number it does not believe.
     */
    STALE,

    /** Never set. */
    UNSET,
}

/** Classify a reference's staleness against an injected clock. */
fun referenceStaleness(reference: SeaLevel?, context: SeaLevelContext): ReferenceStaleness = when {
    reference == null -> ReferenceStaleness.UNSET
    reference.ageHours(context) > REFERENCE_USABLE_HOURS -> ReferenceStaleness.STALE
    reference.ageHours(context) > REFERENCE_FRESH_HOURS -> ReferenceStaleness.AGING
    else -> ReferenceStaleness.FRESH
}

/**
 * The sea-level pressure implied by a station reading and a known
 * altitude. This is [altitudeFromPressure] inverted.
 *
 * `p0 = p / (1 - alt/44330)^(1/0.1903)`, which is how a QNH is actually
 * established: you find out how high you are, you read the pressure, and
 * this is the number that makes the two agree.
 */
fun seaLevelFromAltitude(stationHpa: Float, altitudeMetres: Float): Float? {
    if (stationHpa <= 0f) return null
    if (altitudeMetres >= 44_330f) return null
    val base = 1.0 - altitudeMetres / 44_330.0
    if (base <= 0.0) return null
    return (stationHpa / base.pow(1.0 / 0.1903)).toFloat()
}

/**
 * How far a local sea-level estimate has drifted from the model's, in hPa.
 *
 * ## Why this is the best thing in the app
 *
 * This is only possible because there are two independent sources. The
 * model publishes `pressure_msl` — sea-level pressure — for your grid
 * cell. The barometer publishes station pressure, which [seaLevelFromAltitude]
 * can reduce to sea level if the reference is good. When those two
 * disagree persistently, the *reference* has drifted, because the
 * reference is a snapshot of a value that moves with the weather.
 *
 * So the network forecast does not merely add information to this app. It
 * **audits the offline path.** No offline app can know its own calibration
 * is stale; this one can, and it can say so.
 *
 * The absolute value is returned rather than a verdict, because whether
 * 2 hPa of drift matters depends on what altitude is being computed, and
 * that decision belongs to the caller.
 */
fun referenceDrift(
    localSeaLevelHpa: Float,
    modelSeaLevelHpa: Float,
): Float = localSeaLevelHpa - modelSeaLevelHpa

/**
 * Drift beyond which a local reference should be considered stale.
 *
 * Three hPa is roughly 25 m of altitude error at sea level, which is
 * larger than most buildings. Below that the disagreement is model error
 * and grid-cell offset rather than a bad reference, and discarding a
 * perfectly good calibration because a 9 km grid cell does not know about
 * your valley would be its own kind of error.
 */
const val REFERENCE_DRIFT_STALE_HPA = 3f

/**
 * Temporally smooth a series, Loess-style.
 *
 * ## Why smoothing is not optional here
 *
 * Every estimate of sea-level pressure from a single station reading
 * inherits the chip's noise, and the chip's noise is about 0.012 hPa —
 * which at sea level is 10 cm of altitude, fine, but it accumulates
 * visibly over a trace and it makes a *changing* reference look like a
 * noisy one. A reference that jitters cannot be compared against the
 * model, because the comparison needs to distinguish drift from noise.
 *
 * The weights are triangular — heaviest at the centre of the window,
 * falling to zero at the edges — which is the defining property of LOESS.
 * A uniform moving average would blur a genuine step change in pressure
 * into a ramp, and a genuine step is exactly the event this app exists to
 * catch.
 *
 * @param values the series, oldest first
 * @param fractionOfWindow how much of the series to consider, 0..1
 */
fun loessSmooth(values: List<Float>, fractionOfWindow: Float = 0.4f): List<Float> {
    if (values.size < 3) return values
    val n = values.size
    val width = (n * fractionOfWindow.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(3)
    val halfWidth = (width - 1) / 2

    return (0 until n).map { i ->
        val lo = (i - halfWidth).coerceAtLeast(0)
        val hi = (i + halfWidth).coerceAtMost(n - 1)
        var num = 0f
        var den = 0f
        for (j in lo..hi) {
            val distance = abs(j - i).toFloat()
            val w = 1f - distance / (halfWidth + 1f)
            num += w * values[j]
            den += w
        }
        if (den <= 0f) values[i] else num / den
    }
}

/**
 * Median-filter a series, for comparison against [loessSmooth].
 *
 * Kept separate and separately testable because the two do different
 * jobs. The median is the right tool for a *single* outlier — one door
 * slam — and the wrong tool for a trend, because a median filter has a
 * zero response to a ramp and would flatten a real pressure change into
 * nothing. Using one where the other belongs is a bug that looks like
 * working code.
 */
fun medianFilter(values: List<Float>, window: Int = 5): List<Float> {
    if (values.size < window || window < 3) return values
    val half = window / 2
    return values.indices.map { i ->
        val lo = (i - half).coerceAtLeast(0)
        val hi = (i + half).coerceAtMost(values.size - 1)
        val slice = values.subList(lo, hi + 1).sorted()
        // The window may be clipped at either end, so the median is
        // indexed against the slice's own length rather than the nominal
        // window size. Using the nominal size would walk off the end of a
        // clipped slice — which is every element near the start and end of
        // the series, i.e. exactly where a fresh reading arrives.
        slice[slice.size / 2]
    }
}
