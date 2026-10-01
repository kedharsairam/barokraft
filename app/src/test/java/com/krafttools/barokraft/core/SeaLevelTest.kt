package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sea-level reference, and the drift audit only two sources allow. */
class SeaLevelTest {

    private val now = 1_800_000_000_000L

    private fun ref(ageHours: Float) = SeaLevel(
        hpa = 1013f,
        atMillis = now - (ageHours * 3_600_000f).toLong(),
    )

    @Test
    fun `a fresh reference is fresh`() {
        assertEquals(ReferenceStaleness.FRESH, referenceStaleness(ref(1f), SeaLevelContext(now)))
    }

    @Test
    fun `an old but not ancient reference is ageing, not stale`() {
        assertEquals(ReferenceStaleness.AGING, referenceStaleness(ref(6f), SeaLevelContext(now)))
    }

    @Test
    fun `a reference past the usable window is stale`() {
        // This is the case the whole file exists for: sea-level pressure
        // moves with the weather, so a month-old reference is not a
        // calibration.
        assertEquals(ReferenceStaleness.STALE, referenceStaleness(ref(30f), SeaLevelContext(now)))
    }

    @Test
    fun `no reference is unset rather than fresh`() {
        assertEquals(ReferenceStaleness.UNSET, referenceStaleness(null, SeaLevelContext(now)))
    }

    @Test
    fun `altitude from a known height inverts correctly`() {
        // If we are 850 m up, then 850 hPa *is* sea level, and calibrating
        // against it must return 850 back.
        val seaLevel = seaLevelFromAltitude(stationHpa = 850f, altitudeMetres = 1500f)!!
        val recovered = altitudeFromPressure(850f, seaLevel)
        assertEquals(1500f, recovered, 5f)
    }

    @Test
    fun `calibrating at sea level returns the reading unchanged`() {
        val seaLevel = seaLevelFromAltitude(stationHpa = 1010f, altitudeMetres = 0f)!!
        assertEquals(1010f, seaLevel, 0.5f)
    }

    @Test
    fun `calibration refuses impossible inputs rather than dividing by zero`() {
        assertNull(seaLevelFromAltitude(0f, 100f))
        assertNull(seaLevelFromAltitude(1000f, 50_000f))
    }

    @Test
    fun `drift is signed and measured local minus model`() {
        assertEquals(2f, referenceDrift(localSeaLevelHpa = 1015f, modelSeaLevelHpa = 1013f), 0.001f)
        assertEquals(-2f, referenceDrift(localSeaLevelHpa = 1011f, modelSeaLevelHpa = 1013f), 0.001f)
    }

    @Test
    fun `loess smoothing reduces noise on a flat series`() {
        // Tested on a flat series, where "reduced deviation" is
        // unambiguous. An earlier version of this test added a ramp and
        // then measured deviation from the ramp, which conflated two
        // effects: a smoother *does* lag a ramp slightly, so the assertion
        // failed for a reason that had nothing to do with noise reduction.
        val flat = List(30) { 1013f }
        val noisy = flat.mapIndexed { i, v -> v + if (i % 2 == 0) 0.05f else -0.05f }
        val smoothed = loessSmooth(noisy)
        val before = deviation(flat, noisy)
        val after = deviation(flat, smoothed)
        assertTrue(
            "smoothing should reduce deviation on a flat series: $before -> $after",
            after < before,
        )
    }

    @Test
    fun `loess preserves a real trend rather than flattening it`() {
        // The property a median filter fails and loess does not: a
        // sustained ramp must come through as a ramp, because the whole
        // point of the smoother is to keep a moving reference and reject
        // jitter.
        val ramp = (0 until 30).map { it * 0.4f }
        val smoothed = loessSmooth(ramp)
        val originalRise = ramp.last() - ramp.first()
        val smoothedRise = smoothed.last() - smoothed.first()
        assertTrue(
            "a ramp must survive: $originalRise -> $smoothedRise",
            smoothedRise > originalRise * 0.85f,
        )
    }

    @Test
    fun `loess is a no-op on a short series rather than an error`() {
        val tiny = listOf(1f, 2f)
        assertEquals(tiny, loessSmooth(tiny))
    }

    @Test
    fun `the median filter removes a single outlier`() {
        val withSpike = listOf(10f, 10f, 10f, 99f, 10f, 10f, 10f)
        val filtered = medianFilter(withSpike, window = 5)
        assertTrue("the spike should be gone", filtered.all { it < 20f })
    }

    @Test
    fun `the median filter is a no-op when the series is shorter than the window`() {
        val tiny = listOf(1f, 2f)
        assertEquals(tiny, medianFilter(tiny, window = 5))
    }

    @Test
    fun `the median filter handles clipped windows at both ends`() {
        // Regression: indexing a clipped window by the nominal size walks
        // off the end of the slice, which is every element near the start
        // and end — exactly where a fresh reading arrives.
        val values = listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 100f)
        val filtered = medianFilter(values, window = 5)
        assertEquals(values.size, filtered.size)
        filtered.forEachIndexed { i, v ->
            assertTrue("index $i produced $v", v.isFinite())
        }
    }

    private fun deviation(expected: List<Float>, actual: List<Float>): Float =
        expected.indices.map { kotlin.math.abs(expected[it] - actual[it]) }.average().toFloat()
}
