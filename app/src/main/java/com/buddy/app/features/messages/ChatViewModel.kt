package com.buddy.app.features.messages

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.SessionStore
import com.buddy.app.features.matching.data.ApiMessage
import com.buddy.app.features.messages.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chat de un match — historial + SSE de mensajes nuevos + envío optimista. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val store: SessionStore,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val messages: List<ApiMessage> = emptyList(),
        val myTravelerId: String? = null,
        val isSending: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var matchId: String? = null

    fun open(matchId: String) {
        if (this.matchId == matchId) return
        this.matchId = matchId
        viewModelScope.launch {
            _state.update { it.copy(myTravelerId = store.current()?.travelerId) }
            refresh()
            repo.markRead(matchId)
        }
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            repo.messageStream(matchId).collect {
                refresh()
                repo.markRead(matchId)
            }
        }
    }

    private suspend fun refresh() {
        val id = matchId ?: return
        runCatching { repo.messages(id) }
            .onSuccess { msgs ->
                _state.update { it.copy(isLoading = false, messages = msgs.sortedBy { m -> m.createdAt ?: "" }) }
            }
            .onFailure { Log.e(TAG, "messages failed", it) }
    }

    fun send(content: String) {
        val id = matchId ?: return
        val text = content.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            runCatching { repo.send(id, text) }
                .onSuccess { refresh() }
                .onFailure { Log.e(TAG, "send failed", it) }
            _state.update { it.copy(isSending = false) }
        }
    }

    companion object { private const val TAG = "ChatVM" }
}
