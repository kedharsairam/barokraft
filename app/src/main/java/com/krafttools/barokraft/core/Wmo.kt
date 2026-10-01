package com.krafttools.barokraft.core

/**
 * WMO weather interpretation codes, and the words this app uses for them.
 *
 * ## Why this file is a lookup and not a decision
 *
 * Open-Meteo returns a numeric `weather_code` and nothing else. The
 * mapping to language is the app's job, and it is a place where a weather
 * app can quietly overstate itself: "Thunderstorm with heavy hail" is
 * code 99, and printing that as a confident label is a claim the app
 * cannot support, because the code came from a 9–25 km grid cell and the
 * app is standing in a street.
 *
 * So the wording here is deliberately *mild*. A code is reported as what
 * the model says the region is doing, not as what is happening to you, and
 * the severe codes are phrased as warnings rather than as facts.
 */
object Wmo {

    /**
     * A short label for a WMO code, or `null` for a code this app does
     * not know.
     *
     * Returning null rather than "Unknown" matters: an unrecognised code
     * is a bug in the app, and a bug should be visible on screen rather
     * than papered over with a plausible word. A test asserts that every
     * code in the published set maps to something.
     */
    fun label(code: Int): String? = when (code) {
        0 -> "Clear"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Freezing fog"
        51 -> "Light drizzle"
        53 -> "Drizzle"
        55 -> "Heavy drizzle"
        56 -> "Freezing drizzle"
        57 -> "Heavy freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66 -> "Freezing rain"
        67 -> "Heavy freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Showers"
        82 -> "Violent showers"
        85 -> "Light snow showers"
        86 -> "Snow showers"
        95 -> "Thunderstorm"
        96 -> "Thunderstorm, hail possible"
        97 -> "Thunderstorm, hail likely"
        99 -> "Thunderstorm with hail"
        else -> null
    }

    /**
     * A longer sentence for a code, for the detail row.
     *
     * The severe entries say "forecast" rather than "expected" and name
     * the grid cell, because that is the honest relationship between a
     * point forecast and a street.
     */
    fun describe(code: Int): String? = when (code) {
        0 -> "Sky clear."
        1 -> "Mostly clear."
        2 -> "Partly cloudy."
        3 -> "Overcast."
        45 -> "Fog, reducing visibility."
        48 -> "Freezing fog. Icy surfaces likely."
        51 -> "Light drizzle."
        53 -> "Drizzle."
        55 -> "Heavy drizzle."
        56 -> "Light freezing drizzle. Icy surfaces likely."
        57 -> "Heavy freezing drizzle. Icy surfaces likely."
        61 -> "Light rain."
        63 -> "Rain."
        65 -> "Heavy rain."
        66 -> "Freezing rain. Icy surfaces likely."
        67 -> "Heavy freezing rain. Icy surfaces likely."
        71 -> "Light snow."
        73 -> "Snow."
        75 -> "Heavy snow."
        77 -> "Snow grains."
        80 -> "Light showers."
        81 -> "Showers."
        82 -> "Violent showers."
        85 -> "Light snow showers."
        86 -> "Snow showers."
        95 -> "Thunderstorms forecast for the area."
        96 -> "Thunderstorms forecast, hail possible."
        97 -> "Thunderstorms forecast, hail likely. Take cover if you are out."
        99 -> "Thunderstorms with hail forecast. Take cover if you are out."
        else -> null
    }

    /** Every code this app recognises, for the test that checks coverage. */
    val KNOWN_CODES = listOf(
        0, 1, 2, 3, 45, 48,
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        71, 73, 75, 77,
        80, 81, 82, 85, 86,
        95, 96, 97, 99,
    )

    /** The codes worth interrupting someone about. */
    fun isSevere(code: Int): Boolean = code >= 95 || code in listOf(65, 67, 75, 82)
}
