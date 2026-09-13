package com.buddy.app.features.matching

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiUserRef
import com.buddy.app.features.matching.data.MatchingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Flujo "Buscar un buddy" — espejo de ContactarBuddyView (iOS):
 * crear solicitud → SSE como señal primaria + poll de recuperación cada 30s
 * → matched. Cancelable en todo momento.
 */
@HiltViewModel
class MatchingViewModel @Inject constructor(
    private val repo: MatchingRepository,
    private val tripRepo: com.buddy.app.features.trips.data.TripRepository,
    private val locationProvider: com.buddy.app.core.location.LocationProvider,
) : ViewModel() {

    sealed interface SearchState {
        data object Idle : SearchState
        data class Searching(val requestId: String, val category: String? = null, val position: Int? = null, val total: Int? = null) : SearchState
        data class Matched(val matchId: String, val buddy: ApiUserRef?, val category: String? = null) : SearchState
        data class Failed(val message: String) : SearchState
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var pollJob: Job? = null

    /**
     * @param ensureJourney true (default) = contexto "Mi viaje": asegurar el
     * trip ANTES de pedir ayuda, igual que siempre — así el journey aparece en
     * "Tu trip" y el backend lo promueve a active. false = contexto "Ubicación
     * actual": NO crear ni reusar journey — la solicitud viaja sin journey_id,
     * igual que el flujo pioneer-less de iOS. El trip existente (si lo hay)
     * queda intacto; no se introduce un segundo journey activo.
     */
    fun findBuddy(destinationId: String, category: String, description: String? = null, ensureJourney: Boolean = true) {
        if (_state.value is SearchState.Searching) return
        viewModelScope.launch {
            try {
                val journeyId = if (ensureJourney) {
                    runCatching { tripRepo.ensureActiveTrip(destinationId).id }
                        .onFailure { Log.w(TAG, "ensureActiveTrip falló — request sin journey", it) }
                        .getOrNull()
                } else null
                startRequest(destinationId, category, description, journeyId)
            } catch (e: Exception) {
                Log.e(TAG, "createHelpRequest failed", e)
                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
            }
        }
    }

    /** Aviso del flujo pioneer — espejo de pioneerConfirmation (iOS). */
    private val _pioneerConfirmation = MutableStateFlow<String?>(null)
    val pioneerConfirmation: StateFlow<String?> = _pioneerConfirmation.asStateFlow()

    /** true mientras el flujo pioneer crea trip + solicitud — alimenta el loader del Home. */
    private val _isPioneerRegistering = MutableStateFlow(false)
    val isPioneerRegistering: StateFlow<Boolean> = _isPioneerRegistering.asStateFlow()

    /**
     * Flujo pioneer — espejo EXACTO de pioneerHelpFlow (iOS): sin buddies no
     * hay nada que buscar, así que NO abre el modal de búsqueda. Crea el trip
     * (curado o GPS-only) + la solicitud en silencio, muestra la confirmación
     * y el caller navega a "Tu trip".
     */
    fun pioneerRegister(
        destinationId: String?,
        lat: Double?,
        lng: Double?,
        category: String,
        cityName: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            _isPioneerRegistering.value = true
            try {
                val journey = when {
                    destinationId != null -> tripRepo.ensureActiveTrip(destinationId)
                    lat != null && lng != null -> tripRepo.ensureActiveTripForGps(lat, lng)
                    else -> return@launch
                }
                try {
                    repo.createHelpRequest(
                        destinationId = journey.destination?.id ?: journey.destinationId,
                        category = category,
                        journeyId = journey.id,
                        lat = lat, lng = lng,
                    )
                } catch (e: com.buddy.app.features.matching.data.ActiveRequestExists) {
                    // Solicitud huérfana previa: cancelarla y reintentar una vez —
                    // el flujo pioneer promete registro en silencio, sin modal.
                    val orphan = e.requestId ?: throw e
                    Log.d(TAG, "pioneer 409 → cancelando huérfana ${orphan.take(8)} y reintentando")
                    repo.cancelRequest(orphan)
                    repo.createHelpRequest(
                        destinationId = journey.destination?.id ?: journey.destinationId,
                        category = category,
                        journeyId = journey.id,
                        lat = lat, lng = lng,
                    )
                }
                val city = cityName ?: "tu zona"
                _pioneerConfirmation.value =
                    "Registramos tu solicitud en $city. Te avisaremos cuando haya un buddy disponible."
                delay(500)   // igual que el asyncAfter(0.5) de iOS
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "pioneer flow failed", e)
                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
            } finally {
                _isPioneerRegistering.value = false
            }
        }
    }

    fun clearPioneerConfirmation() { _pioneerConfirmation.value = null }

    private fun startRequest(destinationId: String?, category: String, description: String?, journeyId: String?) {
        viewModelScope.launch {
            // Esperar un cancel en vuelo: sin esto el POST puede llegar al
            // servidor ANTES que el DELETE del cancel anterior y devuelve 409
            // sobre una solicitud que está a punto de morir (la carrera del
            // "minimizo y vuelvo a pedir de inmediato").
            cancelJob?.join()
            // Sin journey (consulta desde el Home) el punto consultado es donde
            // está el viajero: el backend busca buddies que CUBREN ese punto.
            // Con journey no: puede ser un viaje a otra ciudad.
            val punto = if (journeyId == null) runCatching { locationProvider.currentLocation() }.getOrNull() else null
            Log.d(TAG, "startRequest punto=${punto?.let { "(${it.lat},${it.lng})" } ?: "sin punto"} journey=${journeyId?.take(8)}")
            var retried = false
            while (true) {
                try {
                    val request = repo.createHelpRequest(destinationId, category, description, journeyId,
                        lat = punto?.lat, lng = punto?.lng)
                    _state.value = SearchState.Searching(request.id, category)
                    startSse(request.id)
                    startRecoveryPoll(request.id)
                    return@launch
                } catch (e: com.buddy.app.features.matching.data.ActiveRequestExists) {
                    val existingId = e.requestId
                    if (existingId == null) {
                        _state.value = SearchState.Failed("Ya tienes una solicitud activa. Inténtalo en un momento.")
                        return@launch
                    }
                    // ¿La huérfana sigue viva de verdad? Solo reanudar si busca.
                    val st = runCatching { repo.status(existingId).status }.getOrNull()
                    when {
                        st == "matched" -> { transitionToMatched(); return@launch }
                        st == "searching" || st == null -> {
                            Log.d(TAG, "409 → resuming request ${existingId.take(8)}")
                            _state.value = SearchState.Searching(existingId, category)
                            startSse(existingId)
                            startRecoveryPoll(existingId)
                            return@launch
                        }
                        else -> {
                            // cancelled/failed: la huérfana ya murió — asegurar
                            // su desactivación y crear una nueva (un solo retry)
                            if (retried) {
                                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
                                return@launch
                            }
                            retried = true
                            runCatching { repo.cancelRequest(existingId) }
                        }
                    }
                } catch (e: retrofit2.HttpException) {
                    Log.e(TAG, "createHelpRequest HTTP ${e.code()}", e)
                    _state.value = SearchState.Failed(
                        if (e.code() == 429) "Demasiadas solicitudes en poco tiempo. Espera un minuto e inténtalo de nuevo."
                        else "No pudimos enviar tu solicitud. Inténtalo de nuevo.",
                    )
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "createHelpRequest failed", e)
                    _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
                    return@launch
                }
            }
        }
    }

    private fun startSse(requestId: String) {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            repo.requestStream(requestId).collect { event ->
                if (event.event == "matched") transitionToMatched()
                else checkStatus(requestId)
            }
        }
    }

    private fun startRecoveryPoll(requestId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (_state.value is SearchState.Searching) {
                delay(30_000)
                checkStatus(requestId)
            }
        }
    }

    private suspend fun checkStatus(requestId: String) {
        val current = _state.value as? SearchState.Searching ?: return
        runCatching { repo.status(requestId) }.onSuccess { s ->
            when (s.status) {
                "matched" -> transitionToMatched()
                "searching" -> _state.value = SearchState.Searching(requestId, current.category, s.position, s.total)
                "failed" -> fail("Ningún buddy está disponible ahora. Inténtalo más tarde.")
                "cancelled" -> _state.value = SearchState.Idle
            }
        }
    }

    private suspend fun transitionToMatched() {
        val category = (_state.value as? SearchState.Searching)?.category
        val active = runCatching { repo.matches() }.getOrNull()
            ?.firstOrNull { it.status in listOf("pending", "accepted", "active") } ?: return
        stopStreams()
        _state.value = SearchState.Matched(active.id, active.buddy, category)
    }

    private fun fail(message: String) {
        stopStreams()
        _state.value = SearchState.Failed(message)
    }

    /** Cancel en vuelo — startRequest lo espera antes de crear otra solicitud. */
    private var cancelJob: Job? = null

    fun cancelSearch() {
        val searching = _state.value as? SearchState.Searching ?: return
        stopStreams()
        cancelJob = viewModelScope.launch { runCatching { repo.cancelRequest(searching.requestId) } }
        _state.value = SearchState.Idle
    }

    fun dismiss() { _state.value = SearchState.Idle }

    private fun stopStreams() { sseJob?.cancel(); pollJob?.cancel() }

    companion object { private const val TAG = "MatchingVM" }
}
