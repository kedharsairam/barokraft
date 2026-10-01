package com.krafttools.barokraft.net

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The request header, derived from the build and never written by hand.
 *
 * ## Why this is not a constant
 *
 * A hand-maintained version string is a version string that will be wrong,
 * and this one was. v0.3.2 was published carrying the **0.3.1 binary**:
 * the header had been bumped in the same shell command whose test run
 * failed, so the APK on disk was still the previous build, and
 * `gh release create` attached it without being asked what it was.
 *
 * Now there is exactly one version in the project. The header cannot
 * disagree with the binary, and `ProtocolTest` asserts it does not.
 *
 * ## Why it matters beyond tidiness
 *
 * Open-Meteo sees this on every request. A release whose header disagrees
 * with its own binary is visible to the API as well as to a user, which
 * makes it a debugging aid rather than only a cosmetic one.
 */
internal val userAgent: String =
    "BaroKraft/" + com.krafttools.barokraft.BuildConfig.VERSION_NAME +
        " (Android; open-source, MIT)"

/**
 * The one network call this app makes.
 *
 * ## Timeouts are not a detail
 *
 * A weather app on a train has to fail *fast* and say so, because the
 * alternative is a spinner that outlives the tunnel. [CONNECT_TIMEOUT_MS]
 * is short deliberately: if TCP has not connected in eight seconds the
 * radio is not going to manage it, and waiting longer only makes the
 * refusal feel broken rather than decisive.
 *
 * ## The body is read once, fully
 *
 * `response.body?.string()` rather than a stream, because the whole
 * response is a few tens of kilobytes and a partially-read body cannot be
 * parsed at all. A streaming reader here would save nothing and would add
 * a way to get a truncated forecast that parses.
 */
open class OpenMeteoClient(
    private val clock: () -> Long = System::currentTimeMillis,
    private val isOnline: () -> Boolean = { true },
    private val client: OkHttpClient = defaultClient(),
) {

    private companion object {
        const val TAG = "OpenMeteo"

        /**
         * Eight seconds. Short on purpose — see the class note. A weather
         * app that takes twenty seconds to admit it has no connection has
         * stopped being useful.
         */
        const val CONNECT_TIMEOUT_MS = 8_000L

        const val READ_TIMEOUT_MS = 10_000L


        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * Fetch and parse a forecast.
     *
     * Every failure path returns a [Failure] rather than throwing, so the
     * caller has to handle each one and the compiler helps it.
     */
    fun forecast(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 7,
    ): NetResult<Protocol.Forecast> {
        if (!isOnline()) return NetResult.Failed(Failure.Offline)

        val url = Protocol.buildUrl(latitude, longitude, forecastDays)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return NetResult.Failed(classifyStatus(response.code, body))
                }
                if (body.isNullOrBlank()) {
                    return NetResult.Failed(Failure.Malformed("the response was empty"))
                }
                try {
                    NetResult.Ok(OpenMeteo.parseForecast(body), clock())
                } catch (e: Protocol.MalformedResponse) {
                    Log.w(TAG, "could not parse a ${response.code} response: ${e.message}")
                    NetResult.Failed(Failure.Malformed(e.message ?: "unparseable"))
                }
            }
        } catch (e: IOException) {
            // IOException is the transport layer saying it never got an
            // answer: no route, DNS failure, TLS rejected, timeout. All
            // of them are "unreachable" from the user's point of view, and
            // none of them are worth distinguishing in the UI.
            Log.w(TAG, "network unreachable: ${e.message}")
            NetResult.Failed(Failure.Unreachable(e.message))
        } catch (e: RuntimeException) {
            // A malformed URL or a TLS misconfiguration is a programming
            // error, not a weather error. Caught so the app cannot crash on
            // the one screen, and logged loudly so it is found.
            Log.e(TAG, "unexpected failure fetching a forecast", e)
            NetResult.Failed(Failure.Malformed(e.message ?: "unexpected"))
        }
    }

    /**
     * Search for a city. An empty result is a successful empty list.
     *
     * `open` because the ViewModel's search is the one place where a test
     * genuinely needs to control *what arrives and in what order* — the
     * city picker's whole correctness argument is about out-of-order
     * responses, and that cannot be provoked by a real network
     * deterministically. The class is already constructed with an injected
     * clock and an injected connectivity predicate for the same reason.
     */
    open fun search(query: String): NetResult<List<OpenMeteo.Place>> {
        if (!isOnline()) return NetResult.Failed(Failure.Offline)
        if (query.isBlank()) return NetResult.Ok(emptyList(), clock())

        val request = Request.Builder()
            .url(Protocol.buildGeocodingUrl(query))
            .header("User-Agent", userAgent)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    return NetResult.Failed(classifyStatus(response.code, body))
                }
                if (body.isNullOrBlank()) {
                    return NetResult.Failed(Failure.Malformed("the response was empty"))
                }
                try {
                    NetResult.Ok(OpenMeteo.parsePlaces(body), clock())
                } catch (e: Protocol.MalformedResponse) {
                    NetResult.Failed(Failure.Malformed(e.message ?: "unparseable"))
                }
            }
        } catch (e: IOException) {
            NetResult.Failed(Failure.Unreachable(e.message))
        } catch (e: RuntimeException) {
            Log.e(TAG, "unexpected failure searching for '$query'", e)
            NetResult.Failed(Failure.Malformed(e.message ?: "unexpected"))
        }
    }
}
