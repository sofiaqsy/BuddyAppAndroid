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
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
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

    private suspend fun refreshCommunityContext() {
        if (!locationProvider.hasPermission()) {
            _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
            return
        }
        val loc = locationProvider.currentLocation()
        if (loc == null) {
            _state.update { it.copy(isLoading = false) }
            return
        }
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
