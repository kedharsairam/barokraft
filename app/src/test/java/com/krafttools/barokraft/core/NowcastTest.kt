package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/** The ensemble nowcast, and the band that is its real product. */
class NowcastTest {

    private val start = 1_700_000_000_000L

    /** A perfectly linear history at `hpaPerHour`, sampled every minute. */
    private fun linear(hpaPerHour: Float, samples: Int = 60): List<PressureSample> =
        (0 until samples).map { i ->
            PressureSample(
                atMillis = start + i * 60_000L,
                hpa = 1004f + i / 60f * hpaPerHour,
            )
        }

    @Test
    fun `no nowcast without enough history`() {
        assertTrue(nowcast(listOf(PressureSample(start, 1004f)), start).isEmpty())
        assertTrue(nowcast(emptyList(), start).isEmpty())
    }

    @Test
    fun `the horizon is six points at one hour each`() {
        val now = start + 59 * 60_000L
        val points = nowcast(linear(-1f), nowMillis = now)
        assertEquals(HORIZON_HOURS.toInt(), points.size)
        // First point is one step ahead, last is the full horizon.
        assertEquals(1L, (points.first().atMillis - now) / 3_600_000L)
        assertEquals(
            HORIZON_HOURS.toLong(),
            (points.last().atMillis - now) / 3_600_000L,
        )
    }

    @Test
    fun `a falling history projects lower pressure`() {
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        val change = nowcastTotalChange(points, currentHpa = 1002f)!!
        assertTrue("expected a fall, got $change", change < -6f)
    }

    @Test
    fun `a rising history projects higher pressure`() {
        val points = nowcast(linear(1.5f), nowMillis = start + 59 * 60_000L)
        val change = nowcastTotalChange(points, currentHpa = 1005.5f)!!
        assertTrue("expected a rise, got $change", change > 4f)
    }

    @Test
    fun `a flat history projects flat`() {
        val points = nowcast(linear(0f), nowMillis = start + 59 * 60_000L)
        val change = kotlin.math.abs(nowcastTotalChange(points, currentHpa = 1004f)!!)
        assertTrue("a flat history should project flat, moved $change", change < 2f)
    }

    @Test
    fun `the band widens with the horizon`() {
        val points = nowcast(linear(-1.5f), nowMillis = start + 59 * 60_000L)
        assertTrue(
            "the first point should be more certain than the last",
            points.first().uncertaintyHpa < points.last().uncertaintyHpa,
        )
    }

    @Test
    fun `the nowcast branch is reachable in practice`() {
        // The regression that mattered. With the first guess at the
        // perturbation sizes the band reached 6.12 hPa at six hours, above
        // MIN_CERTAINTY_HPA, so Policy.mayShowNowcast refused every real
        // forecast and the verdict collapsed to "steady" permanently. The
        // ensemble would have been running, correct, and invisible.
        //
        // So this asserts reachability directly, not just band shape.
        listOf(0f, -0.5f, -2f, 2f).forEach { rate ->
            val points = nowcast(linear(rate), nowMillis = start + 59 * 60_000L)
            assertTrue(
                "a $rate hPa/h history must produce a showable nowcast, " +
                    "band was ${points.last().uncertaintyHpa}",
                Policy.mayShowNowcast(points),
            )
        }
    }

    @Test
    fun `the band stays inside the documented uncertainty at six hours`() {
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        val last = points.last()
        assertTrue(
            "six-hour band ${last.uncertaintyHpa} should be under " +
                "MIN_CERTAINTY_HPA=$MIN_CERTAINTY_HPA",
            last.uncertaintyHpa < MIN_CERTAINTY_HPA,
        )
    }

    @Test
    fun `the one-hour band is narrow enough to call narrow`() {
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        assertEquals(
            "an hour out a barometer should be confident",
            NowcastConfidence.NARROW,
            nowcastConfidence(points.first().uncertaintyHpa),
        )
    }

    @Test
    fun `the band is a real band, not zero`() {
        val points = nowcast(linear(-1f), nowMillis = start + 59 * 60_000L)
        assertTrue("band should be non-zero", points.last().uncertaintyHpa > 0.05f)
    }

    @Test
    fun `the band contains its own centre`() {
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        points.forEach { p ->
            assertTrue(
                "centre ${p.centreHpa} outside band ${p.lowHpa}..${p.highHpa}",
                p.lowHpa <= p.centreHpa && p.centreHpa <= p.highHpa,
            )
        }
    }

    @Test
    fun `the same data gives the same band twice`() {
        // A forecast that changes when you reopen the app is not a
        // forecast anyone can reason about, so the seed is fixed.
        val a = nowcast(linear(-1.5f), nowMillis = start + 59 * 60_000L)
        val b = nowcast(linear(-1.5f), nowMillis = start + 59 * 60_000L)
        assertEquals(a.map { it.lowHpa }, b.map { it.lowHpa })
        assertEquals(a.map { it.highHpa }, b.map { it.highHpa })
    }

    @Test
    fun `a different seed gives a different draw but the same shape`() {
        val a = nowcast(linear(-1.5f), nowMillis = start + 59 * 60_000L, seed = 1)
        val b = nowcast(linear(-1.5f), nowMillis = start + 59 * 60_000L, seed = 2)
        assertTrue("seed should perturb the draw", a.last().highHpa != b.last().highHpa)
        assertTrue("but not change the trend", a.last().centreHpa > b.last().centreHpa - 1f)
    }

    @Test
    fun `the horizon cannot be pushed past six hours`() {
        val points = nowcast(linear(-1f), nowMillis = start + 59 * 60_000L, horizonHours = 48f)
        assertTrue("horizon must be capped", points.size <= HORIZON_HOURS.toInt() + 1)
    }

    @Test
    fun `an accelerating history curves`() {
        // Pressure falling faster over time: a parabola, not a line.
        val curved = (0 until 60).map { i ->
            val t = i / 60f
            PressureSample(start + i * 60_000L, 1010f - 8f * t * t)
        }
        val points = nowcast(curved, nowMillis = start + 59 * 60_000L)
        assertTrue("a curve should drop faster than a line", points.last().centreHpa < 1002f)
    }

    @Test
    fun `a noisy history still produces a usable band`() {
        val noisy = (0 until 60).map { i ->
            PressureSample(
                start + i * 60_000L,
                1004f - i / 60f + if (i % 3 == 0) 0.08f else -0.08f,
            )
        }
        val points = nowcast(noisy, nowMillis = start + 59 * 60_000L)
        assertEquals(6, points.size)
        assertTrue(points.last().uncertaintyHpa.isFinite())
    }

    @Test
    fun `confidence is classified from the band width`() {
        assertEquals(NowcastConfidence.NARROW, nowcastConfidence(0.5f))
        assertEquals(NowcastConfidence.MODERATE, nowcastConfidence(3f))
        assertEquals(NowcastConfidence.WIDE, nowcastConfidence(9f))
    }

    @Test
    fun `the total change is measured from now, not from the first point`() {
        // The forecast starts one step ahead, so measuring from
        // points.first() would understate the change by exactly one hour.
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        val change = nowcastTotalChange(points, currentHpa = 1002f)!!
        val last = points.last().centreHpa
        val first = points.first().centreHpa
        assertEquals(last - 1002f, change, 0.001f)
        assertTrue("must not equal the one-step-shortened change", change != last - first)
    }

    @Test
    fun `the change band brackets the change`() {
        val points = nowcast(linear(-2f), nowMillis = start + 59 * 60_000L)
        val change = nowcastTotalChange(points, currentHpa = 1002f)!!
        val band = nowcastChangeBand(points, currentHpa = 1002f)!!
        assertTrue("change $change should sit in $band", change in band)
    }

    @Test
    fun `no change is reported without a forecast`() {
        assertNull(nowcastTotalChange(emptyList(), 1004f))
        assertNull(nowcastChangeBand(emptyList(), 1004f))
    }
}
