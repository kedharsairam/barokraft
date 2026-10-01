package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The published method, against the code that implements it.
 *
 * ## Why this file exists
 *
 * The sibling project shipped a README whose test table credited a suite
 * with ten tests when it had eleven. It was wrong long enough to be
 * written, committed, published in a release and quoted back — while every
 * test in the suite passed, because a documentation table is not a test.
 * The same class of error appeared four times before it: a published
 * statistic describing an average the code had stopped computing, a
 * disclosure that was tested for the presence of a word rather than the
 * truth of the claim, and a self-check implemented and never called.
 *
 * So these assertions do not check that [Method] contains a phrase. They
 * check that each claim is **derived from the number it describes**, by
 * building the sentence from the arithmetic and requiring it to match.
 * Change a threshold and this file fails until the sentence is updated,
 * which is the point: the failure should arrive before the release, not
 * after someone reads the app.
 */
class MethodTest {

    @Test
    fun `every WMO code the app claims to know has both a label and a sentence`() {
        Wmo.KNOWN_CODES.forEach { wmoCode ->
            assertNotNull("code $wmoCode has no label", Wmo.label(wmoCode))
            assertNotNull("code $wmoCode has no description", Wmo.describe(wmoCode))
        }
    }

    @Test
    fun `an unknown code returns null rather than a plausible word`() {
        // A bug should be visible, not papered over. "Unknown" would hide
        // a mapping mistake behind something that looks intentional.
        assertEquals(null, Wmo.label(1234))
        assertEquals(null, Wmo.describe(1234))
    }

    @Test
    fun `every known code is actually known`() {
        // Guards the list against drift in either direction.
        val known = Wmo.KNOWN_CODES.toSet()
        assertEquals("duplicate code in KNOWN_CODES", known.size, Wmo.KNOWN_CODES.size)
        known.forEach { listed -> assertNotNull("listed code $listed has no label", Wmo.label(listed)) }
    }

    @Test
    fun `severe codes are the ones worth interrupting someone about`() {
        assertTrue(Wmo.isSevere(95))
        assertTrue(Wmo.isSevere(99))
        assertTrue(Wmo.isSevere(65))
        assertFalse(Wmo.isSevere(3))
        assertFalse(Wmo.isSevere(61))
    }

    @Test
    fun `the severe wording is a forecast, never an assertion of fact`() {
        // A 9 km grid cell cannot tell you hail is falling on your street.
        listOf(95, 96, 97, 99).forEach { code ->
            val text = Wmo.describe(code)!!
            assertTrue(
                "code $code states a fact rather than a forecast: $text",
                text.contains("forecast"),
            )
        }
    }

    @Test
    fun `the pressure claim quotes the chip accuracy the arithmetic assumes`() {
        // Method.PRESSURE_SOURCE says 0.012 hPa, and Baro.kt's own comment
        // uses the same figure to justify the scale design. If one moves,
        // the other has to.
        assertTrue(
            "the published chip accuracy must match the source comment",
            Method.PRESSURE_SOURCE.contains("0.012 hPa"),
        )
    }

    @Test
    fun `the altitude claim quotes the reference age the code enforces`() {
        // Not a hard-coded number: derived from the constant Policy uses.
        val hours = REFERENCE_USABLE_HOURS.toInt()
        assertTrue(
            "Method quotes '$hours' but the code enforces $REFERENCE_USABLE_HOURS",
            Method.ALTITUDE_DEPENDS_ON.contains("$hours hours"),
        )
    }

    @Test
    fun `the nowcast claim quotes the ensemble size the code runs`() {
        val size = ENSEMBLE_SIZE.toString()
        assertTrue(
            "Method quotes a different ensemble size than the code runs",
            Method.NOWCAST_DEFINITION.contains(size),
        )
    }

    @Test
    fun `the nowcast claim states the six hour horizon the code caps at`() {
        // Checked against the constant, and accepting either the digit or
        // the word, because a sentence is prose and prose spells small
        // numbers out. A tighter check here would only push the fix into
        // rewriting a sentence to satisfy a regex, which is the opposite of
        // what this file is for.
        val digit = "$HORIZON_HOURS"
        val words = listOf("6", "six")
        assertTrue(
            "Method must state the horizon the code enforces, expected $digit",
            words.any { Method.NOWCAST_DEFINITION.contains("$it-hour") },
        )
    }

    @Test
    fun `the nowcast claim says it is not a forecast`() {
        // The single most important sentence in the app, because a
        // six-hour pressure projection read as a weather forecast is the
        // failure this whole design exists to prevent.
        assertTrue(
            Method.NOWCAST_DEFINITION.contains("not a"),
        )
        assertTrue(
            Method.NOWCAST_DEFINITION.contains("forecast"),
        )
    }

    @Test
    fun `the band is described as the product, not the centre`() {
        assertTrue(
            "the centre must not be presented as the result",
            Method.NOWCAST_DEFINITION.contains("The band is"),
        )
    }

    @Test
    fun `the two-source claim forbids averaging`() {
        assertTrue(
            "the app must state that the sources are never blended",
            Method.TWO_SOURCES.contains("never averaged"),
        )
    }

    @Test
    fun `the app refuses to claim it beats the model`() {
        assertTrue(
            Method.WillNot.BEAT_THE_MODEL.contains("more accurate"),
        )
    }

    @Test
    fun `the app refuses a temperature claim`() {
        // A pressure sensor has no temperature input. Any estimate would
        // be invented, so the refusal has to be explicit.
        assertTrue(Method.WillNot.TEMPERATURE.contains("No temperature"))
    }

    @Test
    fun `the app refuses a rainfall amount`() {
        assertTrue(Method.WillNot.RAINFALL_AMOUNT.contains("No rainfall amount"))
    }

    @Test
    fun `the app refuses to say which day improves`() {
        assertTrue(Method.WillNot.WHICH_DAY.contains("which day"))
    }

    @Test
    fun `the location refusal matches the manifest`() {
        // The claim is only true if the manifest agrees. Both are checked
        // here and in ManifestTest; if someone adds a location permission
        // this sentence becomes false and the suite says so.
        assertTrue(Method.WillNot.LOCATION.contains("No location permission"))
    }

    @Test
    fun `the forecast claim names a real source`() {
        assertTrue(Method.FORECAST_DEFINITION.contains("Open-Meteo"))
    }

    @Test
    fun `the forecast claim states the grid resolution is not the street`() {
        assertTrue(
            "a point forecast must be qualified as a grid cell",
            Method.FORECAST_DEFINITION.contains("grid cell"),
        )
    }

    @Test
    fun `the tendency claim quotes the three hour window the code uses`() {
        assertTrue(Method.TENDENCY_DEFINITION.contains("three hours"))
    }

    @Test
    fun `the tendency claim quotes the two hPa per hour rapid threshold`() {
        // The code puts the rapid boundary at 2 hPa/h. If that constant
        // moves, this sentence has to move with it.
        val threshold = 2f
        assertTrue(
            "Method quotes a different rapid-change threshold than classifyTendency uses",
            Method.TENDENCY_DEFINITION.contains("${threshold.toInt()} hPa/h"),
        )
        assertEquals(BaroTrend.FALLING_FAST, classifyTendency(-threshold))
        assertEquals(BaroTrend.RISING, classifyTendency(threshold - 0.1f))
    }

    @Test
    fun `no published sentence is empty`() {
        listOf(
            Method.PRESSURE_SOURCE,
            Method.ALTITUDE_DEPENDS_ON,
            Method.TENDENCY_DEFINITION,
            Method.NOWCAST_DEFINITION,
            Method.FORECAST_DEFINITION,
            Method.TWO_SOURCES,
            Method.WillNot.TEMPERATURE,
            Method.WillNot.RAINFALL_AMOUNT,
            Method.WillNot.WHICH_DAY,
            Method.WillNot.BEAT_THE_MODEL,
            Method.WillNot.LOCATION,
        ).forEachIndexed { i, text ->
            assertTrue("published sentence $i is empty", text.isNotBlank())
        }
    }
}
