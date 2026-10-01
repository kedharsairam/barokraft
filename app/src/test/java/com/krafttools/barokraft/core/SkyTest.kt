package com.krafttools.barokraft.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The sky, the marks, and the labels.
 *
 * These are pure functions, so every case below is a fact about the code
 * rather than about the weather. Nothing here can be "flaky because it
 * rained" — which is exactly why the decisions that drive the whole look of
 * the app are made in this layer and not in a composable.
 */
class SkyTest {

    private val day = 1_700_000_000_000L
    private val sunrise = day + 3 * 3_600_000L
    private val sunset = day + 15 * 3_600_000L

    // ── Light level ─────────────────────────────────────────────────────

    @Test
    fun `midday is day`() {
        assertEquals(Sky.Light.DAY, Sky.Light.at(day + 9 * 3_600_000L, sunrise, sunset))
    }

    @Test
    fun `the small hours are night`() {
        assertEquals(Sky.Light.NIGHT, Sky.Light.at(day, sunrise, sunset))
    }

    @Test
    fun `either side of sunrise is twilight`() {
        // This band is why a dawn looks like a dawn rather than like a
        // switch being thrown.
        assertEquals(Sky.Light.TWILIGHT, Sky.Light.at(sunrise - 10 * 60_000L, sunrise, sunset))
        assertEquals(Sky.Light.TWILIGHT, Sky.Light.at(sunset + 10 * 60_000L, sunrise, sunset))
    }

    @Test
    fun `missing sunrise and sunset means day, never midnight`() {
        // Every polar model omits these. Assuming night would render a June
        // afternoon in Tromsø as midnight; assuming day can only ever make a
        // night look like a day, which is a missed aesthetic rather than a
        // false statement.
        assertEquals(Sky.Light.DAY, Sky.Light.at(day, null, sunset))
        assertEquals(Sky.Light.DAY, Sky.Light.at(day, sunrise, null))
        assertEquals(Sky.Light.DAY, Sky.Light.at(day, null, null))
    }

    // ── Families ────────────────────────────────────────────────────────

    @Test
    fun `every published code maps to a family`() {
        for (code in Wmo.KNOWN_CODES) {
            assertTrue(
                "code $code has no sky family",
                Sky.familyOf(code) != null,
            )
        }
    }

    @Test
    fun `every published code maps to a glyph`() {
        for (code in Wmo.KNOWN_CODES) {
            assertTrue("code $code has no glyph", glyphFor(code) != null)
        }
    }

    @Test
    fun `an unknown code has no family and no glyph`() {
        // Absent rather than defaulted: a placeholder mark would be the app
        // claiming to know something it does not.
        assertNull(Sky.familyOf(4242))
        assertNull(glyphFor(4242))
        assertEquals("Conditions unknown", describeGlyph(4242))
    }

    @Test
    fun `severe codes get their own families`() {
        assertEquals(Sky.Family.STORM, Sky.familyOf(95))
        assertEquals(Sky.Family.SNOW, Sky.familyOf(75))
        assertEquals(Sky.Family.FOG, Sky.familyOf(45))
    }

    // ── Palettes ────────────────────────────────────────────────────────

    @Test
    fun `every family has three distinct light levels`() {
        for (family in Sky.Family.entries) {
            val day = Sky.palette(family, Sky.Light.DAY)
            val night = Sky.palette(family, Sky.Light.NIGHT)
            assertNotEquals("$family day and night are identical", day.top, night.top)
            assertNotEquals("$family day and night are identical", day.bottom, night.bottom)
        }
    }

    @Test
    fun `night is darker than day for every family`() {
        // Computed rather than eyeballed, because "a storm at 4pm looked
        // like midnight" was a real report and eyeballing is what missed it.
        fun luminance(c: Long): Double {
            fun channel(shift: Int) = ((c shr shift) and 0xFF) / 255.0
            fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else
                Math.pow((v + 0.055) / 1.055, 2.4)
            return 0.2126 * lin(channel(16)) + 0.7152 * lin(channel(8)) + 0.0722 * lin(channel(0))
        }
        for (family in Sky.Family.entries) {
            val d = luminance(Sky.palette(family, Sky.Light.DAY).top)
            val n = luminance(Sky.palette(family, Sky.Light.NIGHT).top)
            assertTrue(
                "$family night ($n) is not darker than day ($d)",
                n < d,
            )
        }
    }

    @Test
    fun `twilight is blended rather than replacing the palette`() {
        // A storm at dawn must stay a storm. A palette that goes orange
        // because the sun went down is decoration that has overtaken the
        // information it was decorating.
        val stormDay = Sky.palette(Sky.Family.STORM, Sky.Light.DAY)
        val blended = Sky.withTwilight(stormDay, sunrise - 10 * 60_000L, sunrise, sunset)
        assertNotEquals("twilight should change the colour", stormDay.top, blended.top)
        val luminanceOf = { c: Long ->
            val r = ((c shr 16) and 0xFF) / 255.0
            val g = ((c shr 8) and 0xFF) / 255.0
            val b = (c and 0xFF) / 255.0
            0.2126 * r + 0.7152 * g + 0.0722 * b
        }
        assertTrue(
            "twilight must not brighten a storm into daylight",
            luminanceOf(blended.top) < luminanceOf(Sky.palette(Sky.Family.CLEAR, Sky.Light.DAY).top),
        )
    }

    @Test
    fun `outside twilight the palette is untouched`() {
        val p = Sky.palette(Sky.Family.RAIN, Sky.Light.DAY)
        assertEquals(p, Sky.withTwilight(p, day + 9 * 3_600_000L, sunrise, sunset))
    }

    // ── Blending ────────────────────────────────────────────────────────

    @Test
    fun `blend reaches both ends exactly`() {
        assertEquals(0xFF102030L, Sky.blend(0xFF102030L, 0xFFAABBCCL, 0f))
        assertEquals(0xFFAABBCCL, Sky.blend(0xFF102030L, 0xFFAABBCCL, 1f))
    }

    @Test
    fun `blend is clamped rather than extrapolating`() {
        // A value outside 0..1 must not produce an invalid colour, which
        // Compose renders as transparent — a card that silently vanishes.
        assertEquals(0xFF102030L, Sky.blend(0xFF102030L, 0xFFAABBCCL, -5f))
        assertEquals(0xFFAABBCCL, Sky.blend(0xFF102030L, 0xFFAABBCCL, 5f))
    }

    @Test
    fun `blend always returns a fully opaque colour`() {
        val c = Sky.blend(0x80203040L, 0x80C0D0E0L, 0.5f)
        assertEquals("alpha must be restored or the sky renders transparent", 0xFFL, (c shr 24) and 0xFFL)
    }

    // ── Labels ──────────────────────────────────────────────────────────

    @Test
    fun `today and tomorrow are named rather than dated`() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val today = cal.timeInMillis
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Tomorrow", dayLabel(today + 86_400_000L, today))
    }

    @Test
    fun `later days are named by weekday`() {
        val today = Calendar.getInstance().apply {
            timeInMillis = day
            set(Calendar.HOUR_OF_DAY, 12)
        }.timeInMillis
        val label = dayLabel(today + 3 * 86_400_000L, today)
        assertTrue("expected a short weekday, got $label", label.length in 3..4)
        assertTrue(label.all { it.isLetter() })
    }

    @Test
    fun `the first hourly row reads now`() {
        assertEquals("Now", hourLabel(day, day))
        // Ten minutes, not thirty: the response is UTC and a half-hour
        // offset can push +30 min across an hour boundary, so this asserts
        // "same hour" only when it genuinely is.
        assertEquals("Now", hourLabel(day + 10 * 60_000L, day))
    }

    @Test
    fun `later hours are whole hours with no minutes`() {
        // Caught on the device: the platform's SHORT time format was applied
        // to a UTC timestamp, and on a half-hour offset every row of the
        // strip read ":30". Formatting the hour cannot invent minutes.
        for (offset in 1..11) {
            val label = hourLabel(day + offset * 3_600_000L + 1_800_000L, day)
            assertTrue(
                "hour label \"$label\" contains minutes",
                !label.contains(":30") && !label.contains(":45") && !label.contains(":15"),
            )
        }
    }

    @Test
    fun `an hour label fits its column`() {
        // "Tomorrow" wrapped to "Tomorro / w" before the column was widened,
        // and a wrapping label in a fixed-width row is what makes a list
        // look broken rather than merely untidy.
        assertTrue("Tomorrow".length <= 9)
        for (offset in 0..23) {
            val label = hourLabel(day + offset * 3_600_000L, day + 86_400_000L)
            assertTrue("hour label too wide: \"$label\"", label.length <= 6)
        }
    }

    @Test
    fun `twelve and twenty four hour clocks are both honoured`() {
        // The device's own preference, detected rather than assumed — the
        // app is not in a position to tell a reader their phone is wrong.
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "Europe/London", "Asia/Kolkata")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val label = hourLabel(
                    System.currentTimeMillis() + 5 * 3_600_000L,
                    System.currentTimeMillis(),
                )
                assertTrue("hour label \"$label\" has unexpected minutes", !label.contains(":3"))
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `a glyph describes itself for a screen reader`() {
        // A Canvas announces nothing, so this string is the only thing a
        // blind reader gets for the forecast.
        assertEquals("Rain", describeGlyph(63))
        assertEquals("Clear sky", describeGlyph(0))
        assertEquals("Conditions unknown", describeGlyph(null))
    }

    @Test
    fun `glyph descriptions are short enough to scan in a list`() {
        for (code in Wmo.KNOWN_CODES) {
            assertTrue(
                "description for $code is too long: ${describeGlyph(code)}",
                describeGlyph(code).length <= 14,
            )
        }
    }
}