package io.github.michaelmassoni.tessera.weather.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.PrecipitationUnit
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.Units
import io.github.michaelmassoni.tessera.weather.engine.WindUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/** Plain HTTPS GETs to Open-Meteo. No cookies, no identifiers. */
class WeatherApi {
    suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "Tessera Weather")
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}

/** Saved places, in order, as JSON in app storage. */
class PlaceStore(
    private val file: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Place.serializer())

    fun load(): List<Place> = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    fun save(places: List<Place>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, places))
        tmp.renameTo(file)
    }
}

/** The last forecast response per place; the file's time is when it was fetched. */
class ForecastCache(
    private val dir: File,
) {
    data class Entry(
        val body: String,
        val fetchedAt: Long,
    )

    private fun file(id: Long) = File(dir, "$id.json")

    fun read(id: Long): Entry? =
        file(id).takeIf { it.exists() }?.let { f -> runCatching { Entry(f.readText(), f.lastModified()) }.getOrNull() }

    fun write(
        id: Long,
        body: String,
        at: Long,
    ) {
        dir.mkdirs()
        val tmp = File(dir, "$id.tmp")
        tmp.writeText(body)
        tmp.setLastModified(at)
        tmp.renameTo(file(id))
    }

    fun delete(id: Long) {
        file(id).delete()
    }
}

class WeatherSettings(
    private val prefs: SharedPreferences,
) {
    var units: Units
        get() {
            val default = Units.forCountry(Locale.getDefault().country)
            return Units(
                temperature = enumOr(prefs.getString(KEY_TEMPERATURE, null), default.temperature),
                wind = enumOr(prefs.getString(KEY_WIND, null), default.wind),
                precipitation = enumOr(prefs.getString(KEY_PRECIPITATION, null), default.precipitation),
            )
        }
        set(value) =
            prefs.edit {
                putString(KEY_TEMPERATURE, value.temperature.name)
                putString(KEY_WIND, value.wind.name)
                putString(KEY_PRECIPITATION, value.precipitation.name)
            }

    /** Null until the person has chosen (the welcome screen asks). */
    var useLocation: Boolean?
        get() = if (prefs.contains(KEY_LOCATION)) prefs.getBoolean(KEY_LOCATION, false) else null
        set(value) = prefs.edit { if (value == null) remove(KEY_LOCATION) else putBoolean(KEY_LOCATION, value) }

    /** The place last shown, to reopen on it. */
    var lastPlace: Long
        get() = prefs.getLong(KEY_LAST, Place.HERE)
        set(value) = prefs.edit { putLong(KEY_LAST, value) }

    /** The last fix, so the current-location page can show its cached forecast straight away. */
    var lastFix: Place?
        get() =
            if (prefs.contains(KEY_FIX_LAT)) {
                Place(
                    Place.HERE,
                    prefs.getString(KEY_FIX_NAME, "").orEmpty(),
                    latitude = prefs.getFloat(KEY_FIX_LAT, 0f).toDouble(),
                    longitude = prefs.getFloat(KEY_FIX_LON, 0f).toDouble(),
                )
            } else {
                null
            }
        set(value) =
            prefs.edit {
                if (value == null) {
                    remove(KEY_FIX_LAT)
                    remove(KEY_FIX_LON)
                    remove(KEY_FIX_NAME)
                } else {
                    putFloat(KEY_FIX_LAT, value.latitude.toFloat())
                    putFloat(KEY_FIX_LON, value.longitude.toFloat())
                    putString(KEY_FIX_NAME, value.name)
                }
            }

    private inline fun <reified E : Enum<E>> enumOr(
        name: String?,
        default: E,
    ): E = enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_TEMPERATURE = "temperature_unit"
        const val KEY_WIND = "wind_unit"
        const val KEY_PRECIPITATION = "precipitation_unit"
        const val KEY_LOCATION = "use_location"
        const val KEY_LAST = "last_place"
        const val KEY_FIX_LAT = "fix_lat"
        const val KEY_FIX_LON = "fix_lon"
        const val KEY_FIX_NAME = "fix_name"
    }
}

fun Context.weatherSettings() = WeatherSettings(getSharedPreferences("settings", Context.MODE_PRIVATE))

/** Approximate location from the platform (no Play Services), and a name for it when there's a geocoder. */
class Locator(
    private val context: Context,
) {
    fun allowed(): Boolean = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun locate(): Location? {
        if (!allowed()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers =
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        for (provider in providers) {
            val fix =
                withTimeoutOrNull(10_000) {
                    suspendCancellableCoroutine { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                    }
                }
            if (fix != null) return fix
        }
        return providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** "Fitzroy" or "Melbourne", or null when the phone has no geocoder (common without Play Services). */
    suspend fun name(location: Location): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(5_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                cont.resume(addresses.firstOrNull()?.let { it.subLocality ?: it.locality ?: it.subAdminArea })
                            }

                            override fun onError(errorMessage: String?) = cont.resume(null)
                        },
                    )
                }
            } else {
                null
            }
        }
    }
}
