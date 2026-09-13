package com.buddy.app.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class UserLocation(val lat: Double, val lng: Double)

/** Un fix del GPS con su precisión. [accuracy] nulo si el fix no la trae. */
data class LocationFix(val lat: Double, val lng: Double, val accuracy: Float?, val speed: Float?)

/**
 * Equivalente de LocationService (iOS) — una lectura puntual de ubicación
 * para resolver el contexto del Home. Usa FusedLocationProvider
 * (la convención Android; CLLocationManager no tiene equivalente directo).
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): UserLocation? {
        if (!hasPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    cont.resume(loc?.let { UserLocation(it.latitude, it.longitude) })
                }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    /**
     * Fixes continuos mientras se recolecte. Antes solo había lecturas
     * puntuales, así que el Home no podía seguir al viajero: la ubicación se
     * leía una vez al cargar y ya.
     *
     * Un fix cada ~2 s o cada 10 m. El filtrado de precisión NO va aquí: lo
     * decide LocationFilter, para que otras pantallas puedan usar el fix crudo.
     */
    @SuppressLint("MissingPermission")
    fun updates(): Flow<LocationFix> = callbackFlow {
        if (!hasPermission()) { close(); return@callbackFlow }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateDistanceMeters(10f)
            .build()
        var anterior: android.location.Location? = null
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val movido = anterior?.let { "movido ${loc.distanceTo(it).toInt()}m en ${(loc.time - it.time) / 1000}s" } ?: "primer fix"
                Log.d(TAG, "📡 [gps] fix ${"%.6f".format(loc.latitude)},${"%.6f".format(loc.longitude)} " +
                    "±${if (loc.hasAccuracy()) loc.accuracy.toInt() else -1}m · $movido")
                anterior = loc
                trySend(LocationFix(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                    speed = if (loc.hasSpeed()) loc.speed else null,
                ))
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    companion object { private const val TAG = "LocationProvider" }
}
