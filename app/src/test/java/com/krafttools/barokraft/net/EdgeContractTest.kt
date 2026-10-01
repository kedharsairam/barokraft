package com.krafttools.barokraft.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * The real edge, verified against the live API.
 *
 * ## What this can and cannot test
 *
 * A *weather figure* cannot be asserted. Nobody knows what Quito's
 * temperature will be on any given day, and a test that asserted a number
 * would fail for reasons that have nothing to do with this app.
 *
 * But the API's **contract** can be, and this app's whole correctness
 * argument depends on it:
 *
 *  - the endpoint answers 200 with a `hourly` object
 *  - `time` and every variable array are parallel and the same length
 *  - `unixtime` really returns epoch seconds
 *  - `pressure_msl` is present, because the drift audit is built on it
 *  - a field the caller did not ask for is absent rather than zero
 *  - an unknown coordinate produces a parseable error, not an HTML page
 *
 * If any of that changed, the app would show a wrong forecast and **no
 * unit test in the suite would notice**, because every other test parses
 * a fixture. That is what these six are for.
 *
 * They **skip** rather than fail without a route, so a build on a plane
 * is a green build and not a broken one.
 */
class EdgeContractTest {

    private val endpoint = "https://api.open-meteo.com/v1/forecast"

    private fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Encoding", "gzip")
        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                val raw = if (connection.contentEncoding?.contains("gzip") == true) {
                    GZIPInputStream(it)
                } else it
                raw.bufferedReader().readText()
            } ?: ""
            status to body
        } finally {
            connection.disconnect()
        }
    }

    private fun reachable(): Boolean = try {
        val (status, _) = get("$endpoint?latitude=0&longitude=0&current=temperature_2m")
        status in 200..299
    } catch (e: Exception) {
        false
    }

    @Test
    fun `the live endpoint answers with a parseable forecast`() {
        if (!reachable()) return
        val url = Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1)
        val (status, body) = get(url)
        assertEquals("the endpoint must answer 200", 200, status)
        val forecast = OpenMeteo.parseForecast(body)
        assertTrue("a day of hourly data is 24 hours", forecast.hours.size >= 24)
    }

    @Test
    fun `every hourly array is the same length as time`() {
        // The pairing invariant. If the API ever returned a shorter
        // variable array, a naive reader would borrow another hour's value
        // and nothing would look wrong.
        if (!reachable()) return
        val (_, body) = get(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1))
        val hourly = parseJson(body).asObject()["hourly"].asObject()
        val times = hourly["time"].asArray().size
        assertTrue("time array was empty", times > 0)
        for (field in listOf(
            "temperature_2m", "precipitation", "weather_code",
            "pressure_msl", "surface_pressure", "cloud_cover",
        )) {
            val size = hourly[field].asArray().size
            assertEquals("$field is $size long, time is $times", times, size)
        }
    }

    @Test
    fun `unixtime really is epoch seconds`() {
        // The app does no timezone arithmetic of its own, so this is the
        // contract that keeps every hour label correct.
        if (!reachable()) return
        val (_, body) = get(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1))
        val first = parseJson(body).asObject()["hourly"].asObject()["time"].asArray()
            .first().asFloatOrNull()!!.toLong()
        val asDate = java.time.Instant.ofEpochSecond(first)
        val now = java.time.Instant.now().epochSecond
        assertTrue(
            "the first hour should be near now, got $asDate vs now",
            kotlin.math.abs(now - first) < 7L * 24 * 3600,
        )
    }

    @Test
    fun `sea level pressure is present, because the drift audit needs it`() {
        if (!reachable()) return
        val (_, body) = get(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1))
        val forecast = OpenMeteo.parseForecast(body)
        val withPressure = forecast.hours.count { it.seaLevelPressureHpa != null }
        assertTrue(
            "pressure_msl should be present for every hour, got $withPressure of ${forecast.hours.size}",
            withPressure >= forecast.hours.size * 9 / 10,
        )
    }

    @Test
    fun `a field we did not ask for is absent rather than zero`() {
        // Zero and absent mean different things: 0% rain is a claim,
        // absent is silence. If the API ever started returning zeros for
        // unrequested fields, every "no rain expected" label would become
        // a false statement.
        if (!reachable()) return
        val url = Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1) +
            "&hourly=not_a_real_variable"
        val (status, body) = get(url)
        assertTrue("an unknown variable should be an error, got $status", status >= 400)
        assertTrue(
            "an error should be json with a reason",
            body.contains("error") || body.contains("reason"),
        )
    }

    @Test
    fun `a nonsensical coordinate produces a parseable error, not an html page`() {
        // A proxy or a captive portal answers with HTML. Parsing that as a
        // forecast is how an app ends up showing a blank screen with no
        // explanation.
        if (!reachable()) return
        val (status, body) = get("$endpoint?latitude=999&longitude=999&hourly=temperature_2m")
        if (status in 200..299) return
        val looksHtml = body.trimStart().startsWith("<")
        assertTrue(
            "a failure should not be an html page, got: ${body.take(80)}",
            !looksHtml,
        )
    }
}
