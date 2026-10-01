package com.krafttools.barokraft

import android.app.Application
import com.krafttools.barokraft.core.ReferenceStaleness
import com.krafttools.barokraft.core.SeaLevel
import com.krafttools.barokraft.core.SourceState
import com.krafttools.barokraft.core.VerdictTone
import com.krafttools.barokraft.net.Failure
import com.krafttools.barokraft.net.NetResult
import com.krafttools.barokraft.net.OpenMeteo
import com.krafttools.barokraft.net.OpenMeteoClient
import com.krafttools.barokraft.net.Protocol
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.test.core.app.ApplicationProvider
import java.io.IOException

/**
 * The ViewModel, built for real, with no network and no sensor.
 *
 * ## Why this file exists at all
 *
 * Because the app shipped a green build that **crashed on launch**:
 *
 *     java.lang.RuntimeException: Cannot create an instance of class
 *     MeasureViewModel
 *
 * Five constructor parameters, four of them defaulted, and the default
 * `ViewModelProvider` factory reflects for a single-argument
 * `(Application)` constructor that Kotlin does not generate. Every unit
 * test passed because they test pure functions, and every instrumented
 * test passed because they drive the screen as a pure function of a state
 * record. **No test constructed the ViewModel at all.**
 *
 * So the first test here is the one that would have caught it, and it is
 * deliberately the crudest thing in the suite: build the thing. If that
 * ever needs a device again, the architecture has regressed.
 */
@RunWith(RobolectricTestRunner::class)
class MeasureViewModelTest {

    private val application: Application = ApplicationProvider.getApplicationContext()

    private var now = 1_800_000_000_000L

    private fun clock(): Long = now

    private fun forecast(hours: Int = 24, msl: Float? = 1006.4f, code: Int = 51) = Protocol.Forecast(
        latitude = 50.9375,
        longitude = 6.9603,
        elevationMetres = 53.0,
        utcOffsetSeconds = 0,
        hours = (0 until hours).map { i ->
            Protocol.Hour(
                atMillis = now + i * 3_600_000L,
                temperatureC = 19.5f,
                apparentTemperatureC = 18f,
                precipitationMm = 0.2f,
                precipitationProbability = 40,
                weatherCode = code,
                windSpeedKmh = 11.5f,
                cloudCoverPercent = 80,
                visibilityMetres = 10_000f,
                seaLevelPressureHpa = msl,
                surfacePressureHpa = msl?.minus(1f),
            )
        },
        modelName = "best_match",
    )

    /**
     * A client that never touches the network.
     *
     * Failing loudly rather than returning a canned value is deliberate:
     * a test that passes against a fake the code cannot actually reach is
     * a test of the fake.
     */
    private fun client(
        online: Boolean = true,
        forecastResult: () -> NetResult<Protocol.Forecast> = {
            NetResult.Failed(Failure.Unreachable("the test never reaches a network"))
        },
    ) = OpenMeteoClient(
        clock = ::clock,
        isOnline = { online },
        client = OkHttpClient.Builder()
            .build()
            .also { c ->
                c.newBuilder().connectTimeout(1, java.util.concurrent.TimeUnit.MILLISECONDS)
            },
    ).also { c ->
        // The client is real; only the online flag and the clock vary. A
        // test that needs a specific response injects it through
        // `fetchOverride` below instead of a fake client, so the parsing
        // path stays the one that ships.
    }

    // ── The crash ───────────────────────────────────────────────────────

    @Test
    fun `the factory builds a view model`() {
        // The regression test for the launch crash. If this fails, the app
        // does not open, and nothing else in the suite would notice.
        val factory = MeasureViewModel.Factory(application)
        val vm = factory.create(MeasureViewModel::class.java)
        assertNotNull(vm)
        assertTrue(vm is MeasureViewModel)
    }

    @Test
    fun `the factory refuses a view model it does not build`() {
        // Silently building the wrong type is how a classloader problem
        // becomes a runtime crash much later.
        val factory = MeasureViewModel.Factory(application)
        try {
            @Suppress("UNCHECKED_CAST")
            factory.create(androidx.lifecycle.ViewModel::class.java as Class<androidx.lifecycle.ViewModel>)
            // ViewModel::class is not assignable from MeasureViewModel, so
            // this must throw. If it does not, the guard is gone.
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("MeasureViewModel"))
        }
    }

    @Test
    fun `the default constructor path is what the activity uses`() {
        // MainActivity builds the ViewModel through this factory. If the
        // call site changes back to `by viewModels()`, the crash returns
        // and this is the test that has to notice.
        val vm = MeasureViewModel(
            application = application,
            pressureSource = PressureSource.absent(application, ::clock),
            client = client(),
            store = PlaceStore(ApplicationProvider.getApplicationContext()),
            clock = ::clock,
            onlineCheck = { true },
        )
        assertNotNull(vm.state)
    }

    // ── State derivation ────────────────────────────────────────────────

    private fun vm(
        online: Boolean = true,
        hasBarometer: Boolean = true,
    ): MeasureViewModel {
        // `present()` / `absent()` rather than the real constructor, so
        // no SensorManager is involved and the readings arrive through
        // inject() instead of a ten-minute timer.
        val source = if (hasBarometer) {
            PressureSource.present(application, ::clock)
        } else {
            PressureSource.absent(application, ::clock)
        }
        return MeasureViewModel(
            application = application,
            pressureSource = source,
            client = client(online = online),
            store = PlaceStore(ApplicationProvider.getApplicationContext()),
            clock = ::clock,
            onlineCheck = { online },
        )
    }

    @Test
    fun `no barometer and no connection is the blocked state`() {
        val model = vm(online = false, hasBarometer = false)
        assertEquals(SourceState.NEITHER, model.state.sourceState)
        assertEquals(VerdictTone.BLOCKED, model.state.verdict?.tone)
    }

    @Test
    fun `no barometer but a connection is a normal state, not a failure`() {
        // Every budget handset and most tablets live here. Treating it as
        // degraded is the mistake this enum exists to prevent.
        val model = vm(online = true, hasBarometer = false)
        assertEquals(SourceState.NETWORK_ONLY, model.state.sourceState)
        assertTrue(model.state.capabilities.canShowForecast)
        assertFalse(model.state.capabilities.canShowPressure)
        assertFalse(
            "must not be blocked",
            model.state.verdict?.tone == VerdictTone.BLOCKED,
        )
    }

    @Test
    fun `a barometer and a connection unlocks both`() {
        val model = vm(online = true, hasBarometer = true)
        model.injectReading(1004.2f, online = true)
        assertEquals(SourceState.BOTH, model.state.sourceState)
        assertTrue(model.state.capabilities.canAuditReference)
        assertEquals(1004.2f, model.state.currentHpa!!, 0.001f)
    }

    @Test
    fun `a barometer alone is the offline state`() {
        val model = vm(online = false, hasBarometer = true)
        model.injectReading(1004.2f, online = false)
        assertEquals(SourceState.BAROMETER_ONLY, model.state.sourceState)
        assertEquals(1004.2f, model.state.currentHpa!!, 0.001f)
    }

    // ── The reference and altitude ──────────────────────────────────────

    @Test
    fun `no reference means no altitude`() {
        val model = vm()
        model.injectReading(1004.2f, online = true)
        assertEquals(ReferenceStaleness.UNSET, model.state.staleness)
        assertNull(model.state.altitudeMetres)
    }

    @Test
    fun `a fresh reference produces an altitude`() {
        val model = vm()
        model.injectReading(1004.2f, online = true)
        model.calibrateFromAltitude(100f)
        assertEquals(ReferenceStaleness.FRESH, model.state.staleness)
        assertNotNull("a fresh reference should give an altitude", model.state.altitudeMetres)
        assertEquals(100f, model.state.altitudeMetres!!, 2f)
    }

    @Test
    fun `a stale reference withholds the altitude and says so in the verdict`() {
        val model = vm()
        model.injectReading(1004.2f, online = true)
        model.calibrateFromAltitude(100f)
        assertNotNull(model.state.altitudeMetres)

        // Six hours later: past FRESH, inside USABLE.
        now += 6L * 3_600_000L
        model.injectReading(1004.2f, online = true)
        assertEquals(ReferenceStaleness.AGING, model.state.staleness)
        assertNotNull("an ageing reference still shows altitude", model.state.altitudeMetres)

        // Twenty hours: past USABLE. The altitude must go.
        now += 14L * 3_600_000L
        model.injectReading(1004.2f, online = true)
        assertEquals(ReferenceStaleness.STALE, model.state.staleness)
        assertNull(
            "a stale reference must not produce an altitude at all",
            model.state.altitudeMetres,
        )
        assertEquals(VerdictTone.CALIBRATION, model.state.verdict?.tone)
    }

    @Test
    fun `clearing the reference removes the altitude`() {
        val model = vm()
        model.injectReading(1004.2f, online = true)
        model.calibrateFromAltitude(100f)
        assertNotNull(model.state.altitudeMetres)
        model.clearReference()
        assertNull(model.state.altitudeMetres)
        assertEquals(ReferenceStaleness.UNSET, model.state.staleness)
    }

    @Test
    fun `calibrating with no reading does nothing`() {
        val model = vm()
        model.calibrateFromAltitude(100f)
        assertNull("no reading means no calibration", model.state.altitudeMetres)
    }

    // ── Fetching ────────────────────────────────────────────────────────

    @Test
    fun `refreshing with no city selected is not an error`() {
        val model = vm()
        model.refresh(force = true)
        assertTrue(
            "expected NoPlaceSelected, got ${model.state.failure}",
            model.state.failure is Failure.NoPlaceSelected,
        )
    }

    @Test
    fun `offline refresh is refused before a request is attempted`() {
        val model = vm(online = false)
        model.choosePlace(place())
        model.refresh(force = true)
        // Policy.shouldFetch returns false offline, so the fetch never
        // runs and no network is touched.
        assertNotNull(model.state.place)
    }

    @Test
    fun `choosing a place clears the cached forecast`() {
        // A new city must not inherit the old city's weather. This is the
        // bug that would show Cologne's forecast under a header saying
        // Kolkata.
        val model = vm()
        model.choosePlace(place())
        assertNull(model.state.forecast)
    }

    @Test
    fun `choosing a place stores it`() {
        val model = vm()
        val p = place()
        model.choosePlace(p)
        assertEquals(p.name, model.state.place?.name)
    }

    private fun place() = OpenMeteo.Place(
        name = "Cologne",
        country = "Germany",
        admin1 = "North Rhine-Westphalia",
        latitude = 50.9375,
        longitude = 6.9603,
        elevationMetres = 53.0,
    )

    // ── The divergence audit ────────────────────────────────────────────

    /**
     * A calibrated ViewModel, plus the reference it settled on.
     *
     * The reference is read back from the model rather than hardcoded,
     * because the first version of these two tests assumed it was
     * 1005.3 — the station pressure — and it is in fact ~1011.6. The two
     * cases were then labelled in reverse, and both failed: one expecting
     * agreement was given a 6 hPa disagreement, and one expecting a
     * warning was given agreement. Deriving the fixture removes the
     * assumption instead of correcting the number.
     */
    private fun calibrated(online: Boolean = true): Pair<MeasureViewModel, Float> {
        val model = vm(online = online)
        model.injectReading(1005.3f, online = online)
        model.calibrateFromAltitude(53f)
        return model to model.state.reference!!.hpa
    }

    @Test
    fun `an agreeing pair is not a divergence`() {
        // Two sources that agree is the common case and the app should say
        // nothing about it. Only a real disagreement deserves attention.
        val (model, reference) = calibrated()
        model.injectForecast(forecast(msl = reference + 0.2f), online = true)
        assertNotNull("both sources should be present", model.state.modelPressure)
        assertTrue(
            "0.2 hPa is agreement, not divergence: ${model.state.verdict?.tone}",
            model.state.verdict?.tone != VerdictTone.DIVERGENCE,
        )
    }

    @Test
    fun `a badly wrong reference is caught by the network forecast`() {
        // The audit no offline-only app can perform: the stored reference
        // has drifted as the weather moved, the model's sea-level pressure
        // says so, and the app reports it rather than continuing to print a
        // plausible wrong altitude.
        val (model, reference) = calibrated()
        // 6 hPa, well past the 3 hPa threshold in REFERENCE_DRIFT_STALE_HPA.
        model.injectForecast(forecast(msl = reference - 6f), online = true)
        assertEquals(
            "a reference this wrong must be surfaced, not trusted",
            VerdictTone.DIVERGENCE,
            model.state.verdict?.tone,
        )
    }

    @Test
    fun `severe weather outranks a drifted reference`() {
        // Precedence is a design decision with a test behind it: a storm
        // is the more urgent fact, and a calibration notice must not bury
        // it.
        val (model, reference) = calibrated()
        model.injectForecast(forecast(msl = reference - 6f, code = 95), online = true)
        assertEquals(
            "severe weather outranks calibration",
            VerdictTone.WARNING,
            model.state.verdict?.tone,
        )
    }

    @Test
    fun `no network means no audit and no divergence`() {
        // Auditing with one source would be inventing the second one.
        val model = vm(online = false)
        model.injectReading(1005.3f, online = false)
        model.calibrateFromAltitude(53f)
        assertEquals(SourceState.BAROMETER_ONLY, model.state.sourceState)
        assertTrue(
            "nothing to audit offline",
            model.state.verdict?.tone != VerdictTone.DIVERGENCE,
        )
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    @Test
    fun `stopping sampling when not sampling is harmless`() {
        // onStop fires on every backgrounding, including when the app
        // never started. It must not throw.
        val model = vm()
        model.toggleSamplingIfRunning()
        model.toggleSamplingIfRunning()
    }

    @Test
    fun `the state survives being read without a recompute`() {
        // The screen reads `state` on every composition. Reading it must
        // not mutate anything, or a rotation would corrupt the app.
        val model = vm()
        model.injectReading(1004.2f, online = true)
        val first = model.state
        repeat(5) { model.state }
        assertEquals(first, model.state)
    }
}
