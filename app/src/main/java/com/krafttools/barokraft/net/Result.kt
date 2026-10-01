package com.krafttools.barokraft.net

/**
 * A typed outcome, so a failure is a value the screen can render rather
 * than an exception it has to catch.
 *
 * ## Why the reasons are enumerated
 *
 * "Something went wrong" is the least useful thing an app can say, and it
 * is what a bare `catch (e: Exception)` produces. Each reason here
 * carries a different instruction for the user:
 *
 * - [Offline] — nothing to do but wait, and the barometer may still work
 * - [Stale] — there is a cached forecast; show it and label its age
 * - [RateLimited] — do not retry immediately; the daily quota is shared
 * - [Malformed] — the response did not match what this app expects, which
 *   is a bug on our side and worth saying differently from a dead network
 *
 * Modelling these separately is what lets [Policy] decide *whether to
 * fetch at all*, which is the rule that keeps the app off a data bill.
 */
sealed class NetResult<out T> {

    data class Ok<T>(
        val value: T,
        /** When the underlying data was generated, for the age label. */
        val atMillis: Long,
    ) : NetResult<T>()

    data class Failed(val reason: Failure) : NetResult<Nothing>()

    val isOk: Boolean get() = this is Ok

    fun valueOrNull(): T? = (this as? Ok)?.value
}

/** Why a request did not produce data. */
sealed class Failure {

    /** No usable network. Not the same as a server being down. */
    object Offline : Failure()

    /**
     * The request was not made because the cached copy is still good.
     *
     * Not an error. It is [com.krafttools.barokraft.core.Policy] doing its
     * job, and it gets its own case so the screen can say "showing the
     * forecast from two hours ago" instead of showing an error.
     */
    object NotNeeded : Failure()

    /** The server answered, and the answer was no. */
    data class Server(val statusCode: Int, val body: String?) : Failure()

    /** A DNS or TLS failure — the request never got an answer. */
    data class Unreachable(val detail: String?) : Failure()

    /**
     * A 200 whose body this app could not understand.
     *
     * Distinct from [Server] on purpose: a 200 that does not parse is
     * this app's bug, and telling the user "the weather service is
     * unavailable" would be both untrue and useless.
     */
    data class Malformed(val detail: String) : Failure()

    /** Quota exhausted. Open-Meteo allows 10,000 calls a day on the free tier. */
    object RateLimited : Failure()

    /** A user-set city, held locally. */
    data class NoPlaceSelected(val detail: String? = null) : Failure()
}

/** Classify a response, so the mapping from HTTP to meaning is one place. */
fun classifyStatus(statusCode: Int, body: String?): Failure = when {
    statusCode == 429 -> Failure.RateLimited
    statusCode in 200..299 -> {
        // A 200 that does not parse is Malformed, not Server. Anything
        // else in 2xx that does not parse lands here too, and that is
        // correct: the transport succeeded and the contract did not.
        Failure.Malformed("unexpected success status $statusCode")
    }
    else -> Failure.Server(statusCode, body?.take(200))
}
