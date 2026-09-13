package com.buddy.app.features.home.data

import android.content.Context
import android.util.Log
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.location.DistanceResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dueño único de la lista de spots del Home. Espejo de SpotsStore (iOS).
 *
 * 1. Una sola petición: las concurrentes con coordenadas equivalentes (<150 m)
 *    se enganchan a la que está en vuelo.
 * 2. Un fallo conserva la lista: antes el ViewModel la vaciaba ANTES de pedir,
 *    así que un timeout dejaba el carrusel sin nada.
 * 3. Cache en disco: la última lista buena se pinta al instante.
 */
@Singleton
class SpotsRepository @Inject constructor(
    private val api: HomeApi,
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile get() = File(context.cacheDir, "spots-cache.json")

    private val _spots = MutableStateFlow(loadCache())
    val spots: StateFlow<List<ApiPlaceCard>> = _spots.asStateFlow()

    /** Lugar más cercano con margen, para que solo una card diga "Estás aquí". */
    private val _nearestId = MutableStateFlow<String?>(null)
    val nearestId: StateFlow<String?> = _nearestId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var inFlight: Deferred<Unit>? = null
    private var inFlightLat: Double? = null
    private var inFlightLng: Double? = null
    /** Una respuesta de una generación anterior se descarta. */
    @Volatile private var generation = 0

    init { Log.d(TAG, "🗂️ [spots] cache al arrancar → ${_spots.value.size} spot(s)") }

    suspend fun refresh(lat: Double?, lng: Double?, reason: String) {
        val job = mutex.withLock {
            val current = inFlight
            if (current != null && current.isActive && equivalent(inFlightLat, inFlightLng, lat, lng)) {
                Log.d(TAG, "🗂️ [spots] $reason: ya hay una petición equivalente en vuelo — me engancho")
                current
            } else {
                val gen = ++generation
                inFlightLat = lat
                inFlightLng = lng
                // <Unit> explícito: la última expresión del try es un Log (Int) y
                // sin esto el async se infería Deferred<Int>.
                scope.async<Unit> {
                    _isLoading.value = true
                    try {
                        val cards = api.placeShares(limit = 12, lat = lat, lng = lng).items
                        if (gen != generation) {
                            Log.d(TAG, "🗂️ [spots] $reason: respuesta superada por una más nueva — descartada")
                            return@async
                        }
                        _spots.value = cards
                        saveCache(cards)
                        Log.d(TAG, "🗂️ [spots] $reason: ${cards.size} spot(s) — guardados en cache")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "🗂️ [spots] $reason: falló (${e.message}) — conservo ${_spots.value.size} spot(s)")
                    } finally {
                        if (gen == generation) _isLoading.value = false
                    }
                }.also { inFlight = it }
            }
        }
        job.await()
    }

    /** Reordena y recalcula el más cercano con una ubicación YA filtrada. */
    fun reorder(lat: Double, lng: Double) {
        val actuales = _spots.value
        val ordenados = DistanceResolver.stableOrder(actuales, lat, lng)
        if (ordenados.map { it.id } != actuales.map { it.id }) {
            Log.d(TAG, "🗂️ [spots] nuevo orden: ${ordenados.take(3).joinToString(" · ") { it.name }}")
            _spots.value = ordenados
        }
        val candidatos = ordenados.mapNotNull { card ->
            DistanceResolver.distance(lat, lng, card)?.let { card.id to it }
        }
        val nuevo = DistanceResolver.nearest(_nearestId.value, candidatos)
        if (nuevo != _nearestId.value) {
            Log.d(TAG, "🗂️ [spots] más cercano: ${ordenados.firstOrNull { it.id == nuevo }?.name ?: "ninguno"}")
            _nearestId.value = nuevo
        }
    }

    private fun saveCache(cards: List<ApiPlaceCard>) {
        runCatching { cacheFile.writeText(json.encodeToString(ListSerializer(ApiPlaceCard.serializer()), cards)) }
            .onFailure { Log.w(TAG, "no se pudo guardar el cache de spots", it) }
    }

    private fun loadCache(): List<ApiPlaceCard> = runCatching {
        if (!cacheFile.exists()) emptyList()
        else json.decodeFromString(ListSerializer(ApiPlaceCard.serializer()), cacheFile.readText())
    }.getOrDefault(emptyList())

    private fun equivalent(aLat: Double?, aLng: Double?, bLat: Double?, bLng: Double?): Boolean = when {
        aLat == null && bLat == null -> true
        aLat != null && aLng != null && bLat != null && bLng != null ->
            DistanceResolver.meters(aLat, aLng, bLat, bLng) < COALESCE_METERS
        else -> false
    }

    companion object {
        private const val TAG = "SpotsRepository"
        private const val COALESCE_METERS = 150.0
    }
}
