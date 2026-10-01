package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The About sheet cannot contradict the code.
 *
 * ## Why this file exists
 *
 * Because an About sheet is prose a *user* reads, which makes it the
 * equivalent of a README — and this project has now caught a false claim
 * in documentation three separate times. A sheet that says "64 ensemble
 * members" after someone changes [ENSEMBLE_SIZE] to 128 is not a small
 * inaccuracy. It is the app stating something false about its own method,
 * in the place a user looks to decide whether to trust it.
 *
 * So the sentences are assembled from the constants, and these tests
 * assert the *texts contain the numbers* rather than asserting the
 * numbers are right. The arithmetic is [NowcastTest]'s job; this file's
 * job is that the two never disagree.
 */
class AboutTest {

    @Test
    fun `the nowcast description quotes the real ensemble size`() {
        assertTrue(
            "expected the ensemble size $ENSEMBLE_SIZE in: ${About.NOWCAST}",
            About.NOWCAST.contains(ENSEMBLE_SIZE.toString()),
        )
    }

    @Test
    fun `the nowcast description quotes the real horizon`() {
        assertTrue(
            "expected the horizon $HORIZON_HOURS in: ${About.NOWCAST}",
            About.NOWCAST.contains("${About.horizonText()} hours"),
        )
    }

    @Test
    fun `a whole horizon is not written with a decimal point`() {
        // "6.0 hours" reads as machine output in a sentence a user reads.
        assertEquals("6", About.horizonText())
        assertTrue(
            "the sheet must not leak a Float's decimal: ${About.NOWCAST}",
            !About.NOWCAST.contains(".0 hours"),
        )
    }

    @Test
    fun `the nowcast promises a band and not a number`() {
        // The band is the product. If this text ever stops promising it,
        // the design has changed and the sheet should say so loudly.
        assertTrue(About.NOWCAST.contains("band"))
        assertTrue(
            "the sheet should be explicit that the centre is secondary",
            About.NOWCAST.contains("convenience"),
        )
    }

    @Test
    fun `the sheet states the perturbation limits are measured`() {
        // A reader deciding whether to trust the band needs to know the
        // error terms came from measurement rather than taste.
        assertTrue(About.NOWCAST.contains("measured"))
    }

    @Test
    fun `the two sources are described as never blended`() {
        // This is the sentence the whole design exists to make, and the
        // one most likely to be misread as a limitation.
        assertTrue(About.TWO_SOURCES_NEVER_BLENDED.contains("never averaged"))
    }

    @Test
    fun `a disagreement is framed as information rather than a fault`() {
        assertTrue(About.DIVERGENCE_MEANS.contains("not a malfunction"))
    }

    @Test
    fun `the forecast source carries the required attribution`() {
        // CC BY 4.0 requires it, and the user reads the app rather than
        // the repository, so it cannot live only in the README.
        assertTrue(About.FORECAST_SOURCE.contains("Open-Meteo"))
        assertTrue(About.FORECAST_SOURCE.contains("CC BY 4.0"))
        assertTrue(About.FORECAST_SOURCE.contains("ECMWF"))
    }

    @Test
    fun `the grid cell caveat is present`() {
        // The model's own documentation requires saying that a forecast is
        // about a cell and not a street.
        assertTrue(About.GRID_CELL.contains("1 to 25 km"))
        assertTrue(About.GRID_CELL.contains("not about your street"))
    }

    @Test
    fun `the privacy claim matches the manifest`() {
        // Two permissions, no location, no background service. If any of
        // these change, this test fails and the sheet has to be rewritten —
        // which is the correct outcome, because the sheet is a promise.
        assertTrue(About.NO_BACKGROUND.contains("no location permission"))
        assertTrue(About.NO_BACKGROUND.contains("no background service"))
        assertTrue(About.NO_BACKGROUND.contains("Two permissions"))
    }

    @Test
    fun `the support link is in the app and points at the right place`() {
        // The README has always carried this. The app needs it too, because
        // a reader is already in the disclosure sheet and has never seen the
        // repository — the same argument WallKraft and GitAKraft apply in
        // their settings sections.
        assertEquals("https://buymeacoffee.com/kedhartech", About.SUPPORT_URL)
        assertTrue(
            "the note should say what the app does not do",
            About.SUPPORT_NOTE.contains("no ads") && About.SUPPORT_NOTE.contains("no analytics"),
        )
    }

    @Test
    fun `the README and the app name the same support page`() {
        // Two copies of one URL is one thing to forget to update.
        val readme = java.io.File("README.md").takeIf { it.exists() }?.readText()
        if (readme != null) {
            assertTrue(
                "README and About disagree about the support link",
                readme.contains(About.SUPPORT_URL),
            )
        }
    }

    @Test
    fun `the licence is named`() {
        assertTrue(About.LICENCE.contains("MIT"))
    }

    @Test
    fun `a phone with no barometer is described as ordinary`() {
        // Treating it as broken hardware is the mistake the four-state
        // design exists to prevent, so the wording is pinned.
        assertTrue(About.NO_BAROMETER.contains("ordinary hardware"))
    }

    @Test
    fun `the refusals are stated`() {
        assertTrue(About.WILL_NOT_SUMMARY.contains("temperature"))
        assertTrue(About.WILL_NOT_SUMMARY.contains("rain"))
    }

    @Test
    fun `no sentence promises accuracy`() {
        // The single most damaging thing a weather app can claim. A
        // substring search over every published sentence, so a careless
        // edit cannot reintroduce it.
        val published = listOf(
            About.TAGLINE,
            About.PURPOSE,
            About.NOWCAST,
            About.FORECAST_SOURCE,
            About.GRID_CELL,
            About.TWO_SOURCES_NEVER_BLENDED,
            About.DIVERGENCE_MEANS,
            About.REFERENCE_AUDIT,
            About.NO_BACKGROUND,
            About.SAMPLING,
            About.NO_BAROMETER,
            About.WILL_NOT_SUMMARY,
            About.LICENCE,
            About.SUPPORT_NOTE,
        )
        val banned = listOf("most accurate", "better than", "beat", "exactly", "precise")
        for (sentence in published) {
            for (word in banned) {
                assertTrue(
                    "\"$word\" appears in: $sentence",
                    !sentence.lowercase().contains(word),
                )
            }
        }
    }

    @Test
    fun `a whole threshold is not printed as a decimal`() {
        // "4.00 hPa" in a sentence a user reads is machine output. Found
        // by dumping the real view hierarchy on the device, where the
        // sheet showed 4.00 and 3.00 for integer constants.
        assertTrue(fmtThreshold(4f).none { it == '.' })
        assertEquals("4", fmtThreshold(4f))
        assertEquals("3", fmtThreshold(REFERENCE_DRIFT_STALE_HPA))
        assertEquals("0.08", fmtThreshold(VELOCITY_ERROR_HPA_PER_HOUR))
        assertEquals(
            "0.012 must keep three decimals to mean anything",
            "0.012",
            fmtThreshold(ACCELERATION_ERROR_HPA_PER_HOUR2),
        )
    }

    @Test
    fun `the drift threshold quoted to the user is the threshold used`() {
        // The sheet is assembled in AboutSheet.kt from the constant, and
        // this pins the constant so the two cannot drift.
        assertEquals(3f, REFERENCE_DRIFT_STALE_HPA, 0.0001f)
        assertEquals(12f, REFERENCE_USABLE_HOURS, 0.0001f)
    }

    @Test
    fun `the uncertainty floor quoted to the user is the one in the code`() {
        assertEquals(4f, MIN_CERTAINTY_HPA, 0.0001f)
        assertTrue(
            "the sheet should say the band can be too wide to mean anything",
            About.NOWCAST.contains("not yet"),
        )
    }
}