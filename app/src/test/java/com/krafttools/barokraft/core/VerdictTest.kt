package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The headline, branch by branch.
 *
 * Every branch of [verdict] is a test case here. That is the point of
 * making it a pure function of an input record: there is no path to the
 * screen that has not been through this file.
 */
class VerdictTest {

    private val now = 1_800_000_000_000L
    private val start = now - 59 * 60_000L

    private fun fallingSamples(hpaPerHour: Float) = (0 until 60).map { i ->
        PressureSample(start - (59 - i) * 60_000L, 1004f + (i - 59) / 60f * hpaPerHour)
    }

    private fun both(
        drift: Float? = null,
        code: Int? = null,
        staleness: ReferenceStaleness = ReferenceStaleness.FRESH,
        points: List<NowcastPoint> = emptyList(),
    ) = VerdictInput(
        state = SourceState.BOTH,
        nowMillis = now,
        staleness = staleness,
        severeWeatherCode = code,
        nowcastPoints = points,
        currentHpa = 1002f,
        referenceDriftHpa = drift,
    )

    // ── 1. Blocked ──────────────────────────────────────────────────────

    @Test
    fun `with neither source the app says what is missing`() {
        val v = verdict(VerdictInput(SourceState.NEITHER, now))
        assertEquals(VerdictTone.BLOCKED, v.tone)
        // The header line states the cause; the headline must not repeat
        // it, or the same sentence is on screen twice.
        assertEquals("Nothing to read", v.headline)
        assertTrue(
            "the cause belongs in the detail",
            // "pressure sensor", not "barometer": the detail names the
            // hardware in the words a user would use for it, and the
            // header already carries the other phrasing.
            v.detail!!.contains("pressure sensor"),
        )
        assertTrue(v.detail!!.contains("nothing it can honestly tell you"))
    }

    // ── 2. Severe weather outranks everything ───────────────────────────

    @Test
    fun `severe weather outranks a stale reference`() {
        // Precedence test. If calibration came first, hail would go
        // unmentioned, and that is the wrong order for a safety statement.
        val v = verdict(
            both(code = 95, staleness = ReferenceStaleness.STALE),
        )
        assertEquals(VerdictTone.WARNING, v.tone)
        assertTrue(v.headline.contains("Thunderstorm"))
    }

    @Test
    fun `severe weather outranks a divergence`() {
        val v = verdict(both(code = 99, drift = 8f))
        assertEquals(VerdictTone.WARNING, v.tone)
    }

    @Test
    fun `severe weather reaches a network-only phone too`() {
        val v = verdict(
            VerdictInput(SourceState.NETWORK_ONLY, now, severeWeatherCode = 75),
        )
        assertEquals(VerdictTone.WARNING, v.tone)
    }

    @Test
    fun `a mild code is not treated as severe`() {
        val v = verdict(both(code = 3, points = emptyList()))
        assertFalse(v.tone == VerdictTone.WARNING)
    }

    // ── 3. Calibration ──────────────────────────────────────────────────

    @Test
    fun `a stale reference withholds the altitude and says why`() {
        val v = verdict(both(staleness = ReferenceStaleness.STALE))
        assertEquals(VerdictTone.CALIBRATION, v.tone)
        assertTrue(v.detail!!.contains("sea-level reference"))
    }

    @Test
    fun `an unset reference does not trigger the calibration branch`() {
        // Unset means no altitude was ever claimed, so there is nothing to
        // retract. The quiet branch handles it.
        val v = verdict(both(staleness = ReferenceStaleness.UNSET))
        assertEquals(VerdictTone.QUIET, v.tone)
    }

    @Test
    fun `an ageing reference still shows altitude, with a warning`() {
        val v = verdict(both(staleness = ReferenceStaleness.AGING))
        assertFalse(v.tone == VerdictTone.CALIBRATION)
    }

    // ── 4. Divergence ───────────────────────────────────────────────────

    @Test
    fun `drift beyond the threshold is reported`() {
        val v = verdict(both(drift = 4.5f))
        assertEquals(VerdictTone.DIVERGENCE, v.tone)
        assertTrue(v.detail!!.contains("4.5"))
    }

    @Test
    fun `drift below the threshold is not a headline`() {
        // Below 3 hPa the disagreement is more likely grid-cell offset than
        // a bad reference, and calling it a divergence would be its own
        // kind of error.
        val v = verdict(both(drift = 1.2f, points = emptyList()))
        assertFalse(v.tone == VerdictTone.DIVERGENCE)
    }

    @Test
    fun `drift is reported in the right direction`() {
        val above = verdict(both(drift = 4f))
        assertTrue(above.detail!!.contains("above"))
        val below = verdict(both(drift = -4f))
        assertTrue(below.detail!!.contains("below"))
    }

    // ── 5. Nowcast ──────────────────────────────────────────────────────

    @Test
    fun `a falling nowcast leads with falling`() {
        val points = nowcast(fallingSamples(-2f), nowMillis = now)
        val v = verdict(both(points = points))
        assertEquals(VerdictTone.NOWCAST, v.tone)
        assertTrue(v.headline.contains("falling"))
    }

    @Test
    fun `the nowcast states its uncertainty`() {
        val points = nowcast(fallingSamples(-2f), nowMillis = now)
        val v = verdict(both(points = points))
        assertTrue("must admit the band", v.detail!!.contains("Uncertainty"))
    }

    @Test
    fun `the nowcast quotes the change it implies`() {
        val points = nowcast(fallingSamples(-2f), nowMillis = now)
        val change = nowcastTotalChange(points, 1002f)!!
        val v = verdict(both(points = points))
        val detail = requireNotNull(v.detail) {
            "the nowcast branch must always carry a detail sentence, got ${v.headline}"
        }
        assertTrue(
            "detail should carry the magnitude, got: $detail",
            detail.contains(fmt1(kotlin.math.abs(change))),
        )
    }

    @Test
    fun `a nowcast is impossible on a network-only phone`() {
        // No barometer means no local pressure, so there is nothing to
        // project. The state must win over the data.
        val points = nowcast(fallingSamples(-2f), nowMillis = now)
        val v = verdict(
            VerdictInput(
                state = SourceState.NETWORK_ONLY,
                nowMillis = now,
                nowcastPoints = points,
                currentHpa = 1002f,
            ),
        )
        assertFalse(v.tone == VerdictTone.NOWCAST)
    }

    @Test
    fun `a nowcast is possible offline`() {
        val points = nowcast(fallingSamples(-2f), nowMillis = now)
        val v = verdict(
            VerdictInput(
                state = SourceState.BAROMETER_ONLY,
                nowMillis = now,
                nowcastPoints = points,
                currentHpa = 1002f,
            ),
        )
        assertEquals(VerdictTone.NOWCAST, v.tone)
    }

    @Test
    fun `a very wide band falls through to quiet rather than being printed`() {
        val wide = listOf(NowcastPoint(now + 3_600_000L, 1002f, 990f, 1015f))
        val v = verdict(both(points = wide))
        assertFalse("a wide band must not become a headline", v.tone == VerdictTone.NOWCAST)
    }

    // ── 6. Quiet ────────────────────────────────────────────────────────

    @Test
    fun `nothing notable says so plainly`() {
        val v = verdict(both(points = emptyList(), drift = 0f))
        assertEquals(VerdictTone.QUIET, v.tone)
    }

    @Test
    fun `a network-only phone admits it has no barometer`() {
        val v = verdict(VerdictInput(SourceState.NETWORK_ONLY, now))
        assertTrue(v.detail!!.contains("no barometer"))
    }

    @Test
    fun `an unset reference is mentioned in the detail`() {
        val v = verdict(both(staleness = ReferenceStaleness.UNSET, points = emptyList()))
        assertTrue(v.detail!!.contains("no sea-level reference"))
    }

    @Test
    fun `fmt1 always shows one decimal place`() {
        assertEquals("3.0", fmt1(3f))
        assertEquals("3.5", fmt1(3.46f))
        assertEquals("0.0", fmt1(0.02f))
    }
}
