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
) : SensorEventListener {

    private companion object {
        const val TAG = "PressureSource"

        /**
         * 10 minutes, in microseconds.
         *
         * An `Int`, because `registerListener` takes an `int samplingPeriodUs`
         * and there is no `Long` overload. 600,000,000 fits comfortably —
         * the largest value here is 1/2^31 of the range — so the narrower
         * type costs nothing and the alternative is a cast at every call.
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
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    /** True when this device has a pressure sensor at all. */
    val isAvailable: Boolean get() = sensor != null

    private val _samples = mutableListOf<PressureSample>()

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
        val registered = sensorManager.registerListener(
            this,
            s,
            SAMPLING_PERIOD_US,
            SAMPLING_PERIOD_US,
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
        sensorManager.unregisterListener(this)
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
        val sample = PressureSample(clock(), value)
        _samples += sample
        while (_samples.size > MAX_SAMPLES) _samples.removeAt(0)
        latestHpa = value
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // A barometer's accuracy constant is reported but never useful:
        // there is no better and worse mode to switch between, and the
        // chip's error is a fixed property of the part, not a state the
        // driver can improve. Logged and otherwise ignored.
        Log.d(TAG, "accuracy changed to $accuracy")
    }
}
