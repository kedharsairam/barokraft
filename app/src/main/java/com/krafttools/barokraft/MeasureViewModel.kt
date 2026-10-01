package com.krafttools.barokraft

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krafttools.barokraft.core.Policy
import com.krafttools.barokraft.core.PressureSample
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SeaLevel
import com.krafttools.barokraft.core.SeaLevelContext
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.VerdictInput
import com.krafttools.barokraft.core.altitudeFromPressure
import com.krafttools.barokraft.core.nowcast
import com.krafttools.barokraft.core.referenceDrift
import com.krafttools.barokraft.core.referenceStaleness
import com.krafttools.barokraft.core.sourceState
import com.krafttools.barokraft.core.verdict
import com.krafttools.barokraft.net.Failure
import com.krafttools.barokraft.net.NetResult
import com.krafttools.barokraft.net.OpenMeteo
import com.krafttools.barokraft.net.OpenMeteoClient
import com.krafttools.barokraft.net.Protocol
import com.krafttools.barokraft.ui.MeasureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * All of the app's state, and the only thing that mutates it.
 *
 * ## Why this holds everything
 *
 * Because a screen that reads from four sources — a sensor, an HTTP client,
 * DataStore and a clock — can only be tested with all four present. Here
 * they are constructor parameters, and the screen is a pure function of
 * [MeasureState], so the *decisions* are all unit-testable and only the
 * *plumbing* needs a device.
 *
 * ## Why the verdict is recomputed rather than stored
 *
 * Because it is a pure function of things that change independently: the
 * pressure, the reference's age, the model's sea-level pressure, the
 * connection. Caching it would mean invalidating five things, and a
 * headline that is one recompute out of date is a wrong headline.
 */
class MeasureViewModel(
    application: Application,
    private val pressureSource: PressureSource = PressureSource(application),
    private val client: OpenMeteoClient = OpenMeteoClient(
        isOnline = { isOnline(application) },
    ),
    private val store: PlaceStore = PlaceStore(application),
    private val clock: () -> Long = System::currentTimeMillis,
) : AndroidViewModel(application) {

    /** The one piece of state the UI reads. */
    var state by mutableStateOf(MeasureState(SourceState.NEITHER))
        private set

    private var lastFetchedMillis: Long? = null
    private var cachedForecast: Protocol.Forecast? = null
    private var reference: SeaLevel? = null

    init {
        val saved = store.loadPlace()
        reference = store.loadReference()
        recompute(online = isOnline(getApplication()))
        if (saved != null) {
            state = state.copy(place = saved)
            refresh()
        }
    }

    // ── Controls ────────────────────────────────────────────────────────

    fun toggleSampling() {
        if (state.sampling) {
            pressureSource.stop()
        } else if (pressureSource.isAvailable) {
            pressureSource.start()
        }
        recompute(online = isOnline(getApplication()))
    }

    /**
     * A factory, so no reflection is involved in construction.
     *
     * This class has five constructor parameters and four of them have
     * defaults, which is the best kind of signature to write and the
     * worst kind for a default `ViewModelProvider` factory. That factory
     * looks for a single-argument `(Application)` constructor by
     * reflection; Kotlin does not generate one from defaulted parameters,
     * so the app died on launch with
     *
     *     Cannot create an instance of class MeasureViewModel
     *
     * `@JvmOverloads` would have produced the constructor and hidden the
     * problem behind a synthetic bridge. An explicit factory removes the
     * reflection entirely, states the dependencies at the call site, and
     * is the one version a test can construct.
     */
    class Factory(
        private val application: Application,
        private val pressureSource: () -> PressureSource = { PressureSource(application) },
        private val client: () -> OpenMeteoClient = {
            OpenMeteoClient(isOnline = { isOnline(application) })
        },
        private val store: () -> PlaceStore = { PlaceStore(application) },
        private val clock: () -> Long = System::currentTimeMillis,
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MeasureViewModel::class.java)) {
                "this factory only builds MeasureViewModel, not ${modelClass.name}"
            }
            return MeasureViewModel(
                application = application,
                pressureSource = pressureSource(),
                client = client(),
                store = store(),
                clock = clock,
            ) as T
        }
    }

    /** Stop sampling without toggling, for the lifecycle's `onStop`. */
    fun toggleSamplingIfRunning() {
        if (state.sampling) {
            pressureSource.stop()
            recompute(online = isOnline(getApplication()))
        }
    }

    fun openPlacePicker() {
        state = state.copy(pickingPlace = true)
        if (state.searchResults.isEmpty()) search(state.searchQuery)
    }

    fun dismissPlacePicker() {
        state = state.copy(pickingPlace = false, searching = false)
    }

    fun openMethod() {
        state = state.copy(showingMethod = true)
    }

    fun dismissMethod() {
        state = state.copy(showingMethod = false)
    }

    fun choosePlace(place: OpenMeteo.Place) {
        store.savePlace(place)
        state = state.copy(
            place = place,
            pickingPlace = false,
            searchResults = emptyList(),
            searchQuery = "",
            searchEmpty = false,
        )
        // A new city invalidates the cached forecast outright rather than
        // showing Palakollu's weather over a header that says London.
        cachedForecast = null
        lastFetchedMillis = null
        refresh(force = true)
    }

    fun onSearchQueryChange(query: String) {
        state = state.copy(searchQuery = query, searchEmpty = false)
        if (query.length >= 2) search(query) else state = state.copy(searchResults = emptyList())
    }

    private fun search(query: String) {
        state = state.copy(searching = true)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { client.search(query) }) {
                is NetResult.Ok -> state = state.copy(
                    searching = false,
                    searchResults = result.value,
                    // An empty result for a real query is a legitimate
                    // answer, and saying so is better than leaving an
                    // empty list the user cannot interpret.
                    searchEmpty = result.value.isEmpty(),
                )
                is NetResult.Failed -> state = state.copy(
                    searching = false,
                    searchResults = emptyList(),
                    searchEmpty = false,
                    failure = result.reason,
                )
            }
        }
    }

    /**
     * Set the sea-level reference from a known altitude.
     *
     * The one way a user can calibrate without knowing a QNH off the top
     * of their head: they know how high they are, the app does the rest.
     */
    fun calibrateFromAltitude(altitudeMetres: Float) {
        val station = pressureSource.latestHpa ?: return
        val seaLevel = com.krafttools.barokraft.core.seaLevelFromAltitude(
            stationHpa = station,
            altitudeMetres = altitudeMetres,
        ) ?: return
        val ref = SeaLevel(seaLevel, clock())
        reference = ref
        store.saveReference(ref)
        recompute(online = isOnline(getApplication()))
    }

    fun clearReference() {
        reference = null
        store.clearReference()
        recompute(online = isOnline(getApplication()))
    }

    // ── Fetching ────────────────────────────────────────────────────────

    fun refresh(force: Boolean = false) {
        val online = isOnline(getApplication())
        val now = clock()
        if (!force && !Policy.shouldFetch(online, lastFetchedMillis, now)) {
            // Not a failure. Policy is refusing to spend the user's data
            // for a forecast that is three minutes old, and the screen
            // says "fetched 3 minutes ago" rather than showing an error.
            state = state.copy(failure = null)
            return
        }
        val place = state.place ?: run {
            state = state.copy(failure = Failure.NoPlaceSelected())
            return
        }
        state = state.copy(fetching = true, failure = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                client.forecast(place.latitude, place.longitude)
            }
            when (result) {
                is NetResult.Ok -> {
                    cachedForecast = result.value
                    lastFetchedMillis = result.atMillis
                    state = state.copy(
                        forecast = result.value,
                        forecastAtMillis = result.atMillis,
                        fetching = false,
                        failure = null,
                    )
                    recompute(online)
                }
                is NetResult.Failed -> {
                    state = state.copy(
                        fetching = false,
                        failure = result.reason,
                        // A failed refresh must not blank a forecast that
                        // is already on screen. Three hours old and
                        // labelled is worth more than a spinner.
                        forecast = cachedForecast,
                        forecastAtMillis = lastFetchedMillis,
                    )
                    recompute(online)
                }
            }
        }
    }

    // ── Deriving the state ──────────────────────────────────────────────

    private fun recompute(online: Boolean) {
        val now = clock()
        val hasBarometer = pressureSource.isAvailable
        val samples: List<PressureSample> = pressureSource.samples

        val context = SeaLevelContext(now)
        val staleness = referenceStaleness(reference, context)

        val altitude = reference
            ?.takeIf { Policy.mayShowAltitude(staleness) }
            ?.let { altitudeFromPressure(pressureSource.latestHpa ?: 0f, it.hpa) }

        // The drift audit. Only ever computed with two sources and a
        // reference good enough for the comparison to mean anything —
        // auditing with a stale reference would "discover" its own error.
        val drift = if (
            Policy.mayCompareSources(sourceState(hasBarometer, online), staleness)
        ) {
            val local = reference?.let {
                pressureSource.latestHpa?.let { p ->
                    com.krafttools.barokraft.core.seaLevelFromAltitude(p, altitude ?: 0f)
                }
            }
            val model = cachedForecast?.hourAt(now)?.seaLevelPressureHpa
            if (local != null && model != null) referenceDrift(local, model) else null
        } else {
            null
        }

        val points = if (hasBarometer) {
            nowcast(samples, nowMillis = now)
        } else {
            emptyList()
        }

        val severe = cachedForecast?.hourAt(now)?.weatherCode
            ?.takeIf { com.krafttools.barokraft.core.Wmo.isSevere(it) }

        state = state.copy(
            sourceState = sourceState(hasBarometer, online),
            sampling = pressureSource.isSampling,
            samples = samples,
            currentHpa = pressureSource.latestHpa,
            reference = reference,
            staleness = staleness,
            altitudeMetres = altitude,
            forecast = cachedForecast,
            modelPressure = cachedForecast?.hourAt(now)?.seaLevelPressureHpa,
            verdict = verdict(
                VerdictInput(
                    state = sourceState(hasBarometer, online),
                    nowMillis = now,
                    staleness = staleness,
                    severeWeatherCode = severe,
                    nowcastPoints = points,
                    currentHpa = pressureSource.latestHpa,
                    referenceDriftHpa = drift,
                    tendencyHpaPerHour = threeHour(samples),
                ),
            ),
        )
    }

    private fun threeHour(samples: List<PressureSample>): Float? =
        com.krafttools.barokraft.core.tendencyHpaPerHour(samples)

    override fun onCleared() {
        pressureSource.stop()
        super.onCleared()
    }
}

/**
 * Whether there is a usable connection.
 *
 * Deliberately asks whether the network is *validated*, not merely
 * connected. An Android device will happily report a connection to a
 * captive portal in a hotel lobby, and a fetch into that either hangs or
 * returns the portal's login page — which is exactly the case the edge
 * contract test asserts against.
 */
internal fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
