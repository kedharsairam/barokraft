package com.krafttools.barokraft.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request construction and response parsing.
 *
 * The response used here is a trimmed but structurally real Open-Meteo
 * payload, including the two things that break naive parsers: a `null`
 * precipitation probability, and a field that is shorter than the time
 * array.
 */
class ProtocolTest {

    // ── URL construction ────────────────────────────────────────────────

    @Test
    fun `the url asks only for what the app renders`() {
        val url = Protocol.buildUrl(-0.1807, -78.4678)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast"))
        for (variable in listOf(
            "temperature_2m", "apparent_temperature", "precipitation",
            "precipitation_probability", "weather_code", "wind_speed_10m",
            "cloud_cover", "visibility", "pressure_msl", "surface_pressure",
        )) {
            assertTrue("missing $variable", url.contains(variable))
        }
    }

    @Test
    fun `the url requests utc so the device never guesses its own offset`() {
        // A phone that guesses its timezone wrong shows tomorrow's weather
        // under today's date, which is worse than no forecast.
        val url = Protocol.buildUrl(-0.1807, -78.4678)
        assertTrue(url.contains("timezone=UTC"))
        assertTrue(url.contains("timeformat=unixtime"))
    }

    @Test
    fun `the same place produces a byte identical url`() {
        // Determinism is what lets a response be cached against the
        // request that produced it.
        assertEquals(Protocol.buildUrl(-0.1807, -78.4678), Protocol.buildUrl(-0.1807, -78.4678))
    }

    @Test
    fun `forecast days are clamped to what the api allows`() {
        assertTrue(Protocol.buildUrl(0.0, 0.0, forecastDays = 99).contains("forecast_days=16"))
        assertTrue(Protocol.buildUrl(0.0, 0.0, forecastDays = 0).contains("forecast_days=1"))
    }

    @Test
    fun `coordinates are trimmed without losing precision that matters`() {
        // Four decimals is about 11 m, which is far finer than a weather
        // grid cell and keeps the url readable in a log.
        val url = Protocol.buildUrl(-0.18071487, -78.46782264)
        assertTrue(url.contains("latitude=-0.1807"))
        assertTrue(url.contains("longitude=-78.4678"))
    }

    @Test
    fun `past hours are omitted unless asked for`() {
        assertTrue(!Protocol.buildUrl(0.0, 0.0).contains("past_hours"))
        assertTrue(Protocol.buildUrl(0.0, 0.0, pastHours = 3).contains("past_hours=3"))
        assertTrue(Protocol.buildUrl(0.0, 0.0, pastHours = 99).contains("past_hours=24"))
    }

    @Test
    fun `geocoding slugs a query into a url`() {
        val url = Protocol.buildGeocodingUrl("Quito")
        assertTrue(url.contains("name=quito"))
        assertTrue(url.contains("count=10"))
    }

    @Test
    fun `geocoding collapses punctuation rather than dropping characters`() {
        val url = Protocol.buildGeocodingUrl("New  Delhi!!")
        assertTrue("expected new-delhi, got $url", url.contains("name=new-delhi"))
    }

    // ── Forecast parsing ────────────────────────────────────────────────

    private val body = """
    {
      "latitude": -0.1807, "longitude": -78.4678, "elevation": 12.0,
      "utc_offset_seconds": 0, "timezone": "GMT",
      "hourly": {
        "time": [1759257600, 1759261200, 1759264800],
        "temperature_2m": [31.2, 30.1, 28.4],
        "apparent_temperature": [36.0, 34.8, 32.1],
        "precipitation": [0.0, 0.0, 2.4],
        "precipitation_probability": [null, null, 80],
        "weather_code": [1, 2, 63],
        "wind_speed_10m": [9.2, 11.4, 14.1],
        "cloud_cover": [12, 44, 96],
        "visibility": [24140.0, 24140.0, 12000.0],
        "pressure_msl": [1006.4, 1006.1, 1005.2],
        "surface_pressure": [1005.3, 1005.0, 1004.1]
      }
    }
    """.trimIndent()

    @Test
    fun `parses a real response`() {
        val f = OpenMeteo.parseForecast(body)
        assertEquals(3, f.hours.size)
        assertEquals(12.0, f.elevationMetres, 0.01)
        // The app requests timezone=UTC, so the API returns a zero offset
        // and the device does no offset arithmetic of its own. An
        // assertion of 3 here would have been a test of my assumptions
        // rather than of the code.
        assertEquals(0, f.utcOffsetSeconds)
    }

    @Test
    fun `pairs each value with its own timestamp`() {
        // The bug this test exists for: reading field i against time i is
        // the whole job, and getting it wrong shows Tuesday's rain under
        // Monday's heading with no visible symptom.
        val f = OpenMeteo.parseForecast(body)
        val third = f.hours[2]
        assertEquals(2.4f, third.precipitationMm!!, 0.001f)
        assertEquals(63, third.weatherCode)
        assertEquals(1_759_264_800_000L, third.atMillis)
    }

    @Test
    fun `a null probability stays null`() {
        val f = OpenMeteo.parseForecast(body)
        assertNull("must not become 0", f.hours[0].precipitationProbability)
        assertEquals(80, f.hours[2].precipitationProbability)
    }

    @Test
    fun `keeps the sea level pressure the drift audit needs`() {
        val f = OpenMeteo.parseForecast(body)
        assertEquals(1006.4f, f.hours[0].seaLevelPressureHpa!!, 0.001f)
        assertEquals(1005.3f, f.hours[0].surfacePressureHpa!!, 0.001f)
    }

    @Test
    fun `a short field array leaves the rest null rather than shifting`() {
        // Parallel arrays of unequal length are a real API behaviour. The
        // wrong handling indexes past the end and borrows another hour's
        // value, which is silent.
        // Two cases, because the API does both: a variable array SHORTER
        // than the times, and one LONGER. The first must leave nulls; the
        // second must not invent hours.
        val ragged = """
        {"hourly":{
          "time":[100,200,300],
          "temperature_2m":[10.0,11.0],
          "weather_code":[0,1,2,3]
        }}
        """.trimIndent()
        val f = OpenMeteo.parseForecast(ragged)
        assertEquals("hours come from the time array, not the longest variable", 3, f.hours.size)
        assertEquals(11.0f, f.hours[1].temperatureC!!, 0.001f)
        assertNull("a missing third temperature must be null", f.hours[2].temperatureC)
        assertEquals("a longer array is still indexed positionally", 2, f.hours[2].weatherCode)
    }

    @Test
    fun `an api error in a 200 is still an error`() {
        // The API signals failure with HTTP 200 and an `error` field,
        // which is exactly the shape that becomes a blank screen if
        // nobody checks.
        val err = """{"error":true,"reason":"Parameter 'latitude' is missing"}"""
        try {
            OpenMeteo.parseForecast(err)
            throw AssertionError("should have rejected an API error")
        } catch (e: Protocol.MalformedResponse) {
            assertTrue(e.message!!.contains("latitude"))
        }
    }

    @Test
    fun `a response with no times is rejected rather than shown empty`() {
        try {
            OpenMeteo.parseForecast("""{"hourly":{}}""")
            throw AssertionError("should have rejected a response with no times")
        } catch (e: Protocol.MalformedResponse) {
            assertTrue(e.message!!.contains("no hourly times"))
        }
    }

    @Test
    fun `invalid json is reported as malformed not as a crash`() {
        // A captive portal or a proxy answers with HTML. The parser
        // rejects it — '<' is not a value, so it falls through to the
        // number branch and finds no digits — but the message names the
        // nearer failure rather than saying "not valid JSON". The
        // assertion is that it is *rejected*, not which words it uses.
        try {
            OpenMeteo.parseForecast("<html>502 Bad Gateway</html>")
            throw AssertionError("should have rejected HTML")
        } catch (e: Protocol.MalformedResponse) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `hourAt finds the current hour and falls back to the first`() {
        val f = OpenMeteo.parseForecast(body)
        val first = f.hours[0].atMillis
        assertEquals(first, f.hourAt(first)!!.atMillis)
        assertEquals(first, f.hourAt(first - 60_000)!!.atMillis)
    }

    // ── Geocoding ───────────────────────────────────────────────────────

    @Test
    fun `parses places`() {
        val json = """
        {"results":[
          {"name":"Quito","latitude":-0.1807,"longitude":-78.4678,
           "country":"India","admin1":"Pichincha","elevation":14.0},
          {"name":"Bergen","latitude":10.7867,"longitude":76.6548,
           "country":"India","admin1":"Vestland"}
        ]}
        """.trimIndent()
        val places = OpenMeteo.parsePlaces(json)
        assertEquals(2, places.size)
        assertEquals("Quito, Pichincha, India", places[0].displayName)
        assertEquals(14.0, places[0].elevationMetres!!, 0.01)
    }

    @Test
    fun `a result without coordinates is dropped`() {
        val json = """{"results":[{"name":"Nowhere"},{"name":"Somewhere","latitude":1,"longitude":2}]}"""
        val places = OpenMeteo.parsePlaces(json)
        assertEquals(1, places.size)
        assertEquals("Somewhere", places[0].name)
    }

    @Test
    fun `no results is an empty list, not an error`() {
        // Typing a city that does not exist is normal use, not an error.
        assertEquals(0, OpenMeteo.parsePlaces("""{"results":[]}""").size)
    }

    @Test
    fun `a display name is not over-qualified`() {
        val json = """{"results":[{"name":"Singapore","latitude":1.35,"longitude":103.82,
            "country":"Singapore","admin1":"Singapore"}]}"""
        val place = OpenMeteo.parsePlaces(json).single()
        assertEquals("Singapore", place.displayName)
    }

    @Test
    fun `the user agent carries the version the build declares`() {
        // v0.3.2 was published carrying the 0.3.1 binary, because the
        // header held a hand-written version string and the APK on disk was
        // from the previous build. Deriving the header from BuildConfig
        // removes the possibility; this test pins that it is still derived.
        val ua = userAgent
        val version = com.krafttools.barokraft.BuildConfig.VERSION_NAME
        assertTrue(
            "user agent \"$ua\" does not carry the build version \"$version\"",
            ua.contains(version),
        )
        assertTrue("expected a three-part version, got $version", Regex("\\d+\\.\\d+\\.\\d+").matches(version))
    }

    // ── Failure classification ──────────────────────────────────────────

    @Test
    fun `http status maps to a typed failure`() {
        assertTrue(classifyStatus(429, null) is Failure.RateLimited)
        assertTrue(classifyStatus(503, "down") is Failure.Server)
        assertTrue(classifyStatus(404, null) is Failure.Server)
        // A 200 that did not parse is our bug, not the server's.
        assertTrue(classifyStatus(200, "{}") is Failure.Malformed)
    }

    @Test
    fun `a server failure keeps a short body for the log`() {
        val f = classifyStatus(500, "x".repeat(1000)) as Failure.Server
        assertTrue("body should be truncated, got ${f.body!!.length}", f.body!!.length <= 200)
    }
}
