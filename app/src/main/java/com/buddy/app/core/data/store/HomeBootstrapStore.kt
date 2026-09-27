package com.buddy.app.core.data.store

import android.util.Log
import com.buddy.app.BuildConfig
import com.buddy.app.features.home.data.HomeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Espejo del actor HomeBootstrap (iOS): un solo viaje a GET /home/bootstrap
 * cuando llega el primer fix GPS del Home, repartido después a cada
 * repositorio que ya pedía su parte por su cuenta (journeys, matches,
 * placeShares, myOffers, forBuddy, myRequest, destinations, recentHelp…).
 *
 * No es un cache general: cubre el arranque, vive 8 s y cualquier respuesta
 * servida desde aquí es la misma que el endpoint individual habría dado —
 * el backend arma /home/bootstrap llamando a esos mismos endpoints por dentro.
 */
@Singleton
class HomeBootstrapStore @Inject constructor(
    private val api: HomeApi,
) {
    private data class Entrada(val json: JsonElement, val hasta: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var entradas: Map<String, Entrada> = emptyMap()
    private var lat: Double? = null
    private var lng: Double? = null
    private var pendiente: Deferred<Unit>? = null

    /** Arranca el viaje. Vuelve en cuanto queda registrado — no espera la
     *  respuesta — así cualquier petición que llegue detrás ya lo ve en curso. */
    fun preparar(lat: Double?, lng: Double?) {
        synchronized(lock) {
            if (pendiente?.isActive == true) return
            // Mismo punto (~200 m): el GPS y loadData difieren en decimales
            // sueltos, y eso no justifica otro viaje de red.
            val vivo = entradas.values.firstOrNull()?.hasta ?: 0L
            if (vivo > System.currentTimeMillis() && cerca(this.lat, lat) && cerca(this.lng, lng)) return
            this.lat = lat
            this.lng = lng
            entradas = emptyMap()
            pendiente = scope.async {
                runCatching { fetchAndStore(lat, lng) }
                    .onFailure { dlog("sin respuesta útil (${it.message}) — cada petición sale sola") }
            }
        }
    }

    private suspend fun fetchAndStore(lat: Double?, lng: Double?) {
        val t0 = System.currentTimeMillis()
        // Igual que iOS: si el bootstrap tarda demasiado, mejor que cada
        // petición individual salga sola a que el Home se quede esperando.
        val envelope = withTimeoutOrNull(ESPERA_MAXIMA_MS) { api.bootstrap(lat, lng, radiusKm = 15) } ?: return
        val hasta = System.currentTimeMillis() + VIDA_MS
        val nuevas = mutableMapOf<String, Entrada>()
        for ((nombre, valor) in envelope.parts) {
            val obj = valor as? JsonObject ?: continue
            val status = obj["status"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull } ?: continue
            if (status !in 200..299) continue
            val data = obj["data"] ?: continue
            if (data is JsonNull) continue
            nuevas[nombre] = Entrada(data, hasta)
        }
        synchronized(lock) { entradas = nuevas }
        dlog("⚡️ listo en ${System.currentTimeMillis() - t0}ms — partes: ${nuevas.keys.sorted().joinToString(", ")}")
    }

    /**
     * Los datos de [parte] si el bootstrap los trae (esperándolo si está en
     * curso), o null para que quien llama pida el endpoint como antes.
     * [paraLat]/[paraLng] solo importan para las partes que dependen de la
     * ubicación (placeShares, recentHelpNearby): si el punto pedido no
     * coincide con el del bootstrap, no se sirve desde aquí.
     */
    suspend fun datos(parte: String, paraLat: Double? = null, paraLng: Double? = null): JsonElement? {
        pendiente?.let { d -> if (d.isActive) runCatching { d.await() } }
        val e = synchronized(lock) { entradas[parte] } ?: return null
        if (e.hasta < System.currentTimeMillis()) return null
        if (parte == "placeShares" || parte == "recentHelpNearby") {
            if (!cerca(lat, paraLat) || !cerca(lng, paraLng)) return null
        }
        return e.json
    }

    fun <T> decodificar(elemento: JsonElement, strategy: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(strategy, elemento)

    /**
     * POST/PATCH/DELETE que en realidad solo LEEN (resolver una ubicación no
     * cambia nada de lo que trae el bootstrap): no invalidan. Espejo de
     * escriturasFalsas en APIClient.swift.
     */
    fun invalidar(path: String) {
        synchronized(lock) {
            if (entradas.isEmpty()) return
            if (ESCRITURAS_FALSAS.any { path.contains(it) }) return
            entradas = emptyMap()
        }
    }

    private fun cerca(a: Double?, b: Double?): Boolean = when {
        a == null && b == null -> true
        a != null && b != null -> kotlin.math.abs(a - b) < TOLERANCIA_GRADOS
        else -> false
    }

    private fun dlog(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[bootstrap] $msg")
    }

    companion object {
        private const val TAG = "HomeBootstrapStore"
        private const val VIDA_MS = 8_000L
        private const val ESPERA_MAXIMA_MS = 6_000L
        private const val TOLERANCIA_GRADOS = 0.002
        private val ESCRITURAS_FALSAS = listOf("/location/", "/places/resolve", "/notifications/")
    }
}
