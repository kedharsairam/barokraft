package com.krafttools.barokraft

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.krafttools.barokraft.core.PressureSample
import com.krafttools.barokraft.core.threeHourTendency
import kotlin.math.abs

/**
 * The pressure sensor, wrapped.
 *
 * ## Sampling interval
 *
 * 10 minutes, and that is a deliberate choice rather than a default.
 *
 * A barometer's signal is small and slow. The chip is good to about
 * 0.012 hPa, a day's weather is 10–40 hPa, and a front takes hours to
 * arrive. Fifteen-second sampling — which is what the sibling app's live
 * trace uses — buys nothing here: the extra 1,699 samples an hour are
 * noise, they cost battery, and a three-hour window needs 18 of them
 * rather than 720.
 *
 * The live trace in that app is a different problem, because a *trace*
 * is the point. Here the product is a number and a verdict, and a verdict
 * does not change in fifteen seconds.
 *
 * ## No background service
 *
 * Sampling runs only while the screen is open, and this is a considered
 * refusal rather than an omission. A foreground service for a barometer
 * means a permanent notification, `FOREGROUND_SERVICE_DATA_SYNC`,
 * `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` — four permissions
 * and a standing complaint from the user, in exchange for watching the
 * weather change while the phone is in a pocket. The trade is not worth
 * it for an app whose premise is that it works when you look at it.
 *
 * ## The reading is a Float and it stays one
 *
 * `Sensor.TYPE_PRESSURE` reports hPa directly. No conversion, no scaling,
 * and no rounding — because rounding a 0.012 hPa signal to the nearest
 * hPa would destroy the entire measurement.
 */
class PressureSource(
    context: Context,
    /** Injected so a test can decide what time it is. */
    private val clock: () -> Long = System::currentTimeMillis,
    private val sensorOverride: Sensor? = null,
    /**
     * Forces [isAvailable] without a `Sensor` instance.
     *
     * `Sensor` is a final framework class with no public constructor, so a
     * test cannot build one — and the decision this drives is not a detail:
     * it chooses between the two largest code paths in the app, because a
     * phone with no barometer is *normal* hardware rather than a broken
     * one. An earlier attempt at this reached for reflection to set the
     * private `mType` field, which is a fragile thing to depend on a
     * platform class for and buys nothing: the ViewModel asks whether a
     * barometer exists, never what type it is.
     */
    private val availableOverride: Boolean? = null,
) : SensorEventListener {

    companion object {
        /**
         * A source for a device with no barometer.
         *
         * The seam that lets the ViewModel be built without a handset.
         * Without it, the state this app's design most exists to handle —
         * a phone that simply has no pressure sensor — could only be
         * reached on a device that happens to lack one.
         */
        fun absent(
            context: Context,
            clock: () -> Long = System::currentTimeMillis,
        ): PressureSource = PressureSource(context, clock, availableOverride = false)

        /**
         * A source that reports a barometer but never delivers an event.
         *
         * Readings arrive through `inject` rather than a ten-minute timer,
         * so a test drives the whole derivation chain without waiting.
         */
        fun present(
            context: Context,
            clock: () -> Long = System::currentTimeMillis,
        ): PressureSource = PressureSource(context, clock, availableOverride = true)

        const val TAG = "PressureSource"

        /**
         * 10 minutes, in microseconds.
         *
         * An `Int`, because `registerListener` takes an `int
         * samplingPeriodUs` and there is no `Long` overload. 600,000,000
         * fits comfortably, so the narrower type costs nothing.
         */
        const val SAMPLING_PERIOD_US = 10 * 60 * 1_000_000

        /**
         * How many samples to keep.
         *
         * 6 hours at 10 minutes is 36. The nowcast horizon is 6 hours and
         * the three-hour tendency wants a window behind the forecast, so
         * 36 is the smallest number that serves both with room to spare.
         * An unbounded list would grow without limit for a screen that is
         * simply open, which is a slow memory leak dressed as a feature.
         */
        const val MAX_SAMPLES = 36

        /**
         * `maxReportLatencyUs`. Zero, and never anything else.
         *
         * ## Why this is the important line in the file
         *
         * The four-argument `registerListener` takes a **sampling period**
         * and a **max report latency**, and they are not the same thing. The
         * second is how long the framework may *batch* events before
         * delivering any of them.
         *
         * An earlier version passed the ten-minute sampling period as both.
         * That told Android it had up to ten minutes to accumulate events, so
         * the first one was withheld for ten minutes — and the app's
         * headline number sat as a pair of dashes for that long after
         * launch. `dumpsys sensorservice` said it plainly:
         *
         *     samplingPeriod=1000000us batchingPeriod=600000000us
         *     first flush pending: false
         *
         * Batching exists to save power when you want *many* readings and can
         * live with them arriving together. This app wants one reading every
         * ten minutes and wants each one immediately, so batching saves
         * nothing and costs the entire first reading.
         */
        const val MAX_REPORT_LATENCY_US = 0

        /**
         * Readings closer together than this are discarded.
         *
         * ## Why
         *
         * Registering a sensor makes the framework deliver a **burst** of
         * recent values to fill its history, and those arrive within
         * milliseconds of each other. Stored as independent readings they
         * are indistinguishable, from the data's point of view, from a real
         * 36-hour history: a freshly launched app drew a 30-point trace and
         * computed a three-hour tendency from readings taken in one second.
         *
         * Thirty seconds is well below the ten-minute sampling period and
         * well above any burst, so nothing real is lost. What it prevents is
         * the app presenting elapsed time it did not measure.
         */
        const val MIN_GAP_MILLIS = 30_000L
    }

    private val _samples = mutableListOf<PressureSample>()

    /**
     * Called on the main thread after every accepted reading.
     *
     * ## Why this exists
     *
     * Because a sensor that updates itself and tells nobody produces a screen
     * that never changes. `onSensorChanged` filled the sample list and
     * nothing re-derived the state from it, so the app could sit showing
     * dashes with a perfectly good reading sitting in memory — which is
     * exactly what it did, and it only showed up by watching the screen
     * after a launch rather than by running any test.
     *
     * Null rather than an event, because the only thing a listener can
     * usefully do here is re-derive everything, and passing the reading
     * invites a listener that updates one field and leaves the rest stale.
     */
    var onSample: (() -> Unit)? = null

    /** Set by a test to push a reading, exactly as the sensor would. */
    internal fun inject(hpa: Float, atMillis: Long = clock()) {
        // No gap check here: a test pushes readings deliberately and is the
        // authority on what its fixture contains.
        _samples += PressureSample(atMillis, hpa)
        while (_samples.size > MAX_SAMPLES) _samples.removeAt(0)
        latestHpa = hpa
        onSample?.invoke()
    }

    // Guarded because a Context is allowed to have no system services at
    // all, and this must not be the thing that crashes a device.
    private val sensorManager: SensorManager? = runCatching {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }.getOrNull()

    private val sensor: Sensor? = sensorOverride ?: runCatching {
        sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    }.getOrNull()

    /** True when this device has a pressure sensor at all. */
    val isAvailable: Boolean get() = availableOverride ?: (sensor != null)

    /** The history, oldest first. A copy, so callers cannot mutate it. */
    val samples: List<PressureSample> get() = _samples.toList()

    /** The most recent reading, or null before the first event. */
    var latestHpa: Float? = null
        private set

    var isSampling: Boolean = false
        private set

    /**
     * The tendency over the last three hours, or null when there is not
     * enough history.
     *
     * Computed from the retained window rather than kept up to date
     * incrementally, because it is read once per screen composition and a
     * three-hour window is small enough that recomputing is cheaper than
     * the bookkeeping to keep it warm.
     */
    val tendencyHpaPerHour: Float?
        get() = threeHourTendency(_samples)

    fun start() {
        val s = sensor ?: run {
            Log.i(TAG, "no pressure sensor on this device; sampling unavailable")
            return
        }
        if (isSampling) return
        val manager = sensorManager ?: return
        val registered = manager.registerListener(
            this,
            s,
            SAMPLING_PERIOD_US,
            // Zero: deliver every event as it happens. See the note on
            // MAX_REPORT_LATENCY_US for why this is not the sampling period.
            MAX_REPORT_LATENCY_US,
        )
        if (registered) {
            isSampling = true
            Log.i(TAG, "sampling every 10 minutes")
        } else {
            Log.w(TAG, "the sensor refused registration")
        }
    }

    fun stop() {
        if (!isSampling) return
        sensorManager?.unregisterListener(this)
        isSampling = false
        Log.i(TAG, "stopped sampling")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val value = event.values.firstOrNull() ?: return
        // Guard the range. A few devices report 0 before the first real
        // reading, and a zero would be stored and then charted as the
        // most dramatic dip in the history.
        if (value <= 0f || value > 1200f) {
            Log.w(TAG, "implausible pressure $value hPa, discarded")
            return
        }
        // Drop a burst reading. The framework delivers a catch-up batch on
        // registration; see MIN_GAP_MILLIS for why those are not data.
        val now = clock()
        val previous = _samples.lastOrNull()?.atMillis ?: 0L
        if (_samples.isNotEmpty() && now - previous < MIN_GAP_MILLIS) return

        val sample = PressureSample(now, value)
        _samples += sample
        while (_samples.size > MAX_SAMPLES) _samples.removeAt(0)
        latestHpa = value
        onSample?.invoke()

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // A barometer's accuracy constant is reported but never useful:
        // there is no better and worse mode to switch between, and the
        // chip's error is a fixed property of the part, not a state the
        // driver can improve. Logged and otherwise ignored.
        Log.d(TAG, "accuracy changed to $accuracy")
    }
}
