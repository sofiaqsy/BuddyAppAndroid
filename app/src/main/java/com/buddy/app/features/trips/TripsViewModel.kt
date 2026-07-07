package com.buddy.app.features.trips

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.features.authentication.data.TravelerRepository
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.trips.data.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val api: HomeApi,
    private val tripRepo: TripRepository,
    private val travelerRepo: TravelerRepository,
    private val matchingRepo: com.buddy.app.features.matching.data.MatchingRepository,
) : ViewModel() {

    data class TripsState(
        val isLoading: Boolean = true,
        val journeys: List<ApiJourney> = emptyList(),
        val showRegisterSheet: Boolean = false,
        val searchResults: List<ApiPlaceResult> = emptyList(),
        val isCreating: Boolean = false,
        val activeBuddyName: String? = null,
        val activeBuddyAvatarUrl: String? = null,
    )

    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
                val journeys = tripRepo.myJourneys()
                // Buddy activo para la fila "¿Una duda en X?" (como iOS activeMatch)
                val activeMatch = runCatching { matchingRepo.matches() }.getOrDefault(emptyList())
                    .firstOrNull { it.status in listOf("pending", "accepted", "active") }
                _state.update {
                    it.copy(
                        isLoading = false,
                        journeys = journeys,
                        activeBuddyName = activeMatch?.buddy?.fullName?.split(" ")?.firstOrNull(),
                        activeBuddyAvatarUrl = activeMatch?.buddy?.avatarUrl,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun openRegister() = _state.update { it.copy(showRegisterSheet = true, searchResults = emptyList()) }
    fun closeRegister() = _state.update { it.copy(showRegisterSheet = false) }

    /** Debounce 300ms — mismo comportamiento que el triggerSearch de iOS. */
    fun search(query: String) {
        searchJob?.cancel()
        if (query.length < 2) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            runCatching { api.searchPlaces(query) }
                .onSuccess { res -> _state.update { it.copy(searchResults = res.items) } }
                .onFailure { Log.e(TAG, "search failed", it) }
        }
    }

    /** Crea el journey para el lugar elegido y refresca la lista. */
    fun registerTrip(place: ApiPlaceResult) {
        if (_state.value.isCreating) return
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            runCatching {
                when (place.source) {
                    "destination" -> tripRepo.createTrip(destinationId = place.id)
                    "place" -> tripRepo.createTrip(placeId = place.id, lat = place.lat, lng = place.lng)
                    else -> tripRepo.createTrip(lat = place.lat, lng = place.lng)
                }
            }.onSuccess {
                _state.update { it.copy(showRegisterSheet = false, isCreating = false) }
                load()
            }.onFailure {
                Log.e(TAG, "registerTrip failed", it)
                _state.update { it.copy(isCreating = false) }
            }
        }
    }

    companion object { private const val TAG = "TripsVM" }
}
