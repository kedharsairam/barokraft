package com.krafttools.barokraft.net

/**
 * The Open-Meteo response, parsed.
 *
 * ## Why this is hand-parsed
 *
 * Because the alternative is a JSON library and a data class per field,
 * and a weather response is a flat object of parallel arrays. The parsing
 * that matters is not structural — it is the part where the API's
 * behaviour is surprising and a naive reader gets it wrong:
 *
 *  - **`null` is normal, not exceptional.** Every field can come back
 *    `null` when a model does not provide it. `precipitation_probability`
 *    is null for most models that lack an ensemble. A parser that
 *    assumes non-null either crashes or, worse, substitutes a zero that
 *    reads as "no rain" when the truth is "nobody knows".
 *  - **The arrays are parallel, not objects.** An hourly forecast is
 *    `time: [...]` and `temperature_2m: [...]` side by side, and pairing
 *    them by index is the whole job. Get the pairing wrong and you show
 *    tomorrow's temperature against today's time, which is the kind of
 *    error that survives a code review because it looks correct.
 *  - **`generationtime_ms` is not latency.** It is how long the server
 *    spent building the response. It says nothing about how fresh the
 *    forecast is, and the model run time is what matters.
 *
 * Everything here is a pure function of the response text, so every one of
 * those cases is a unit test on the JVM with no device and no network.
 */
object Protocol {

    /** One hour of the forecast, already paired with its time. */
    data class Hour(
        val atMillis: Long,
        val temperatureC: Float?,
        val apparentTemperatureC: Float?,
        val precipitationMm: Float?,
        val precipitationProbability: Int?,
        val weatherCode: Int?,
        val windSpeedKmh: Float?,
        val cloudCoverPercent: Int?,
        val visibilityMetres: Float?,
        /** Mean sea-level pressure, which is what audits a local QNH. */
        val seaLevelPressureHpa: Float?,
        val surfacePressureHpa: Float?,
    )

    /** A parsed response. */
    data class Forecast(
        val latitude: Double,
        val longitude: Double,
        val elevationMetres: Double,
        val utcOffsetSeconds: Int,
        val hours: List<Hour>,
        /** The model run this came from, if the API told us. */
        val modelName: String?,
    ) {
        val isEmpty: Boolean get() = hours.isEmpty()

        /** The first hour at or after [nowMillis], or the first available. */
        fun hourAt(nowMillis: Long): Hour? =
            hours.firstOrNull { it.atMillis >= nowMillis } ?: hours.firstOrNull()
    }

    /** Raised when a response cannot be understood. Carries no partial data. */
    class MalformedResponse(message: String) : Exception(message)

    // ── URL construction ────────────────────────────────────────────────

    /**
     * Build the forecast request.
     *
     * Only the variables the app actually renders are requested. The API
     * bills fractional calls by variable count and time span, so asking
     * for everything and using four is both slower and, past the free
     * tier's daily ceiling, a real cost.
     *
     * `timeformat=unixtime` is requested deliberately: ISO-8601 strings
     * would need a timezone-aware parser on the device, and a phone that
     * guesses its own offset wrong will show a forecast for the wrong
     * hour, which is worse than no forecast.
     */
    fun buildUrl(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 7,
        pastHours: Int = 0,
    ): String {
        val variables = listOf(
            "temperature_2m",
            "apparent_temperature",
            "precipitation",
            "precipitation_probability",
            "weather_code",
            "wind_speed_10m",
            "cloud_cover",
            "visibility",
            "pressure_msl",
            "surface_pressure",
        ).joinToString(",")

        return buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(trimCoord(latitude))
            append("&longitude=").append(trimCoord(longitude))
            append("&hourly=").append(variables)
            append("&timezone=UTC")
            append("&timeformat=unixtime")
            append("&forecast_days=").append(forecastDays.coerceIn(1, 16))
            if (pastHours > 0) {
                append("&past_hours=").append(pastHours.coerceAtMost(24))
            }
        }
    }

    /**
     * Trim a coordinate to four decimals, about 11 m.
     *
     * A full double serialises to seventeen significant digits, which is
     * silly for a location and makes the URL unpleasant to read in a log.
     * More importantly it is *deterministic*: two requests for the same
     * place produce byte-identical URLs, which is what lets the response
     * be cached against the request that produced it.
     */
    internal fun trimCoord(v: Double): String {
        // Locale.US is not optional. A default-locale format renders 9.2263
        // as "9,2263" in much of the world, which silently corrupts every
        // coordinate on a phone set to a comma-decimal language and
        // produces a forecast for the wrong hemisphere.
        val s = String.format(java.util.Locale.US, "%.4f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    /**
     * Build the geocoding search request for a city name.
     *
     * The query is slugged rather than URL-encoded: Open-Meteo's geocoding
     * endpoint takes a hyphenated name, and percent-encoding a space as
     * `%20` returns nothing at all. So "New Delhi" has to become
     * "new-delhi" or the search silently returns empty and the user is
     * told no city matched, which is a lie about the app rather than
     * about the world.
     *
     * Non-ASCII letters are kept: "München" and "Kolkata" both need to
     * survive, and the endpoint handles them. Transliteration is not
     * attempted and would be wrong more often than it helped.
     */
    fun buildGeocodingUrl(query: String): String {
        val encoded = buildString {
            var lastWasDash = false
            for (c in query.trim().lowercase()) {
                if (c.isLetterOrDigit()) {
                    append(c)
                    lastWasDash = false
                } else if (!lastWasDash) {
                    append('-')
                    lastWasDash = true
                }
            }
        }.trim('-')
        return "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=10&language=en&format=json"
    }
}
