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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Espejo del estado de TripsView (iOS): trips visibles (active/planning,
 * activos primero), selección, cancelación optimista de viaje/lugar.
 */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val api: HomeApi,
    private val tripRepo: TripRepository,
    private val travelerRepo: TravelerRepository,
    private val matchingRepo: com.buddy.app.features.matching.data.MatchingRepository,
    private val sse: com.buddy.app.core.network.SseClient,
    private val locationProvider: com.buddy.app.core.location.LocationProvider,
) : ViewModel() {

    data class TripsState(
        val isLoading: Boolean = true,
        val journeys: List<ApiJourney> = emptyList(),
        val selectedTripId: String? = null,
        val showRegisterSheet: Boolean = false,
        val searchResults: List<ApiPlaceResult> = emptyList(),
        val isCreating: Boolean = false,
        val activeBuddyName: String? = null,
        val activeBuddyAvatarUrl: String? = null,
        // Fase 2 "Buddy Community Places" — estado separado del de "Registrar
        // trip" a propósito: son dos sheets distintos, no deben compartir
        // resultados de búsqueda ni flags de carga entre sí.
        val showShareLugarSheet: Boolean = false,
        val shareLugarSearchResults: List<ApiPlaceResult> = emptyList(),
        val isSharingLugar: Boolean = false,
        val shareLugarError: String? = null,
        /** One-shot: la pantalla lo consume (abre el editor Memoir) y lo limpia. */
        val sharedLugarJourney: ApiJourney? = null,
    ) {
        /** Mismo filtro/orden que visibleTrips (iOS): active primero, luego llegada desc. */
        val visibleTrips: List<ApiJourney>
            get() = journeys
                .filter { it.status in listOf("active", "planning") }
                .sortedWith(compareBy({ if (it.status == "active") 0 else 1 }, { it.arrivalAt ?: "" }))

        val selectedTrip: ApiJourney?
            get() = visibleTrips.firstOrNull { it.id == selectedTripId } ?: visibleTrips.firstOrNull()
    }

    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        load()
        observeRealtime()
    }

    /**
     * Tiempo real de Tu trip — el stream SSE global emite match/offer/message:
     * si el buddy (o el propio usuario desde el chat) cierra la ayuda, la fila
     * "¿Una duda en X?" se actualiza sin salir del tab. Debounce anti-ráfagas.
     */
    private var realtimeJob: Job? = null
    private fun observeRealtime() {
        viewModelScope.launch {
            // Solo eventos de match: aquí lo único que depende del stream es la
            // fila "¿Una duda en X?". Antes recargaba journeys + matches con
            // CADA mensaje de chat, que es el evento más frecuente de todos.
            sse.events("stream").filter { it.event == null || it.event == "match" }.collect {
                realtimeJob?.cancel()
                realtimeJob = viewModelScope.launch {
                    delay(800)
                    load()
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
                val journeys = tripRepo.myJourneys()
                // Match activo para la fila del buddy (como activeMatch en iOS)
                val hasActive = journeys.any { it.status == "active" }
                val activeMatch = if (hasActive) {
                    runCatching { matchingRepo.matches() }.getOrDefault(emptyList())
                        .firstOrNull { it.status in listOf("pending", "accepted", "active") }
                } else null
                _state.update { s ->
                    s.copy(
                        isLoading = false,
                        journeys = journeys,
                        activeBuddyName = activeMatch?.buddy?.fullName?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() },
                        activeBuddyAvatarUrl = activeMatch?.buddy?.avatarUrl,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectTrip(id: String) = _state.update { it.copy(selectedTripId = id) }

    /** Cancela el VIAJE completo — optimista, igual que cancelActiveTrip (iOS). */
    fun cancelSelectedTrip() {
        val trip = _state.value.selectedTrip ?: return
        val tripId = trip.tripId
        _state.update { s ->
            s.copy(
                journeys = if (tripId != null) s.journeys.filter { it.tripId != tripId }
                           else s.journeys.filter { it.id != trip.id },
                selectedTripId = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                if (tripId != null) api.cancelTrip(tripId) else api.cancelJourney(trip.id)
            }.onFailure { Log.e(TAG, "cancelTrip failed", it) }
        }
    }

    /** Elimina UN lugar del viaje — optimista, igual que deleteJourney (iOS). */
    fun deleteJourney(journey: ApiJourney) {
        _state.update { s ->
            s.copy(
                journeys = s.journeys.filter { it.id != journey.id },
                selectedTripId = if (s.selectedTripId == journey.id) null else s.selectedTripId,
            )
        }
        viewModelScope.launch {
            runCatching { api.cancelJourney(journey.id) }
                .onFailure { Log.e(TAG, "deleteJourney failed", it) }
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

    // ── Fase 2 "Buddy Community Places": Compartir un lugar ──────────────

    private var shareLugarSearchJob: Job? = null

    fun openShareLugar() = _state.update {
        it.copy(showShareLugarSheet = true, shareLugarSearchResults = emptyList(), shareLugarError = null)
    }
    fun closeShareLugar() = _state.update { it.copy(showShareLugarSheet = false) }

    fun shareLugarSearch(query: String) {
        shareLugarSearchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(shareLugarSearchResults = emptyList()) }
            return
        }
        shareLugarSearchJob = viewModelScope.launch {
            delay(300)
            runCatching { api.searchPlaces(query) }
                .onSuccess { res -> _state.update { it.copy(shareLugarSearchResults = res.items) } }
                .onFailure { Log.e(TAG, "shareLugarSearch failed", it) }
        }
    }

    /** Requiere permiso de ubicación ya concedido — TripsScreen lo pide antes de llamar esto. */
    fun shareCurrentLocation() {
        if (_state.value.isSharingLugar) return
        viewModelScope.launch {
            val loc = locationProvider.currentLocation()
            if (loc == null) {
                _state.update { it.copy(shareLugarError = "No pudimos obtener tu ubicación. Activa el GPS o busca el lugar manualmente.") }
                return@launch
            }
            shareLugar { tripRepo.shareLugar(lat = loc.lat, lng = loc.lng) }
        }
    }

    fun shareSearchResult(place: ApiPlaceResult) = shareLugar {
        when (place.source) {
            "destination" -> tripRepo.shareLugar(destinationId = place.id)
            "place" -> tripRepo.shareLugar(placeId = place.id, lat = place.lat, lng = place.lng)
            else -> {
                if (place.lat == null || place.lng == null) {
                    error("Ese resultado no tiene coordenadas — prueba con otra búsqueda.")
                }
                tripRepo.shareLugar(lat = place.lat, lng = place.lng)
            }
        }
    }

    private fun shareLugar(create: suspend () -> ApiJourney) {
        if (_state.value.isSharingLugar) return
        viewModelScope.launch {
            _state.update { it.copy(isSharingLugar = true, shareLugarError = null) }
            runCatching { create() }
                .onSuccess { journey ->
                    _state.update {
                        it.copy(isSharingLugar = false, showShareLugarSheet = false, sharedLugarJourney = journey)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "shareLugar failed", e)
                    _state.update { it.copy(isSharingLugar = false, shareLugarError = "No pudimos compartir este lugar. Inténtalo de nuevo.") }
                }
        }
    }

    /** Consumido por TripsScreen tras abrir el editor Memoir con el journey creado. */
    fun consumeSharedLugarJourney() = _state.update { it.copy(sharedLugarJourney = null) }

    companion object { private const val TAG = "TripsVM" }
}
