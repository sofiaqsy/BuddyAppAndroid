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
) : ViewModel() {

    sealed interface SearchState {
        data object Idle : SearchState
        data class Searching(val requestId: String, val position: Int? = null, val total: Int? = null) : SearchState
        data class Matched(val matchId: String, val buddy: ApiUserRef?) : SearchState
        data class Failed(val message: String) : SearchState
    }

    private val _state = MutableStateFlow<SearchState>(SearchState.Idle)
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var pollJob: Job? = null

    fun findBuddy(destinationId: String, category: String, description: String? = null) {
        if (_state.value is SearchState.Searching) return
        viewModelScope.launch {
            try {
                // Igual que iOS: asegurar el trip ANTES de pedir ayuda — así el
                // journey aparece en "Tu trip" y el backend lo promueve a active.
                val journeyId = runCatching { tripRepo.ensureActiveTrip(destinationId).id }
                    .onFailure { Log.w(TAG, "ensureActiveTrip falló — request sin journey", it) }
                    .getOrNull()
                startRequest(destinationId, category, description, journeyId)
            } catch (e: Exception) {
                Log.e(TAG, "createHelpRequest failed", e)
                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
            }
        }
    }

    /**
     * Flujo pioneer por GPS — espejo de pioneerHelpFlow (iOS): sin destino
     * curado, resuelve/crea el Place con las coords, crea el trip GPS-only
     * y lanza la solicitud con el journey.
     */
    fun findBuddyPioneer(lat: Double, lng: Double, category: String, description: String? = null) {
        if (_state.value is SearchState.Searching) return
        viewModelScope.launch {
            try {
                val journey = tripRepo.ensureActiveTripForGps(lat, lng)
                startRequest(
                    destinationId = journey.destination?.id ?: journey.destinationId,
                    category = category,
                    description = description,
                    journeyId = journey.id,
                )
            } catch (e: Exception) {
                Log.e(TAG, "pioneer flow failed", e)
                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
            }
        }
    }

    private fun startRequest(destinationId: String?, category: String, description: String?, journeyId: String?) {
        viewModelScope.launch {
            try {
                val request = repo.createHelpRequest(destinationId, category, description, journeyId)
                _state.value = SearchState.Searching(request.id)
                startSse(request.id)
                startRecoveryPoll(request.id)
            } catch (e: Exception) {
                Log.e(TAG, "createHelpRequest failed", e)
                _state.value = SearchState.Failed("No pudimos enviar tu solicitud. Inténtalo de nuevo.")
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
                "searching" -> _state.value = current.copy(position = s.position, total = s.total)
                "failed" -> fail("Ningún buddy está disponible ahora. Inténtalo más tarde.")
                "cancelled" -> _state.value = SearchState.Idle
            }
        }
    }

    private suspend fun transitionToMatched() {
        val active = runCatching { repo.matches() }.getOrNull()
            ?.firstOrNull { it.status in listOf("pending", "accepted", "active") } ?: return
        stopStreams()
        _state.value = SearchState.Matched(active.id, active.buddy)
    }

    private fun fail(message: String) {
        stopStreams()
        _state.value = SearchState.Failed(message)
    }

    fun cancelSearch() {
        val searching = _state.value as? SearchState.Searching ?: return
        stopStreams()
        viewModelScope.launch { runCatching { repo.cancelRequest(searching.requestId) } }
        _state.value = SearchState.Idle
    }

    fun dismiss() { _state.value = SearchState.Idle }

    private fun stopStreams() { sseJob?.cancel(); pollJob?.cancel() }

    companion object { private const val TAG = "MatchingVM" }
}
