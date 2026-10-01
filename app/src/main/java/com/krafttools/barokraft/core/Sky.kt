package com.krafttools.barokraft.core

/**
 * The sky behind the app, decided as data rather than as pixels.
 *
 * ## Why this is pure and separate
 *
 * Because "does the sky go dark at dusk" is a question about arithmetic, not
 * about drawing, and it is the kind of question that goes wrong at exactly
 * the hours somebody is looking at the screen. Sunrise, sunset and twilight
 * all come from the forecast as real timestamps, so the twilight bands are
 * *computed* from them rather than guessed as fixed clock times — which is
 * what every weather app does and why they are all wrong at your latitude
 * in February.
 *
 * ## The colours
 *
 * Six palettes, one per condition family, each with a day and a night form.
 * The families are chosen so that a glance is informative: clear is warm
 * and open, rain is desaturated and cool, snow is near-white and low
 * contrast, and a storm is dark enough to read as one without going black,
 * because black would hide the trace drawn on top of it.
 *
 * ## Why there is no separate "twilight" family
 *
 * There is a `Twilight` band because the sky genuinely changes colour about
 * half an hour either side of sunrise, but it is applied as a *blend* of the
 * day's own palette rather than as its own palette. That keeps the number of
 * colours bounded while still letting a dawn look like a dawn.
 */
object Sky {

    /** The condition families the palette distinguishes. */
    enum class Family {
        CLEAR,
        CLOUD,
        FOG,
        DRIZZLE,
        RAIN,
        SNOW,
        STORM,
    }

    /**
     * The light at [atMillis], given the day's sunrise and sunset.
     *
     * ## Why `null` means noon, not night
     *
     * A response without sunrise or sunset — which every model omits in
     * polar latitudes, permanently — must not render the app as though it
     * were midnight in Tromsø in June. Assuming day is the failure that
     * cannot embarrass anyone: it can make a night look like a day, which is
     * a missed aesthetic, rather than make a noon look like midnight, which
     * is a wrong statement.
     */
    enum class Light { DAY, TWILIGHT, NIGHT;

        companion object {
            fun at(nowMillis: Long, sunriseMillis: Long?, sunsetMillis: Long?): Light {
                // Without both times there is no honest way to know, and a
                // wrong answer here tints every other decision on screen.
                if (sunriseMillis == null || sunsetMillis == null) return DAY
                val hour = 3_600_000L
                // Stated as four ordered bands rather than a pair of
                // "is it near either edge" tests. The earlier form ORed the
                // two proximity checks, so *any* time before sunrise also
                // satisfied "within an hour of sunset" and three in the
                // morning was classified as twilight — which is what a test
                // for "the small hours are night" caught.
                return when {
                    nowMillis < sunriseMillis - hour -> NIGHT
                    nowMillis < sunriseMillis -> TWILIGHT
                    nowMillis > sunsetMillis + hour -> NIGHT
                    nowMillis > sunsetMillis -> TWILIGHT
                    else -> DAY
                }
            }
        }
    }

    /** A gradient, as ARGB pairs from the top of the screen downward. */
    data class Palette(val top: Long, val bottom: Long)

    /**
     * The condition family for a WMO code, or null when the code is unknown.
     *
     * Null rather than a default family: an unrecognised code is a bug, and
     * a bug should be visible as an absence rather than painted over with a
     * plausible sky.
     */
    fun familyOf(code: Int?): Family? = when (code) {
        null -> null
        0, 1 -> Family.CLEAR
        2, 3 -> Family.CLOUD
        45, 48 -> Family.FOG
        51, 53, 55, 56, 57 -> Family.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Family.RAIN
        71, 73, 75, 77, 85, 86 -> Family.SNOW
        95, 96, 97, 99 -> Family.STORM
        else -> null
    }

    /** The palette for a family and a light level. */
    fun palette(family: Family, light: Light): Palette = when (family) {
            Family.CLEAR -> when (light) {
                Light.DAY -> Palette(0xFF2E6FD8, 0xFF7EC8F5)
                Light.TWILIGHT -> Palette(0xFF3A4E8C, 0xFFE08A6A)
                Light.NIGHT -> Palette(0xFF070B1C, 0xFF16224A)
            }
            Family.CLOUD -> when (light) {
                Light.DAY -> Palette(0xFF4A5C74, 0xFF9AA9BD)
                Light.TWILIGHT -> Palette(0xFF3E4258, 0xFF8A7C8E)
                Light.NIGHT -> Palette(0xFF11151F, 0xFF2A3040)
            }
            Family.FOG -> when (light) {
                Light.DAY -> Palette(0xFF7C8794, 0xFFC6CDD4)
                Light.TWILIGHT -> Palette(0xFF5B5F6B, 0xFFA79FA6)
                Light.NIGHT -> Palette(0xFF181C22, 0xFF39404A)
            }
            Family.DRIZZLE -> when (light) {
                Light.DAY -> Palette(0xFF41586F, 0xFF8FA3B5)
                Light.TWILIGHT -> Palette(0xFF3A4459, 0xFF7E7C90)
                Light.NIGHT -> Palette(0xFF0D1219, 0xFF232C38)
            }
            Family.RAIN -> when (light) {
                Light.DAY -> Palette(0xFF3D5468, 0xFF7A91A3)
                Light.TWILIGHT -> Palette(0xFF2B3446, 0xFF66687E)
                Light.NIGHT -> Palette(0xFF080D13, 0xFF1B242E)
            }
            Family.SNOW -> when (light) {
                Light.DAY -> Palette(0xFF6E8095, 0xFFDCE5EE)
                Light.TWILIGHT -> Palette(0xFF5A5F72, 0xFFC3B8C4)
                Light.NIGHT -> Palette(0xFF14181F, 0xFF3B434F)
            }
            Family.STORM -> when (light) {
                Light.DAY -> Palette(0xFF3B4759, 0xFF6B7A8E)
                Light.TWILIGHT -> Palette(0xFF1E2230, 0xFF4A4256)
                Light.NIGHT -> Palette(0xFF04060A, 0xFF141A24)
        }
    }

    /**
     * The palette for a code at a moment, or a neutral one when the code is
     * unknown.
     *
     * The fallback is `CLOUD` in daylight rather than `CLEAR`: an app with
     * no idea what the weather is should look unremarkable, not
     * optimistic.
     */
    fun paletteFor(code: Int?, nowMillis: Long, sunriseMillis: Long?, sunsetMillis: Long?): Palette {
        val light = Light.at(nowMillis, sunriseMillis, sunsetMillis)
        return palette(familyOf(code) ?: Family.CLOUD, light)
    }

    /** Blend two colours, `t` in 0..1 where 0 is [from]. */
    fun blend(from: Long, to: Long, t: Float): Long {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = ((from shr shift) and 0xFF).toInt()
            val b = ((to shr shift) and 0xFF).toInt()
            return (a + (b - a) * f).toInt().coerceIn(0, 255)
        }
        return (0xFFL shl 24) or
            (channel(16).toLong() shl 16) or
            (channel(8).toLong() shl 8) or
            channel(0).toLong()
    }

    /**
     * Apply twilight as a blend toward the day's own palette.
     *
     * Bounded to 0.55 at the deepest twilight so a storm at dawn stays a
     * storm. A sky that goes orange because the sun went down is a
     * decorative choice that has overtaken the information it was
     * decorating.
     */
    fun withTwilight(palette: Palette, nowMillis: Long, sunriseMillis: Long?, sunsetMillis: Long?): Palette {
        if (Light.at(nowMillis, sunriseMillis, sunsetMillis) != Light.TWILIGHT) return palette
        val warm = Palette(0xFFB4603C, 0xFFE8A06B)
        val strength = 0.42f
        return Palette(
            blend(palette.top, warm.top, strength),
            blend(palette.bottom, warm.bottom, strength),
        )
    }
}