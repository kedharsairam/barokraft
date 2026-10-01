package com.krafttools.barokraft

import android.content.Context
import com.krafttools.barokraft.core.SeaLevel
import com.krafttools.barokraft.net.OpenMeteo

/**
 * The city and the sea-level reference, on the device.
 *
 * ## No storage permission
 *
 * These live in the app's own private directory, which needs no permission
 * on any version of Android. That is not a small thing: it means the
 * app's whole persistence story costs the user nothing and there is no
 * permission prompt to explain in a store listing.
 *
 * ## Why the reference is stored at all
 *
 * Because losing it every launch would make altitude permanently
 * unavailable, and the reference is the input the user supplies exactly
 * once. What is stored is the *value* and *when it was set* — never a
 * derived altitude, because a stored altitude would look authoritative
 * long after the reference had gone stale.
 */
class PlaceStore(context: Context) {

    private companion object {
        const val FILE = "barokraft.place"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_NAME = "name"
        const val KEY_COUNTRY = "country"
        const val KEY_ADMIN = "admin1"
        const val KEY_ELEVATION = "elev"
        const val KEY_REFERENCE_HPA = "refHpa"
        const val KEY_REFERENCE_AT = "refAt"
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadPlace(): OpenMeteo.Place? {
        if (!prefs.contains(KEY_LAT)) return null
        return OpenMeteo.Place(
            name = prefs.getString(KEY_NAME, "") ?: "",
            country = prefs.getString(KEY_COUNTRY, null),
            admin1 = prefs.getString(KEY_ADMIN, null),
            latitude = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null,
            longitude = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null,
            elevationMetres = prefs.getString(KEY_ELEVATION, null)?.toDoubleOrNull(),
        )
    }

    fun savePlace(place: OpenMeteo.Place) {
        prefs.edit()
            .putString(KEY_LAT, place.latitude.toString())
            .putString(KEY_LON, place.longitude.toString())
            .putString(KEY_NAME, place.name)
            .putString(KEY_COUNTRY, place.country)
            .putString(KEY_ADMIN, place.admin1)
            .apply {
                place.elevationMetres?.let { putString(KEY_ELEVATION, it.toString()) }
            }
            .apply()
    }

    fun loadReference(): SeaLevel? {
        if (!prefs.contains(KEY_REFERENCE_HPA)) return null
        val hpa = prefs.getString(KEY_REFERENCE_HPA, null)?.toFloatOrNull() ?: return null
        val at = prefs.getLong(KEY_REFERENCE_AT, 0L)
        if (at <= 0L) return null
        return SeaLevel(hpa, at)
    }

    fun saveReference(reference: SeaLevel) {
        prefs.edit()
            .putString(KEY_REFERENCE_HPA, reference.hpa.toString())
            .putLong(KEY_REFERENCE_AT, reference.atMillis)
            .apply()
    }

    fun clearReference() {
        prefs.edit().remove(KEY_REFERENCE_HPA).remove(KEY_REFERENCE_AT).apply()
    }
}
