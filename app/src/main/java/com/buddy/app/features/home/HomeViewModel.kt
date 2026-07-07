package com.buddy.app.features.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.features.authentication.data.TravelerRepository
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.ResolveRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Espejo del flujo de datos de InicioView:
 * 1. ensureSession (guest silencioso)
 * 2. resolveLocation(GPS) → destination más cercana (LocationResolver backend)
 * 3. placeContext(destination) → buddies / stories / status
 * 4. feed/stories → "Historias de viajeros"
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: HomeApi,
    private val matchingApi: com.buddy.app.features.matching.data.MatchingApi,
    private val travelerRepo: TravelerRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    data class HomeState(
        val isLoading: Boolean = true,
        val loadFailed: Boolean = false,
        val destinationId: String? = null,
        val destinationName: String? = null,
        val communityContext: ApiPlaceContext? = null,
        val stories: List<ApiJourney> = emptyList(),
        val isLoadingFeed: Boolean = true,
        val feedFailed: Boolean = false,
        val needsLocationPermission: Boolean = false,
        // Espejo de liveJourneys / activeMatch (iOS): con trip vivo el composer
        // usa el destino del trip y el CTA cambia a "Sigue hablando con X".
        val activeJourney: ApiJourney? = null,
        val userLat: Double? = null,
        val userLng: Double? = null,
        val activeBuddyName: String? = null,
        val activeBuddyAvatarUrl: String? = null,
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
                loadTripAndMatch()
                refreshCommunityContext()
                loadFeed()
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) load()
        else _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
    }

    /** Espejo de loadData + activeMatch (iOS). */
    private suspend fun loadTripAndMatch() {
        val journeys = runCatching { api.myJourneys() }.getOrDefault(emptyList())
        val active = journeys.firstOrNull { it.status == "active" }
            ?: journeys.firstOrNull { it.status == "planning" }
        val match = if (active != null) {
            runCatching { matchingApi.matches() }.getOrDefault(emptyList())
                .firstOrNull { it.status in listOf("accepted", "active", "pending") }
        } else null
        _state.update {
            it.copy(
                activeJourney = active,
                activeBuddyName = match?.buddy?.fullName?.split(" ")?.firstOrNull()?.replaceFirstChar { c -> c.uppercase() },
                activeBuddyAvatarUrl = match?.buddy?.avatarUrl,
            )
        }
    }

    private suspend fun refreshCommunityContext() {
        // Con trip vivo: contexto del destino del trip (como iOS)
        val journey = _state.value.activeJourney
        if (journey != null) {
            val destId = journey.destination?.id ?: journey.destinationId
            val ctx = destId?.let { runCatching { api.placeContext(it, "destination") }.getOrNull() }
                ?: ApiPlaceContext(0, 0, 0, "pioneer")
            _state.update {
                it.copy(
                    isLoading = false,
                    destinationId = destId,
                    destinationName = journey.destination?.name,
                    communityContext = ctx,
                )
            }
            return
        }
        if (!locationProvider.hasPermission()) {
            _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
            return
        }
        val loc = locationProvider.currentLocation()
        if (loc == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        _state.update { it.copy(userLat = loc.lat, userLng = loc.lng) }
        Log.d(TAG, "resolving location lat=${loc.lat} lng=${loc.lng}")
        val res = api.resolveLocation(ResolveRequest(loc.lat, loc.lng))
        val resolution = if (res.code() == 204) null else res.body()
        if (resolution == null) {
            // Sin match → pioneer mode (0 buddies), igual que iOS
            _state.update {
                it.copy(
                    isLoading = false,
                    destinationId = null,
                    destinationName = null,
                    communityContext = ApiPlaceContext(0, 0, 0, "pioneer"),
                )
            }
            return
        }
        val ctx = api.placeContext(resolution.destinationId, source = "destination")
        Log.d(TAG, "resolved ${resolution.destinationName} → buddies=${ctx.buddies}")
        _state.update {
            it.copy(
                isLoading = false,
                destinationId = resolution.destinationId,
                destinationName = resolution.destinationName,
                communityContext = ctx,
            )
        }
    }

    fun loadFeed() {
        _state.update { it.copy(isLoadingFeed = true, feedFailed = false) }
        viewModelScope.launch {
            try {
                val loc = if (locationProvider.hasPermission()) locationProvider.currentLocation() else null
                val page = api.feedStories(limit = 10, lat = loc?.lat, lng = loc?.lng)
                _state.update { it.copy(stories = page.items, isLoadingFeed = false) }
            } catch (e: Exception) {
                Log.e(TAG, "feed failed", e)
                _state.update { it.copy(isLoadingFeed = false, feedFailed = true) }
            }
        }
    }

    companion object { private const val TAG = "HomeVM" }
}
