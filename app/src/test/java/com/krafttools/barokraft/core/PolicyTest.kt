package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app is allowed to say, in each of the four states. */
class PolicyTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `all four capability states are distinct and correct`() {
        assertEquals(SourceState.BOTH, sourceState(hasBarometer = true, online = true))
        assertEquals(SourceState.BAROMETER_ONLY, sourceState(hasBarometer = true, online = false))
        assertEquals(SourceState.NETWORK_ONLY, sourceState(hasBarometer = false, online = true))
        assertEquals(SourceState.NEITHER, sourceState(hasBarometer = false, online = false))
    }

    @Test
    fun `a phone with no barometer but a network is a normal state, not a failure`() {
        // The whole reason this enum has four members and not two. Every
        // budget handset and most tablets are in this state.
        val caps = Capabilities.of(SourceState.NETWORK_ONLY)
        assertTrue("must still show a forecast", caps.canShowForecast)
        assertFalse("must not claim a pressure reading", caps.canShowPressure)
    }

    @Test
    fun `both sources unlocks the audit that only two sources allow`() {
        val both = Capabilities.of(SourceState.BOTH)
        val baroOnly = Capabilities.of(SourceState.BAROMETER_ONLY)
        assertTrue(both.canAuditReference)
        assertFalse("nothing to compare against offline", baroOnly.canAuditReference)
    }

    @Test
    fun `neither state offers nothing`() {
        val caps = Capabilities.of(SourceState.NEITHER)
        assertFalse(caps.canShowPressure)
        assertFalse(caps.canShowForecast)
        assertFalse(caps.canShowNowcast)
        assertFalse(caps.canAuditReference)
    }

    @Test
    fun `offline never fetches`() {
        assertFalse(Policy.shouldFetch(online = false, lastFetchedMillis = null, nowMillis = now))
    }

    @Test
    fun `a first fetch always happens when online`() {
        assertTrue(Policy.shouldFetch(online = true, lastFetchedMillis = null, nowMillis = now))
    }

    @Test
    fun `a fresh forecast is not refetched`() {
        // The metered-data rule. A weather app that refetches every time
        // the screen opens is a data bill nobody agreed to.
        assertFalse(
            Policy.shouldFetch(
                online = true,
                lastFetchedMillis = now - 60_000L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `a stale forecast is refetched`() {
        assertTrue(
            Policy.shouldFetch(
                online = true,
                lastFetchedMillis = now - 4L * 3_600_000L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `sampling requires a sensor`() {
        assertTrue(Policy.shouldSample(hasBarometer = true))
        assertFalse(Policy.shouldSample(hasBarometer = false))
    }

    @Test
    fun `a stale reference withholds altitude entirely`() {
        // A plausible wrong number is worse than no number, because it is
        // believed.
        assertFalse(Policy.mayShowAltitude(ReferenceStaleness.STALE))
        assertFalse(Policy.mayShowAltitude(ReferenceStaleness.UNSET))
        assertTrue(Policy.mayShowAltitude(ReferenceStaleness.FRESH))
        assertTrue(Policy.mayShowAltitude(ReferenceStaleness.AGING))
    }

    @Test
    fun `an empty nowcast cannot be shown`() {
        assertFalse(Policy.mayShowNowcast(emptyList()))
    }

    @Test
    fun `a very wide band is refused rather than printed with a shrug`() {
        val wide = listOf(NowcastPoint(0L, 1004f, 995f, 1015f))
        assertFalse(Policy.mayShowNowcast(wide))
    }

    @Test
    fun `a tight band is shown`() {
        val tight = listOf(NowcastPoint(0L, 1004f, 1003.5f, 1004.5f))
        assertTrue(Policy.mayShowNowcast(tight))
    }

    @Test
    fun `sources are only compared when there are two of them`() {
        assertFalse(
            Policy.mayCompareSources(SourceState.BAROMETER_ONLY, ReferenceStaleness.FRESH),
        )
        assertFalse(
            Policy.mayCompareSources(SourceState.NETWORK_ONLY, ReferenceStaleness.FRESH),
        )
        assertTrue(
            Policy.mayCompareSources(SourceState.BOTH, ReferenceStaleness.FRESH),
        )
    }

    @Test
    fun `sources are not compared with a stale reference`() {
        // An audit run with a stale reference would discover drift that is
        // its own error.
        assertFalse(
            Policy.mayCompareSources(SourceState.BOTH, ReferenceStaleness.STALE),
        )
    }
}
