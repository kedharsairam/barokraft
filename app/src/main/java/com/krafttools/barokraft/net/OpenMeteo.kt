package com.krafttools.barokraft.net

/**
 * Turning a response body into a [Protocol.Forecast].
 *
 * A separate file from the JSON reader so the two concerns can be tested
 * independently: one suite proves the parser handles syntax, the other
 * proves the pairing and the null handling are right. A bug in either
 * shows up in only one.
 */
object OpenMeteo {

    /**
     * Parse a forecast response.
     *
     * @throws Protocol.MalformedResponse if the body is not a forecast, or
     *   has no usable hours. An error the app can show beats a forecast
     *   that silently lost days.
     */
    fun parseForecast(body: String): Protocol.Forecast {
        val root = try {
            parseJson(body)
        } catch (e: JsonSyntaxError) {
            throw Protocol.MalformedResponse("response was not valid JSON: ${e.message}")
        }

        // The API signals its own errors with HTTP 200, an `error` field
        // set to **true**, and a human-readable `reason`. Checking
        // `error` for a string — as a first draft here did — never fires,
        // because it is a boolean, and the response then looks like a
        // forecast with no hours in it, which surfaces as a blank screen
        // rather than as the parameter error that actually happened.
        if ((obj0(root)["error"] as? JsonValue.Bool)?.value == true) {
            val reason = obj0(root)["reason"].asStringOrNull() ?: "no reason given"
            throw Protocol.MalformedResponse("API returned an error: $reason")
        }

        val obj = root.asObject()
        val hourly = obj["hourly"].asObject()

        val times = hourly["time"].asArray()
        if (times.isEmpty()) {
            throw Protocol.MalformedResponse("the response carried no hourly times")
        }

        // Read the timestamp through the raw text. A Float is exact for
        // integers only to 2^24 and epoch seconds are ~1.76e9, so the
        // float path is 32 seconds early on every single hour — which
        // reads as a slightly-off clock rather than as a bug, and is
        // therefore the kind of error that ships.
        //
        // `times.size` is the authority on how many hours exist. Every
        // other array is indexed against it, so a shorter variable array
        // yields nulls rather than another hour's values, and a longer one
        // is truncated rather than inventing hours.
        val hourCount = times.size

        // Parallel arrays, pulled once and indexed by position. Reading
        // each field inside the loop instead would be a map lookup per
        // field per hour, and — more importantly — it invites pairing
        // the wrong two arrays, which is how a forecast ends up showing
        // Tuesday's rain under Monday's heading.
        val temperatures = hourly["temperature_2m"].asArray()
        val apparent = hourly["apparent_temperature"].asArray()
        val precipitation = hourly["precipitation"].asArray()
        val probability = hourly["precipitation_probability"].asArray()
        val codes = hourly["weather_code"].asArray()
        val wind = hourly["wind_speed_10m"].asArray()
        val cloud = hourly["cloud_cover"].asArray()
        val visibility = hourly["visibility"].asArray()
        val msl = hourly["pressure_msl"].asArray()
        val surface = hourly["surface_pressure"].asArray()

        val hours = (0 until hourCount).mapNotNull { i ->
            val seconds = times[i].asLongOrNull()
                ?: return@mapNotNull null
            Protocol.Hour(
                atMillis = seconds * 1000L,
                temperatureC = at(temperatures, i),
                apparentTemperatureC = at(apparent, i),
                precipitationMm = at(precipitation, i),
                precipitationProbability = at(probability, i)?.toInt(),
                weatherCode = at(codes, i)?.toInt(),
                windSpeedKmh = at(wind, i),
                cloudCoverPercent = at(cloud, i)?.toInt(),
                visibilityMetres = at(visibility, i),
                seaLevelPressureHpa = at(msl, i),
                surfacePressureHpa = at(surface, i),
            )
        }

        if (hours.isEmpty()) {
            throw Protocol.MalformedResponse("no parseable hours in the response")
        }

        return Protocol.Forecast(
            latitude = obj["latitude"].asFloatOrNull()?.toDouble() ?: 0.0,
            longitude = obj["longitude"].asFloatOrNull()?.toDouble() ?: 0.0,
            elevationMetres = obj["elevation"].asFloatOrNull()?.toDouble() ?: 0.0,
            utcOffsetSeconds = obj["utc_offset_seconds"].asFloatOrNull()?.toInt() ?: 0,
            hours = hours,
            modelName = modelNameFrom(body),
        )
    }

    /**
     * The value at [index], or null when the array is shorter.
     *
     * The API does return arrays of unequal length when a model lacks a
     * variable, and the alternative — `getOrNull` with a zero default —
     * turns "nobody knows" into "no rain", which is a false statement
     * about the weather rather than a formatting bug.
     */
    private fun at(array: List<JsonValue>, index: Int): Float? =
        if (index < array.size) array[index].asFloatOrNull() else null

    /** The document's root object, for the error check before anything else. */
    private fun obj0(value: JsonValue): JsonValue.Obj = value.asObject()

    /**
     * Dig the model name out of the response if it is there.
     *
     * Open-Meteo does not put the chosen model in a single obvious field
     * on every response, so this looks for the common spellings and
     * returns null rather than guessing. Returning null is correct: the
     * app shows the model when it knows and says nothing when it does not,
     * and a wrong name is worse than no name.
     */
    internal fun modelNameFrom(body: String): String? {
        val root = runCatching { parseJson(body) }.getOrNull() ?: return null
        val obj = root.asObject()
        for (key in listOf("model", "model_name", "modelName")) {
            obj[key].asStringOrNull()?.let { return it }
        }
        return null
    }

    /** A city from the geocoding API. */
    data class Place(
        val name: String,
        val country: String?,
        val admin1: String?,
        val latitude: Double,
        val longitude: Double,
        val elevationMetres: Double?,
    ) {
        /** "Palakollu, Andhra Pradesh" — disambiguated, not over-qualified. */
        val displayName: String
            get() = listOfNotNull(
                name,
                admin1?.takeIf { it != name },
                country?.takeIf { it != admin1 },
            ).joinToString(", ")
    }

    /** Parse a geocoding response. An empty result is a valid empty list. */
    fun parsePlaces(body: String): List<Place> {
        val root = runCatching { parseJson(body) }.getOrNull()
            ?: throw Protocol.MalformedResponse("geocoding response was not valid JSON")
        // Same shape as the forecast endpoint: `error` is a boolean and
        // `reason` carries the text.
        if ((root.asObject()["error"] as? JsonValue.Bool)?.value == true) {
            val reason = root.asObject()["reason"].asStringOrNull() ?: "no reason given"
            throw Protocol.MalformedResponse("geocoding error: $reason")
        }
        return root.asObject()["results"].asArray().mapNotNull { item ->
            val o = item.asObject()
            val lat = o["latitude"].asFloatOrNull()?.toDouble() ?: return@mapNotNull null
            val lon = o["longitude"].asFloatOrNull()?.toDouble() ?: return@mapNotNull null
            Place(
                name = o["name"].asStringOrNull() ?: return@mapNotNull null,
                country = o["country"].asStringOrNull(),
                admin1 = o["admin1"].asStringOrNull(),
                latitude = lat,
                longitude = lon,
                elevationMetres = o["elevation"].asFloatOrNull()?.toDouble(),
            )
        }
    }
}
