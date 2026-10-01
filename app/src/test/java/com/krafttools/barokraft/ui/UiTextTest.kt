package com.krafttools.barokraft.ui

import com.krafttools.barokraft.core.HORIZON_HOURS
import com.krafttools.barokraft.core.PressureSample
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.fmt1
import com.krafttools.barokraft.core.nowcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The sentences the barometer panel prints.
 *
 * ## Why these are tested at all
 *
 * Because the nowcast caption is the only place the app shows an *estimate*
 * rather than a reading, and an estimate that does not state its own
 * uncertainty is a forecast wearing a measurement's clothes. The wording is
 * therefore part of the correctness of the feature, not decoration.
 */
class UiTextTest {

    private val start = 1_800_000_000_000L

    /** Three hours of falling pressure, ten minutes apart. */
    private fun fallingHistory(): List<PressureSample> =
        (0 until 19).map { i -> PressureSample(start + i * 600_000L, 1010f - i * 0.05f) }

    private fun points() = nowcast(fallingHistory(), nowMillis = start + 18 * 600_000L)

    @Test
    fun `the nowcast caption names the horizon`() {
        val caption = nowcastCaption(points(), HORIZON_HOURS)
        assertTrue("expected the horizon in: $caption", caption.contains("6 hours"))
    }

    @Test
    fun `the nowcast caption says the band is the honest part`() {
        // The sentence exists so a reader can tell which number to trust,
        // and so the app cannot quietly present the centre as a reading.
        val caption = nowcastCaption(points(), HORIZON_HOURS)
        assertTrue("expected the band to be called out: $caption", caption.contains("band"))
        assertTrue(
            "the caption should admit which part is uncertain: $caption",
            caption.contains("honest"),
        )
    }

    @Test
    fun `the nowcast caption states the uncertainty as a half-width`() {
        // uncertaintyHpa is the full width between the edges. Printing it
        // as a plus-or-minus would double the stated uncertainty.
        val p = points().last()
        val caption = nowcastCaption(points(), HORIZON_HOURS)
        assertTrue(
            "expected plus or minus ${fmt1(p.uncertaintyHpa / 2f)}: $caption",
            caption.contains(fmt1(p.uncertaintyHpa / 2f)),
        )
        assertFalse(
            "must not quote the full width as a half-width: $caption",
            caption.contains("plus or minus ${fmt1(p.uncertaintyHpa)} hPa"),
        )
    }

    @Test
    fun `the nowcast caption says which way it is going`() {
        val caption = nowcastCaption(points(), HORIZON_HOURS)
        assertTrue("falling history should read as falling: $caption", caption.contains("falling"))
    }

    @Test
    fun `a flat ensemble reads as about level rather than rising`() {
        val flat = (0 until 19).map { i -> PressureSample(start + i * 600_000L, 1010f) }
        val caption = nowcastCaption(nowcast(flat, nowMillis = start + 18 * 600_000L), HORIZON_HOURS)
        assertTrue("expected 'about level' in: $caption", caption.contains("about level"))
    }

    @Test
    fun `an empty ensemble produces no caption`() {
        // Otherwise the panel prints an empty line, which reads as a
        // rendering fault rather than as "there is nothing yet".
        assertEquals("", nowcastCaption(emptyList(), HORIZON_HOURS))
    }

    @Test
    fun `reference staleness is described in words`() {
        // A raw enum name on screen is the app printing its own
        // implementation detail at the reader.
        val words = ReferenceStaleness.entries.map { stalenessWord(it) }
        assertTrue(
            "no staleness word may contain its enum name: $words",
            words.none { w -> ReferenceStaleness.entries.any { w.contains(it.name) } },
        )
        for (w in words) assertTrue("empty staleness word", w.isNotBlank())
    }

    @Test
    fun `a stale reference is described as not to be trusted`() {
        assertTrue(stalenessWord(ReferenceStaleness.STALE).contains("wrong"))
    }

    @Test
    fun `no caption leaks a method call or a null`() {
        val caption = nowcastCaption(points(), HORIZON_HOURS)
        for (leak in listOf(".toInt()", ".toFloat()", ".toString()", "null")) {
            assertFalse("\"$leak\" leaked into: $caption", caption.contains(leak))
        }
    }

    @Test
    fun `the caption quotes the real ensemble size`() {
        // The number of runs behind the plot, not the number of points
        // drawn — the plot samples the band and the band came from all of
        // them.
        val p = points()
        assertTrue(
            "caption should mention the ${p.size} runs",
            nowcastCaption(p, HORIZON_HOURS).contains(p.size.toString()),
        )
    }

    @Test
    fun `no user-facing string names a specific place`() {
        // A named example in a placeholder reads as a default the app chose
        // for you. The city field used to name a real town, which is both a
        // location this app does not have and someone's home address in a
        // public repository. The name itself is not repeated here.
        //
        // The file is found by walking up from the test's working directory,
        // because that directory is `app/` and a path relative to the
        // repository root does not exist from there. The first version of
        // this test used exactly that path, returned early when the file was
        // missing, and therefore passed with the name put back — a check
        // that cannot fail, in a commit whose whole subject is checks that
        // cannot fail.
        val marker = File("src/main/java/com/krafttools/barokraft/MainActivity.kt")
        val file = generateSequence(marker.absoluteFile) { it.parentFile }
            .map { File(it, "src/main/java/com/krafttools/barokraft/MainActivity.kt") }
            .firstOrNull { it.exists() }
        assertNotNull(
            "could not locate MainActivity.kt from ${marker.absoluteFile}",
            file,
        )
        val source = file!!.readText()
        val placeholders = Regex("placeholder = \\{ Text\\(\"([^\"]+)\"")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "the city field's placeholder was not found — this test is not looking at anything",
            placeholders.isNotEmpty(),
        )
        for (text in placeholders) {
            // The rule is shape, not vocabulary. A place hint is a single
            // word; a hint that is actually an instruction is several.
            // Checking for capitals instead rejected the sentence-case hint
            // this now uses, which is the sort of over-eager assertion that
            // gets deleted rather than fixed.
            assertTrue(
                "the search field looks like a place name: \"$text\"",
                text.trim().split(" ", "\\n").size >= 2,
            )
            assertFalse(
                "the search field names a place and region: \"$text\"",
                text.contains(','),
            )
            assertTrue("placeholder is too long to be a hint: \"$text\"", text.length <= 28)
        }
    }
}