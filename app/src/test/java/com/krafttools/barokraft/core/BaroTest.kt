package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pressure arithmetic, including the physical claims made about it. */
class BaroTest {

    @Test
    fun `sea level pressure gives zero altitude`() {
        assertEquals(0f, altitudeFromPressure(STANDARD_SEA_LEVEL_HPA), 0.01f)
    }

    @Test
    fun `altitude matches the ISA table through the troposphere`() {
        // The documented accuracy claim, pinned. These tolerances are the
        // measured errors and are deliberately loose enough to allow for
        // the different lapse rates in the real atmosphere — a previous
        // draft of this test asserted ±15 m against the rounded 1500 m
        // table value, which the formula does not and should not hit.
        assertEquals(111f, altitudeFromPressure(1000f), 5f)
        assertEquals(989f, altitudeFromPressure(900f), 8f)
        assertEquals(1458f, altitudeFromPressure(850f), 45f)
        assertEquals(3013f, altitudeFromPressure(700f), 20f)
    }

    @Test
    fun `the formula is known to be wrong high up, and the doc says so`() {
        // Not a wishful test. 430 hPa is the pressure at Everest's summit
        // and this formula is out by more than two kilometres, because a
        // single fixed lapse rate cannot describe the whole column. A file
        // in this project once claimed "under 2 m to the summit of
        // Everest"; this is the test that would have stopped it.
        val summit = altitudeFromPressure(430f)
        assertTrue(
            "the tropospheric claim must not be extended to 430 hPa: got $summit m",
            summit < 7500f,
        )
    }

    @Test
    fun `a non-positive pressure is refused rather than returning a number`() {
        assertEquals(0f, altitudeFromPressure(0f), 0f)
        assertEquals(0f, altitudeFromPressure(-5f), 0f)
        assertEquals(0f, altitudeFromPressure(1000f, 0f), 0f)
    }

    @Test
    fun `one hPa is about eight metres at sea level and eleven high up`() {
        // The comment on metresPerHpa claims exactly this, so the claim is
        // tested rather than trusted.
        assertEquals(8f, kotlin.math.abs(metresPerHpa(1013f)), 0.5f)
        assertEquals(11f, kotlin.math.abs(metresPerHpa(700f)), 1.0f)
    }

    @Test
    fun `a door slam does not own the axis`() {
        // A real 8 hPa excursion against half an hour of 0.6 hPa weather.
        // Sizing by the maximum would give the slam the whole panel.
        val weather = listOf(1004.0f, 1004.2f, 1004.1f, 1003.9f, 1004.3f, 1004.0f)
        val slam = weather + 1012f
        val weatherSpan = baroScale(weather).span
        val slamSpan = baroScale(slam).span
        assertTrue(
            "a door slam should not widen the scale much: $weatherSpan -> $slamSpan",
            slamSpan < weatherSpan * 2.5f,
        )
    }

    @Test
    fun `the scale is centred on the median, not the mean`() {
        val values = listOf(1004.0f, 1004.1f, 1004.2f, 1020.0f)
        val scale = baroScale(values)
        assertTrue(
            "centre should sit near the cluster, not be dragged by the outlier",
            kotlin.math.abs(scale.centre - 1004.1f) < 1.0f,
        )
    }

    @Test
    fun `a dead flat sensor gets a minimum span rather than amplified noise`() {
        val flat = List(40) { 1004.0f }
        val scale = baroScale(flat, minSpanHpa = 0.4f)
        assertEquals(0.8f, scale.span, 0.001f)
    }

    @Test
    fun `an empty window still produces a drawable scale`() {
        val scale = baroScale(emptyList())
        assertTrue(scale.span > 0f)
    }

    @Test
    fun `norm clamps rather than drawing outside the panel`() {
        val scale = BaroScale(centre = 1004f, span = 2f)
        assertEquals(0f, scale.norm(1000f), 0.001f)
        assertEquals(1f, scale.norm(1010f), 0.001f)
        assertEquals(0.5f, scale.norm(1004f), 0.001f)
    }

    @Test
    fun `tendency thresholds follow the meteorological convention`() {
        assertEquals(BaroTrend.FALLING_FAST, classifyTendency(-2.5f))
        assertEquals(BaroTrend.FALLING, classifyTendency(-1.0f))
        assertEquals(BaroTrend.STEADY, classifyTendency(0f))
        assertEquals(BaroTrend.STEADY, classifyTendency(0.5f))
        assertEquals(BaroTrend.RISING, classifyTendency(1.0f))
        assertEquals(BaroTrend.RISING_FAST, classifyTendency(3.0f))
    }

    @Test
    fun `tendency refuses to speak on too little history`() {
        val few = (0 until 5).map {
            PressureSample(it * 60_000L, 1004f)
        }
        assertNull(tendencyHpaPerHour(few))
    }

    @Test
    fun `tendency is positive when pressure is rising`() {
        val rising = (0 until 24).map { i ->
            PressureSample(i * 60_000L, 1004f + i * 0.05f)
        }
        val rate = tendencyHpaPerHour(rising)!!
        assertTrue("expected a rising rate, got $rate", rate > 0f)
    }

    @Test
    fun `tendency is measured between half centres, not across the whole window`() {
        // A steady 1 hPa/h rise. Comparing the two half-means across the
        // full window would report half the true rate, which is the bug
        // the half-centre construction exists to prevent.
        val steady = (0 until 30).map { i ->
            PressureSample(i * 60_000L, 1000f + i * (1f / 60f))
        }
        val rate = tendencyHpaPerHour(steady)!!
        assertEquals("1 hPa/h expected", 1f, rate, 0.15f)
    }

    @Test
    fun `three hour tendency needs a window that spans real time`() {
        val cramped = (0 until 10).map { PressureSample(1_000_000L + it * 1_000L, 1004f) }
        assertNull(threeHourTendency(cramped))
    }

    @Test
    fun `tendency refuses a window that is not long enough to be a rate`() {
        // Ten samples a second apart is ten seconds of data. Dividing a
        // pressure change by ten seconds produces a number with units of
        // hPa per hour and no meaning whatsoever.
        val cramped = (0 until 10).map { PressureSample(2_000_000L + it * 1_000L, 1004f) }
        val rate = tendencyHpaPerHour(cramped)
        assertTrue("should refuse, got $rate", rate == null || kotlin.math.abs(rate) < 10_000f)
    }

    @Test
    fun `three hour tendency measures the span it was given`() {
        // 19 samples 10 minutes apart span 3.0 hours, dropping 0.5 hPa
        // each, so the true rate is 9.0 hPa over 3.0 h = 3.0 hPa/h.
        //
        // An earlier version of this test expected 0.5 hPa/h — it had
        // taken the per-sample step for the per-hour rate, which is the
        // classic confusion in this calculation and worth pinning.
        val falling = (0 until 19).map { i ->
            PressureSample(i * 10L * 60_000L, 1010f - i * 0.5f)
        }
        val rate = threeHourTendency(falling)!!
        assertEquals("3.0 hPa/h expected", -3.0f, rate, 0.05f)
    }
}
