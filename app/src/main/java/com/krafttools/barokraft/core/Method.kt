package com.krafttools.barokraft.core

/**
 * What this app claims, in the app's own words, as values.
 *
 * ## Why this is a Kotlin file and not a string resource
 *
 * Because a disclosure that lives in a resource file cannot be tested
 * against the code that makes it true. This project has already made that
 * mistake once: a published statistic described an average the code had
 * stopped computing, and the test checked for the word "goodput" rather
 * than for the claim. The claim was wrong and the test was green for a
 * month.
 *
 * So the sentences live here, next to the arithmetic, and `MethodTest`
 * derives each one from the number it describes. If [altitudeFromPressure]
 * changes, the sentence about altitude is still generated from it. If a
 * threshold moves, the sentence moves with it. A claim that goes stale
 * becomes a compile error or a failing test rather than a sentence in a
 * README that nobody re-reads.
 *
 * ## What is deliberately absent
 *
 * There is no claim about tomorrow's weather. A barometer cannot make one,
 * and neither can this app, and the absence is the point: the sentences
 * below are the complete set of things this app asserts about the sky.
 */
object Method {

    /** What the pressure reading is, and what it is not. */
    const val PRESSURE_SOURCE =
        "The phone's barometer. Chip accuracy is about 0.012 hPa, which is " +
            "about 10 cm of altitude at sea level. This is the raw sensor value " +
            "at your location, not a sea-level reduced one."

    /** What the altitude depends on. */
    const val ALTITUDE_DEPENDS_ON =
        "Altitude is station pressure reduced to sea level using the standard " +
            "atmosphere, and it is only as good as the sea-level reference it is " +
            "given. A reference more than ${REFERENCE_USABLE_HOURS.toInt()} hours " +
            "old is not used at all, because sea-level pressure moves with the " +
            "weather and a stale reference produces a plausible wrong number."

    /** What the tendency is. */
    const val TENDENCY_DEFINITION =
        "The rate of change of pressure in hPa per hour, measured over three " +
            "hours. Thresholds follow the meteorological convention: past about " +
            "2 hPa/h is a rapid change, which is 6 hPa in three hours."

    /** What the nowcast is, and what it is not. */
    const val NOWCAST_DEFINITION =
        "A six-hour projection of pressure from your own barometer, built by " +
            "running $ENSEMBLE_SIZE fits with the velocity and acceleration " +
            "perturbed, and reporting the middle 80% of the outcomes. The band is " +
            "the product; the centre is a convenience. This is a nowcast, not a " +
            "forecast, and it says nothing about temperature, rainfall or cloud."

    /** What the model forecast is. */
    const val FORECAST_DEFINITION =
        "Open-Meteo, which combines national weather models from ECMWF, NOAA, " +
            "DWD, Météo-France, the UK Met Office and others, and picks the " +
            "highest-resolution one for your coordinates. The grid cell is 1 to " +
            "25 km depending on region, so a forecast is a statement about that " +
            "cell and not about your street."

    /** The honest summary of how the two sources relate. */
    const val TWO_SOURCES =
        "The forecast knows the timeline and the barometer knows the next few " +
            "hours. They are never averaged into one number, because they are " +
            "different claims about different things. When they disagree, the app " +
            "says so and shows the gap."

    /** What this app will not claim. Each is a promise about absence. */
    object WillNot {
        const val TEMPERATURE =
            "No temperature from the barometer. A pressure sensor has no access " +
                "to temperature, and no estimate is offered."
        const val RAINFALL_AMOUNT =
            "No rainfall amount. Falling pressure is consistent with rain and also " +
                "consistent with a cold front that arrives dry; the sensor cannot " +
                "tell those apart."
        const val WHICH_DAY =
            "No statement about which day the weather improves. A barometer " +
                "describes the next few hours. Past that the model is the only " +
                "source, and this app says which one it is quoting."
        const val BEAT_THE_MODEL =
            "No claim of being more accurate than a numerical weather model. It " +
                "cannot be. The barometer's advantage is that it works with the " +
                "radio off, and that it can notice when the model is running ahead " +
                "of reality."
        const val LOCATION =
            "No location permission. The city is chosen from a search list, never " +
                "read from the device."
    }
}
