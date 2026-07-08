package com.buddy.app.features.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.features.profile.data.ApiBuddyMe
import com.buddy.app.features.profile.data.ApiUser
import com.buddy.app.features.profile.data.ApiUserSticker
import com.buddy.app.features.profile.data.BioBody
import com.buddy.app.features.profile.data.ProfileApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Espejo del estado de YoView (iOS): perfil, bio, stickers, trips, buddy. */
@HiltViewModel
class YoViewModel @Inject constructor(
    private val api: ProfileApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val user: ApiUser? = null,
        val journeys: List<ApiJourney> = emptyList(),
        val stickers: List<ApiUserSticker> = emptyList(),
        val buddyMe: ApiBuddyMe? = null,
        val isSavingBio: Boolean = false,
        val bioSaveFailed: Boolean = false,
        val isBecomingBuddy: Boolean = false,
        val isDeletingAccount: Boolean = false,
    ) {
        /** "N trips · N stickers" — igual que metaLine (iOS). */
        val metaLine: String
            get() {
                val trips = if (journeys.size == 1) "1 trip" else "${journeys.size} trips"
                val st = when {
                    stickers.isEmpty() -> null
                    stickers.size == 1 -> "1 sticker"
                    else -> "${stickers.size} stickers"
                }
                return listOfNotNull(trips, st).joinToString(" · ")
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val myId = sessionStore.current()?.travelerId ?: run {
                    _state.update { it.copy(isLoading = false) }; return@launch
                }
                coroutineScope {
                    val user = async { runCatching { api.me() }.getOrNull() }
                    val trips = async { runCatching { api.trips(myId).items }.getOrDefault(emptyList()) }
                    val stickers = async { runCatching { api.stickers(myId) }.getOrDefault(emptyList()) }
                    // buddy/me responde 403 para no verificados — se trata como "no buddy"
                    val buddy = async { runCatching { api.buddyMe() }.getOrNull() }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            user = user.await(),
                            journeys = trips.await(),
                            stickers = stickers.await(),
                            buddyMe = buddy.await(),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveBio(bio: String, onDone: () -> Unit) {
        val myId = _state.value.user?.id ?: return
        _state.update { it.copy(isSavingBio = true, bioSaveFailed = false) }
        viewModelScope.launch {
            runCatching { api.updateBio(myId, BioBody(bio)) }
                .onSuccess {
                    _state.update { s -> s.copy(isSavingBio = false, user = s.user?.copy(bio = bio)) }
                    onDone()
                }
                .onFailure {
                    Log.e(TAG, "saveBio failed", it)
                    _state.update { s -> s.copy(isSavingBio = false, bioSaveFailed = true) }
                }
        }
    }

    fun dismissBioError() = _state.update { it.copy(bioSaveFailed = false) }

    /** Crea el perfil de buddy y refresca la sección — como becomeBuddy (iOS). */
    fun becomeBuddy() {
        if (_state.value.isBecomingBuddy) return
        _state.update { it.copy(isBecomingBuddy = true) }
        viewModelScope.launch {
            runCatching { api.becomeBuddy() }
                .onSuccess { result -> _state.update { it.copy(isBecomingBuddy = false, buddyMe = result) } }
                .onFailure {
                    Log.e(TAG, "becomeBuddy failed", it)
                    _state.update { s -> s.copy(isBecomingBuddy = false) }
                }
        }
    }

    fun deleteAccount(onDone: () -> Unit) {
        if (_state.value.isDeletingAccount) return
        _state.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            runCatching { api.deleteAccount() }
                .onSuccess { onDone() }
                .onFailure { Log.e(TAG, "deleteAccount failed", it) }
            _state.update { it.copy(isDeletingAccount = false) }
        }
    }

    companion object { private const val TAG = "YoVM" }
}
