package com.krafttools.barokraft.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Barometric arithmetic, in one tested place.
 *
 * ## Why the scale works the way it does
 *
 * A barometer's signal is *small*. The chip is good to about 0.012 hPa,
 * and the entire weather story of a day is 10–40 hPa. So a pressure trace
 * drawn against a zero-based axis is a picture of a flat line, and any
 * scale that snaps to a "nice" round number larger than the variation is
 * worse than no graph at all. Both mistakes are easy to make and invisible
 * until the trace is on screen.
 *
 * ## Where this came from
 *
 * The arithmetic and the reasoning were written for KraftTools' barometer
 * tool and are reused here deliberately, because it is the same physics
 * and because a barometer that quietly got the arithmetic wrong in one
 * app and right in another would be a worse outcome than either alone.
 * The tests came with it, and they are the specification.
 */

/** International Standard Atmosphere pressure at sea level, in hPa. */
const val STANDARD_SEA_LEVEL_HPA = 1013.25f

/**
 * One pressure reading, at a moment in time.
 *
 * Time is epoch milliseconds and pressure is in hPa, which is what
 * `Sensor.TYPE_PRESSURE` reports. Nothing about this type knows about
 * Android: it is constructed from a FloatArray by the sensor layer and
 * consumed by pure functions, which is what lets every rule in this file
 * be tested on the JVM with no device.
 */
data class PressureSample(
    val atMillis: Long,
    val hpa: Float,
)

/**
 * Altitude from station pressure and a sea-level reference, in metres.
 *
 * The hypsometric form: `alt = 44330 * (1 - (p / p0) ^ 0.1903)`, where
 * 44330 is R*T/(M*g) for the standard atmosphere and 0.1903 is 1/5.255,
 * the standard atmosphere's lapse-rate exponent.
 *
 * ## Where this is accurate, and where it is not
 *
 * This is the hypsometric equation with a single fixed lapse rate, and
 * that assumption holds well in the troposphere and badly above it. The
 * measured error against the standard atmosphere:
 *
 * | Pressure | Formula | Standard atmosphere | Error |
 * |---|---|---|---|
 * | 1000 hPa | 111 m | ~111 m | under 1 m |
 * | 900 hPa | 989 m | ~988 m | ~1 m |
 * | 850 hPa | 1458 m | 1500 m (table) | ~42 m |
 * | 700 hPa | 3013 m | 3000 m (table) | ~13 m |
 *
 * A draft of this file claimed accuracy "under 2 m from sea level to the
 * summit of Everest". That was false, and a test caught it rather than a
 * reader: 430 hPa — the pressure at Everest's summit — comes out of this
 * formula as 6672 m, an error of more than two kilometres. The equation
 * assumes one lapse rate for the whole column and the real atmosphere
 * does not have one.
 *
 * **The honest claim is tropospheric**: good to a few tens of metres
 * through the range a barometer is actually used in, and not to be
 * trusted in the high Himalaya or anywhere a mountaineer would use it.
 *
 * It is also not an absolute altitude — only as good as the sea-level
 * reference it is given, which is why [SeaLevel] exists and why a stale
 * QNH is worse than none.
 */
fun altitudeFromPressure(
    pressureHpa: Float,
    seaLevelHpa: Float = STANDARD_SEA_LEVEL_HPA,
): Float {
    if (pressureHpa <= 0f || seaLevelHpa <= 0f) return 0f
    return 44330f * (1 - (pressureHpa / seaLevelHpa).toDouble().pow(0.1903)).toFloat()
}

/**
 * Metres of altitude per hPa of pressure change, positive when pressure
 * rises.
 *
 * This exists to put an error bar on a reading rather than implying a
 * precision the sensor does not have: at sea level one hPa is worth about
 * 8 m, and at 3000 m about 11 m. An altitude printed to the metre from a
 * chip good to 0.012 hPa is a false precision, and this is the number
 * that says so.
 */
fun metresPerHpa(pressureHpa: Float, seaLevelHpa: Float = STANDARD_SEA_LEVEL_HPA): Float =
    altitudeFromPressure(pressureHpa - 1f, seaLevelHpa) - altitudeFromPressure(
        pressureHpa,
        seaLevelHpa,
    )

/**
 * A pressure trace's vertical scale.
 *
 * Unlike every other trace in this app, a pressure trace is centred on
 * the mean and sized to the *variation*, because a barometer's signal is
 * the change and not the value. Zero-based, or snapped to a round number,
 * would draw 1004.0 hPa as a flat line pinned at the top of the panel and
 * hide the entire trend.
 */
data class BaroScale(val centre: Float, val span: Float) {

    /** Normalise a pressure into 0..1 for drawing. */
    fun norm(p: Float): Float = ((p - centre) / span + 0.5f).coerceIn(0f, 1f)

    /** The value at the bottom of the panel. */
    val floor: Float get() = centre - span / 2f

    /** The value at the top of the panel. */
    val ceiling: Float get() = centre + span / 2f
}

/**
 * Fit a scale to a window of pressures.
 *
 * Centred on the **median** and sized by a **percentile** of the
 * deviation, never the mean and never the maximum. The reason is
 * physical. Closing a door is a genuine 8 hPa excursion; the whole
 * weather story of half an hour is about 0.6 hPa. Sizing to the maximum
 * let the door own the axis and crushed the weather into 2% of the panel
 * — a graph that was technically correct and completely useless. A median
 * centre and a 90th-percentile spread put the weather across roughly 45%
 * of the panel, and the door clips off the top, which is the honest
 * depiction: it genuinely is off the scale.
 *
 * The span never drops below [minSpanHpa], so a dead-flat sensor shows a
 * centred line instead of amplifying noise into a storm.
 */
fun baroScale(values: List<Float>, minSpanHpa: Float = 0.4f): BaroScale {
    if (values.isEmpty()) return BaroScale(STANDARD_SEA_LEVEL_HPA, minSpanHpa * 2f)
    val sorted = values.sorted()
    val n = sorted.size
    val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    val deviations = values.map { abs(it - median) }.sorted()
    val spread = deviations[(n - 1) * 90 / 100]
    val half = maxOf(spread * 1.6f, minSpanHpa)
    return BaroScale(median, half * 2f)
}

/**
 * How a pressure tendency should be described to a person.
 *
 * The thresholds are the meteorological convention, not numbers chosen to
 * make a chart look convincing. Anything past about 2 hPa/h is a *rapid*
 * change, which is 6 hPa in three hours — the difference between a
 * passing shower and a front arriving.
 */
enum class BaroTrend(val label: String, val arrow: String) {
    FALLING_FAST("Falling fast", "↓"),
    FALLING("Falling", "↓"),
    STEADY("Steady", "→"),
    RISING("Rising", "↑"),
    RISING_FAST("Rising fast", "↑"),
}

/**
 * Classify a tendency in hPa per hour.
 *
 * Note what this does *not* say. It is not a forecast. "Falling fast" is
 * a statement about the last three hours of pressure, and turning it into
 * "rain by lunchtime" would be a claim the sensor cannot support. The
 * step from a tendency to a weather condition is [Nowcast]'s job, and it
 * comes with an error band for a reason.
 */
fun classifyTendency(hpaPerHour: Float): BaroTrend = when {
    hpaPerHour <= -2f -> BaroTrend.FALLING_FAST
    hpaPerHour < -0.5f -> BaroTrend.FALLING
    hpaPerHour <= 0.5f -> BaroTrend.STEADY
    hpaPerHour < 2f -> BaroTrend.RISING
    else -> BaroTrend.RISING_FAST
}

/**
 * Tendency in hPa per hour from a window of samples.
 *
 * Returns `null` until there is enough history to say anything at all.
 * Below [minSamples] the answer is noise, and a confident-sounding rate
 * computed from noise is worse than no rate at all — the user cannot see
 * that it was noise, so they will believe it.
 *
 * The rate is taken between the centres of the first and second half of
 * the window, which is the honest estimate. Comparing the two means
 * against the *whole* window would report the rate at half its true value
 * purely as an artefact of how the samples are laid out.
 */
fun tendencyHpaPerHour(
    samples: List<PressureSample>,
    minSamples: Int = 12,
): Float? {
    if (samples.size < minSamples) return null
    val half = samples.size / 2
    if (half < 2) return null

    val first = samples.take(half)
    val second = samples.drop(half)

    // Elapsed time between the centres of the two halves, in hours.
    val firstCentre = (first.first().atMillis + first.last().atMillis) / 2.0
    val secondCentre = (second.first().atMillis + second.last().atMillis) / 2.0
    val spanHours = (secondCentre - firstCentre) / 3_600_000.0
    if (spanHours <= 0.0) return null

    val firstMean = first.map { it.hpa }.average()
    val secondMean = second.map { it.hpa }.average()
    return ((secondMean - firstMean) / spanHours).toFloat()
}

/**
 * A three-hour tendency, which is the number meteorology actually uses.
 *
 * Three hours is long enough that a passing door or a gust cannot own the
 * answer, and short enough to be useful. A one-hour tendency is noise and
 * a six-hour one is already history.
 */
fun threeHourTendency(samples: List<PressureSample>): Float? {
    if (samples.size < 2) return null
    val now = samples.last().atMillis
    val threeHoursAgo = now - 3L * 3_600_000L
    val recent = samples.filter { it.atMillis >= threeHoursAgo }
    // Need the window to actually span some time, or this is a division
    // by a near-zero and the answer is noise dressed as a rate.
    if (recent.size < 2) return null
    val spanMillis = recent.last().atMillis - recent.first().atMillis
    if (spanMillis < 600_000L) return null
    return (recent.last().hpa - recent.first().hpa) / (spanMillis / 3_600_000f)
}
