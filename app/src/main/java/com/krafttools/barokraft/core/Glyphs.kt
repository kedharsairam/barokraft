package com.krafttools.barokraft.core

/**
 * Which mark to draw for a weather code.
 *
 * ## Why a kind and not an image
 *
 * Because a bitmap per condition would mean shipping assets that cannot be
 * tinted, cannot be recoloured for night, and cannot be described to a
 * screen reader. A shape can be all three. This maps a code to a *kind*;
 * `ui/Glyphs.kt` draws the kind.
 *
 * ## Why so few kinds
 *
 * Nine, not thirty-one. The app's job on this screen is to let someone read
 * the sky at a glance; thirty-one distinct icons would be a gallery rather
 * than a signal. The distinction between "light rain" and "rain" is carried
 * by the temperature and the probability figure beside the icon, not by
 * making the icon marginally wetter.
 */
enum class Glyph {
    /** Sun, alone or nearly. */
    SUN,

    /** Sun behind a small cloud. */
    MOSTLY_SUNNY,

    /** Sun behind a larger cloud. */
    PARTLY_CLOUDY,

    /** Cloud with nothing falling from it. */
    CLOUD,

    /** Cloud with a horizontal bar: fog. */
    FOG,

    /** Cloud with short dashes: drizzle. */
    DRIZZLE,

    /** Cloud with heavier dashes: rain. */
    RAIN,

    /** Cloud with flakes. */
    SNOW,

    /** Cloud with a bolt. */
    STORM,
    ;

    val isWet: Boolean get() = this == DRIZZLE || this == RAIN || this == STORM
}

/** The mark for a WMO code, or null when the code is unknown. */
fun glyphFor(code: Int?): Glyph? = when (code) {
    null -> null
    0 -> Glyph.SUN
    1 -> Glyph.MOSTLY_SUNNY
    2 -> Glyph.PARTLY_CLOUDY
    3 -> Glyph.CLOUD
    45, 48 -> Glyph.FOG
    51, 53, 55, 56, 57 -> Glyph.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Glyph.RAIN
    71, 73, 75, 77, 85, 86 -> Glyph.SNOW
    95, 96, 97, 99 -> Glyph.STORM
    else -> null
}

/**
 * The words a screen reader gets instead of a mark.
 *
 * ## Why this exists at all
 *
 * Because a drawn glyph announces nothing. A `Canvas` is not focusable and
 * carries no text, so an icon-only forecast row is invisible to a blind
 * reader while looking complete to everyone else. This string is what makes
 * the same row usable.
 *
 * ## Why it is not simply the WMO label
 *
 * Because the label is a *sentence* ("Thunderstorm, hail possible") and it
 * appears beside the icon anyway. Reusing it here would make the icon
 * announce its neighbour, which is noise in a list a user is scanning.
 * This is short on purpose.
 */
fun describeGlyph(code: Int?): String = when (glyphFor(code)) {
    Glyph.SUN -> "Clear sky"
    Glyph.MOSTLY_SUNNY -> "Mostly clear"
    Glyph.PARTLY_CLOUDY -> "Partly cloudy"
    Glyph.CLOUD -> "Overcast"
    Glyph.FOG -> "Fog"
    Glyph.DRIZZLE -> "Drizzle"
    Glyph.RAIN -> "Rain"
    Glyph.SNOW -> "Snow"
    Glyph.STORM -> "Thunderstorm"
    null -> "Conditions unknown"
}

/**
 * Time formatting, in the *reader's* timezone rather than the response's.
 *
 * ## Why this cannot use the response's UTC
 *
 * The request is `timezone=UTC` on purpose, so the device never guesses an
 * offset for the *data*. But a label has to say "3 PM" in the reader's
 * sense of 3 PM, and a 7-day forecast whose first row reads "Today" at
 * 05:00 IST because the day boundary was computed in UTC is worse than no
 * forecast. Parsing stays UTC; *display* is local, and the two concerns are
 * separated on purpose.
 *
 * ## Why the format comes from the platform
 *
 * `DateFormat.SHORT` renders "3:49 PM" in en-US and "15:49" in de-DE, and
 * honours a device set to 24-hour time. Hardcoding either is a small way of
 * telling a large part of the world that their phone is wrong.
 */
/**
 * Whether the reader's device shows a 12-hour clock.
 *
 * Detected from the platform's own pattern rather than assumed, because
 * assuming is how apps end up printing "14:00" on a phone set to
 * 12-hour — and it is detectable, since a 12-hour pattern carries an
 * AM/PM marker.
 */
private val uses12HourClock: Boolean by lazy {
    runCatching {
        val format = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        format is java.text.SimpleDateFormat && format.toPattern().contains('a')
    }.getOrDefault(true)
}

/** The reader's local calendar day for a timestamp. */
private fun localDay(millis: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    return cal.get(java.util.Calendar.YEAR).toLong() * 1000L +
        cal.get(java.util.Calendar.DAY_OF_YEAR).toLong()
}

/**
 * The word for a day of the week, given its date and "now".
 *
 * ## Why "Today" rather than the weekday
 *
 * Because a seven-row forecast whose first row says "Wednesday" makes the
 * reader do arithmetic. Saying "Today" removes it, and small friction of
 * that kind is most of what makes an app feel unpolished.
 */
fun dayLabel(dateMillis: Long, nowMillis: Long): String = when (localDay(dateMillis) - localDay(nowMillis)) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> weekdayName(dateMillis)
}

/** Short weekday in the reader's own locale, e.g. "Wed". */
fun weekdayName(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val names = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
}

/**
 * Hour label for the hourly strip.
 *
 * ## Why the first row is "Now" and not the hour number
 *
 * Because the first row is the one people read most, and a row labelled
 * "15" when it is 15:48 makes them check the clock. "Now" is also not
 * usually what a forecast says — the first *forecast* hour may be the
 * current hour or the next one — so it is used only when the hour really
 * is the hour being looked at.
 */
fun hourLabel(atMillis: Long, nowMillis: Long): String {
    if (localDay(atMillis) == localDay(nowMillis) &&
        localHourOf(atMillis) == localHourOf(nowMillis)
    ) {
        return "Now"
    }
    // Whole hours only, and formatted from the *hour*, not from the instant.
    //
    // An earlier version ran the platform's SHORT time format over the raw
    // timestamp and got "2:30 pm". The response is `timezone=UTC`, and a
    // UTC hour that is on the hour maps to half past in any half-hour
    // offset — so IST, and India is where this app's author lives, showed
    // ":30" in every single row of the hourly strip. Formatting the hour
    // itself cannot produce minutes that are not there.
    val h = localHourOf(atMillis)
    return if (uses12HourClock) {
        when {
            h == 0 -> "12 am"
            h < 12 -> "$h am"
            h == 12 -> "12 pm"
            else -> "${h - 12} pm"
        }
    } else {
        String.format(java.util.Locale.US, "%02d:00", h)
    }
}

/** The reader's local hour, 0..23. */
private fun localHourOf(millis: Long): Int =
    java.util.Calendar.getInstance()
        .apply { timeInMillis = millis }
        .get(java.util.Calendar.HOUR_OF_DAY)
