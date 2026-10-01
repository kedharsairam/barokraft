package com.krafttools.barokraft.core

/**
 * What this app is, in the words it uses about itself.
 *
 * ## Why these are `const val` and not string literals in a composable
 *
 * Because a number written twice drifts. This project's third recorded
 * lesson is that a README is not a test — a claim in prose goes stale
 * silently while every test passes — and the same is true of an About
 * sheet, which is prose a user reads instead of a README a developer
 * reads.
 *
 * So the sentences are assembled from the constants that define the
 * arithmetic. [ENSEMBLE_SIZE] is 64 because [nowcast] runs 64 members,
 * [HORIZON_HOURS] is 6 because the ensemble runs six hours forward, and
 * [AboutTest] asserts those texts contain those numbers rather than
 * asserting the numbers are right. Change the ensemble size and the About
 * sheet changes with it; there is no second place to update.
 */
/**
 * A threshold as a person writes it.
 *
 * Trailing zeros are dropped, so `4f` prints as "4" rather than "4.00".
 * "4.00 hPa" in a sentence a user reads is machine output, and the small
 * version of that is what makes a reader wonder what else has not been
 * checked. Three decimals below 0.1 because `VELOCITY_ERROR_HPA_PER_HOUR`
 * is 0.08 and two would round it to 0.08 but a value like 0.012 would
 * lose its meaning entirely.
 */
internal fun fmtThreshold(v: Float): String {
    val pattern = if (v < 0.1f) "%.3f" else "%.2f"
    return String.format(java.util.Locale.US, pattern, v)
        .trimEnd('0')
        .trimEnd('.')
}


object About {

    /** The app's own name, as it appears in the store and the launcher. */
    const val NAME = "BaroKraft"

    /** One line: what it measures, for a store listing or a README header. */
    const val TAGLINE =
        "Reads the pressure in your phone and says what it can honestly " +
            "tell you about the weather in the next few hours."

    /** What this app is for, stated without a claim of superiority. */
    const val PURPOSE =
        "A barometer measures pressure. Pressure falls before weather " +
            "arrives, and the size of the fall says something about what " +
            "is coming. This app does that reading, reports it with its " +
            "uncertainty attached, and never turns it into a forecast " +
            "beyond what a pressure sensor can actually support."

    /**
     * The nowcast, described with the real ensemble size and horizon.
     *
     * Interpolated rather than written, so it cannot contradict
     * [Nowcast.kt].
     */
    val NOWCAST =
        // HORIZON_HOURS is a Float because the arithmetic uses it as one,
        // and interpolating a Float writes "6.0 hours". Formatted rather
        // than interpolated, because a published sentence saying "6.0
        // hours" is the kind of small wrongness that makes a reader
        // wonder what else has not been checked.
        "The next ${horizonText()} hours are estimated $ENSEMBLE_SIZE times " +
            "over. Pressure rate and acceleration are each perturbed within " +
            "measured limits, and the middle 80% of those runs is reported " +
            "as a band. The band is the result; the centre line is a " +
            "convenience. A wide band means the pressure trace is not yet " +
            "constraining the answer, and the app says so instead of " +
            "picking a number out of the middle anyway."

    /**
     * The forecast, and the honest description of what it is.
     *
     * Attribution is required by the licence, so it lives in the app
     * itself and not only in the README — the user reads this, not the
     * repository.
     */
    const val FORECAST_SOURCE =
        "Forecasts come from Open-Meteo, which serves a blend of national " +
            "weather models from ECMWF, NOAA, DWD, Météo-France, the UK Met " +
            "Office and others, and selects the highest-resolution one for " +
            "your coordinates. Forecast data is licensed CC BY 4.0 and " +
            "requires this attribution."

    /**
     * The grid-cell caveat, which the model licence makes a matter of
     * accuracy rather than of courtesy.
     *
     * A model forecast describes a grid cell 1 to 25 km across, not a
     * street, and an app that hides that is overstating what it knows.
     */
    const val GRID_CELL =
        "The grid cell is 1 to 25 km across depending on region, so a " +
            "forecast is a statement about that cell and not about your " +
            "street. Coastal lines, hills and anything downwind of a range " +
            "can differ from it substantially."

    /**
     * The two sources, and the rule that governs them.
     *
     * This is the sentence the whole design exists to make, and it is the
     * one most likely to be misread as a limitation. It is the reason to
     * trust the app: two numbers that disagree are shown disagreeing.
     */
    const val TWO_SOURCES_NEVER_BLENDED =
        "The barometer and the forecast are never averaged into a single " +
            "figure. They answer different questions on different " +
            "timescales — one knows what is happening outside now, the " +
            "other knows what is coming over days — and a blend of the two " +
            "would be less accurate than either, while looking as confident " +
            "as both. When they disagree, this app says they disagree."

    /** What a divergence means, so it is not read as a fault. */
    const val DIVERGENCE_MEANS =
        "A disagreement is worth reading, not a malfunction. A falling " +
            "trace with a rising forecast often means rain or wind has not " +
            "yet reached the ground, or that the forecast is off. Either " +
            "way the honest report is that the two sources do not agree, " +
            "and which one to believe is a judgement the app should leave " +
            "to the person holding it."

    /** How the two sources keep each other honest. */
    const val REFERENCE_AUDIT =
        "A sea-level reference is a value that moves with the weather, so " +
            "a stored one goes stale and starts producing a plausible wrong " +
            "altitude. When a forecast is available, this app compares the " +
            "two and reports the disagreement. An offline-only app cannot " +
            "do that, because it has nothing to check itself against."

    /** What the app never does, which is a design commitment. */
    const val NO_BACKGROUND =
        "There is no background service and no location permission. The " +
            "barometer is read only while this screen is open, and the city " +
            "is one you type. Two permissions in total: internet, and the " +
            "ability to tell whether there is a connection."

    /** The honest performance note. */
    const val SAMPLING =
        "Readings are taken every 10 minutes. A pressure sensor is good to " +
            "about 0.012 hPa and a day's weather is 10 to 40 hPa, so a " +
            "faster interval would mostly collect noise, cost battery, and " +
            "improve nothing a person could read."

    /** The note about phones without a barometer. */
    const val NO_BAROMETER =
        "A phone with no pressure sensor is ordinary hardware, not a " +
            "broken phone. On such a device this app shows the forecast and " +
            "says plainly that there is no local reading, rather than " +
            "displaying zeros or hiding the difference."

    /**
     * The refusals, as one paragraph.
     *
     * Assembled alongside [Method.WillNot] rather than restated, so the
     * About sheet and the in-app method sheet cannot drift apart into
     * saying two different things about what this app will not claim.
     */
    val WILL_NOT_SUMMARY: String =
        "This app will not tell you the temperature from a barometer, " +
            "how much rain to expect, which day a change will arrive on, or " +
            "whether it is more accurate than a national forecast. It has " +
            "no way to know any of those, and a number that looks like an " +
            "answer is worse than an honest absence."

    /** Licensing and provenance. */
    /** [HORIZON_HOURS] as a person writes it. */
    fun horizonText(): String =
        if (HORIZON_HOURS % 1f == 0f) HORIZON_HOURS.toInt().toString()
        else HORIZON_HOURS.toString()

    /**
     * The support link.
     *
     * ## Why it lives in the app and not only the repository
     *
     * The README carries this link and always has, but a user reads the app
     * and never sees the repository. Two of the sibling apps put it in an
     * in-app settings section for exactly that reason; this is the same
     * argument applied to the disclosure sheet, which is the one place a
     * reader goes looking for who made something.
     */
    const val SUPPORT_HEADING = "Support"
    const val SUPPORT_NOTE =
        "This app is free, has no ads and no analytics, and costs nothing " +
            "to run. If it is useful to you, a coffee is the whole business " +
            "model."
    const val SUPPORT_URL = "https://buymeacoffee.com/kedhartech"

    const val LICENCE =
        "Open source under the MIT licence. The source is public, the " +
            "issue tracker is public, and there is no analytics, no " +
            "advertising, and no account."
}