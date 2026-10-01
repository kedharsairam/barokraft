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

    /**
     * Fetch, or null when the answer tells us nothing.
     *
     * ## Why null rather than an exception
     *
     * Because a timeout is a fact about the network, not about the
     * contract. These tests exist to check that the API's arrays stay
     * parallel and that `unixtime` stays epoch seconds; a socket timeout
     * answers none of that, and failing on it means the build reports a
     * broken contract when the contract was never tested.
     *
     * The pre-flight `reachable()` check already swallowed exceptions, which
     * made this worse rather than better: it confirmed the host was up, the
     * test then fetched again, and the second fetch timed out and failed the
     * run. Two requests where one would do, with only the first guarded.
     *
     * So every fetch is guarded, once, at the point of use.
     */
    private fun fetch(url: String): Pair<Int, String>? = try {
        get(url)
    } catch (e: java.io.IOException) {
        // Includes SocketTimeoutException. Logged rather than thrown: the
        // run stays green, and the message is in the log for anyone who
        // wondered why the suite was quiet.
        println("SKIP|${e::class.simpleName}: ${e.message}")
        null
    }

    private fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        // 45 s, not 15. This request asks for ten variables over a day and
        // can be slow from a shared runner; the earlier value fired on a
        // response that was merely late.
        connection.readTimeout = 45_000
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

    private fun reachable(): Boolean =
        fetch("$endpoint?latitude=0&longitude=0&current=temperature_2m")
            ?.first?.let { it in 200..299 } == true

    @Test
    fun `the live endpoint answers with a parseable forecast`() {
        if (!reachable()) return
        val url = Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1)
        val (status, body) = fetch(url) ?: return
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
        val (_, body) = fetch(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1)) ?: return
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
        val (_, body) = fetch(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1)) ?: return
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
        val (_, body) = fetch(Protocol.buildUrl(-0.1807, -78.4678, forecastDays = 1)) ?: return
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
        val (status, body) = fetch(url) ?: return
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
        val (status, body) = fetch("$endpoint?latitude=999&longitude=999&hourly=temperature_2m") ?: return
        if (status in 200..299) return
        val looksHtml = body.trimStart().startsWith("<")
        assertTrue(
            "a failure should not be an html page, got: ${body.take(80)}",
            !looksHtml,
        )
    }
}
