package com.buddy.app.features.conexiones

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.network.SseClient
import com.buddy.app.features.matching.data.ApiBuddyOffer
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.MatchingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Conexiones — matches (chats) + solicitudes entrantes para buddies.
 * El stream global /stream refresca la lista cuando llega actividad
 * (mismo patrón que ChatStore en ConexionesView de iOS).
 */
@HiltViewModel
class ConexionesViewModel @Inject constructor(
    private val repo: MatchingRepository,
    private val sse: SseClient,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val matches: List<ApiMatch> = emptyList(),
        val solicitudes: List<ApiBuddyOffer> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sse.events("stream").collect { load() }   // cualquier evento → refresh
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val matches = repo.matches()
                val offers = runCatching { repo.myOffers() }.getOrDefault(emptyList())
                _state.update { it.copy(isLoading = false, matches = matches, solicitudes = offers) }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun acceptSolicitud(requestId: String) {
        viewModelScope.launch {
            runCatching { repo.acceptOffer(requestId) }
                .onSuccess { load() }
                .onFailure { Log.e(TAG, "accept failed", it) }
        }
    }

    fun declineSolicitud(requestId: String) {
        viewModelScope.launch {
            runCatching { repo.declineOffer(requestId) }.also { load() }
        }
    }

    companion object { private const val TAG = "ConexionesVM" }
}
