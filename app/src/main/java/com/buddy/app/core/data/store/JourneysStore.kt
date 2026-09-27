package com.buddy.app.core.data.store

import android.content.Context
import android.util.Log
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.home.data.HomeApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dueño único de /travelers/me/journeys — Home y Trips lo pedían cada uno por
 * su cuenta. Ventana de 8 s, igual que iOS.
 *
 * Dos capas de cache además de la ventana de frescura:
 * - HomeBootstrapStore: si el Home ya disparó /home/bootstrap para este
 *   arranque, esta parte se sirve de ahí en vez de pedir el endpoint aparte.
 * - Disco: la última lista buena, por traveler — espejo de guardarEnDisco/
 *   desdeDisco (iOS). Deja pintar "pista viaje" al instante en un arranque
 *   frío, antes de que responda cualquier red.
 */
@Singleton
class JourneysStore @Inject constructor(
    private val api: HomeApi,
    private val sessionStore: SessionStore,
    private val bootstrap: HomeBootstrapStore,
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ApiJourney.serializer())
    private val shared = SharedFetch("journeys", 8_000, sessionStore) { fetchJourneys() }

    suspend fun load(trigger: String): List<ApiJourney> = shared.load(trigger)
    suspend fun refresh(trigger: String): List<ApiJourney> = shared.refresh(trigger)

    private suspend fun fetchJourneys(): List<ApiJourney> {
        val desdeBootstrap = bootstrap.datos("journeys")
        val journeys = if (desdeBootstrap != null) {
            Log.d(TAG, "journeys ← /home/bootstrap (sin red aparte)")
            bootstrap.decodificar(desdeBootstrap, serializer)
        } else {
            api.myJourneys()
        }
        guardarEnDisco(journeys)
        return journeys
    }

    // ── Cache en disco (solo para el arranque) ──────────────────────────

    private fun archivo(travelerId: String): File = File(context.filesDir, "journeys-$travelerId.json")

    private suspend fun guardarEnDisco(journeys: List<ApiJourney>) {
        val tid = sessionStore.current()?.travelerId ?: return
        runCatching {
            archivo(tid).writeText(json.encodeToString(serializer, journeys))
        }.onFailure { Log.w(TAG, "no se pudo guardar el cache de journeys", it) }
    }

    /**
     * Los journeys de la última sesión de ESTE traveler, o null. Solo debe
     * leerse antes de que responda la red (arranque frío); el dato puede
     * estar viejo y la red siempre lo corrige.
     */
    suspend fun desdeDisco(): List<ApiJourney>? {
        val tid = sessionStore.current()?.travelerId ?: return null
        return runCatching {
            val f = archivo(tid)
            if (!f.exists()) return null
            json.decodeFromString(serializer, f.readText())
        }.getOrNull()
    }

    /** Logout / cambio de cuenta: no debe quedar rastro de los journeys del
     *  traveler anterior — se borran todos los archivos, no solo el actual
     *  (en logout la sesión puede estar ya limpia cuando esto corre). */
    fun clear() {
        runCatching {
            context.filesDir.listFiles { f -> f.name.startsWith("journeys-") && f.name.endsWith(".json") }
                ?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "no se pudo limpiar el cache de journeys", it) }
    }

    companion object {
        private const val TAG = "JourneysStore"
    }
}
