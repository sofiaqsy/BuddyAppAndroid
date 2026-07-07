package com.buddy.app.features.trips.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiDestination
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.features.home.data.CreateJourneyBody
import com.buddy.app.features.home.data.HomeApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Opciones rápidas de llegada — espejo del enum de RegisterTripView. */
enum class QuickOption { Here, Today, Tomorrow }

/**
 * Estado y lógica de RegisterTripView (iOS) portados 1:1:
 * búsqueda debounced, populares, contexto de comunidad del lugar elegido,
 * llegada (rápida o fecha), toggles de planificación y creación del journey.
 */
@HiltViewModel
class RegisterTripViewModel @Inject constructor(
    private val api: HomeApi,
) : ViewModel() {

    data class State(
        val searchText: String = "",
        val isSearching: Boolean = false,
        val searchResults: List<ApiPlaceResult> = emptyList(),
        val popularDests: List<ApiDestination> = emptyList(),
        val popularLoadFailed: Boolean = false,
        val selectedDest: ApiDestination? = null,
        val selectedPlace: ApiPlaceResult? = null,
        val placeContext: ApiPlaceContext? = null,
        val isLoadingContext: Boolean = false,
        val quickOption: QuickOption? = QuickOption.Here,
        val selectedDate: LocalDate = LocalDate.now(),
        val showDatePicker: Boolean = false,
        val knowsHowToGet: Boolean = false,
        val hasLodging: Boolean = false,
        val isCreating: Boolean = false,
        val showCreateError: Boolean = false,
    ) {
        val hasSelection: Boolean get() = selectedDest != null || selectedPlace != null
        val canCreate: Boolean get() = hasSelection
        /** Las preguntas de planificación solo aplican a trips futuros (como iOS). */
        val showPlanningQuestions: Boolean get() = quickOption != QuickOption.Here
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var contextJob: Job? = null

    init { loadPopular() }

    fun loadPopular() {
        _state.update { it.copy(popularLoadFailed = false) }
        viewModelScope.launch {
            runCatching { api.destinations(limit = 5) }
                .onSuccess { d -> _state.update { it.copy(popularDests = d) } }
                .onFailure {
                    Log.e(TAG, "fetchDestinations failed", it)
                    _state.update { it.copy(popularLoadFailed = true) }
                }
        }
    }

    fun onSearchTextChange(text: String) {
        _state.update { s ->
            s.copy(
                searchText = text,
                // Editar el texto deselecciona (como iOS)
                selectedDest = s.selectedDest?.takeIf { it.name == text },
                selectedPlace = s.selectedPlace?.takeIf { it.title == text },
            )
        }
        searchJob?.cancel()
        val q = text.trim()
        if (q.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            delay(300)
            runCatching { api.searchPlaces(q) }
                .onSuccess { res -> _state.update { it.copy(searchResults = res.items, isSearching = false) } }
                .onFailure { _state.update { it.copy(isSearching = false) } }
        }
    }

    fun clearSearch() {
        searchJob?.cancel(); contextJob?.cancel()
        _state.update {
            it.copy(
                searchText = "", searchResults = emptyList(),
                selectedDest = null, selectedPlace = null,
                placeContext = null, isLoadingContext = false,
            )
        }
    }

    fun selectPlace(place: ApiPlaceResult) {
        _state.update { it.copy(selectedPlace = place, selectedDest = null, searchText = place.title) }
        fetchContext(place.id, place.source)
    }

    fun selectDest(dest: ApiDestination) {
        _state.update { it.copy(selectedDest = dest, selectedPlace = null, searchText = dest.name) }
        fetchContext(dest.id, "destination")
    }

    private fun fetchContext(id: String, source: String) {
        contextJob?.cancel()
        _state.update { it.copy(placeContext = null, isLoadingContext = true) }
        contextJob = viewModelScope.launch {
            runCatching { api.placeContext(id, source) }
                .onSuccess { ctx -> _state.update { it.copy(placeContext = ctx, isLoadingContext = false) } }
                .onFailure { _state.update { it.copy(isLoadingContext = false) } }
        }
    }

    fun selectQuick(option: QuickOption) = _state.update {
        it.copy(
            quickOption = option,
            selectedDate = if (option == QuickOption.Tomorrow) LocalDate.now().plusDays(1) else LocalDate.now(),
            showDatePicker = false,
        )
    }

    fun toggleDatePicker() = _state.update { it.copy(showDatePicker = !it.showDatePicker) }

    fun selectDate(date: LocalDate) = _state.update {
        it.copy(selectedDate = date, quickOption = null, showDatePicker = false)
    }

    fun setKnowsHowToGet(v: Boolean) = _state.update { it.copy(knowsHowToGet = v) }
    fun setHasLodging(v: Boolean) = _state.update { it.copy(hasLodging = v) }
    fun dismissError() = _state.update { it.copy(showCreateError = false) }

    /** Espejo exacto de createTrip (iOS): resuelve qué mandar según el origen. */
    fun createTrip(onCreated: (ApiJourney) -> Unit) {
        val s = _state.value
        if (!s.canCreate || s.isCreating) return

        var destinationId: String? = s.selectedDest?.id
        var placeId: String? = null
        var osmId: String? = null
        var lat: Double? = null
        var lng: Double? = null

        val place = s.selectedPlace
        if (place != null) {
            when (place.source) {
                "place" -> placeId = place.id
                "destination" -> destinationId = destinationId ?: place.id
                else -> {
                    lat = place.lat; lng = place.lng
                    if (place.id.firstOrNull() in listOf('N', 'W', 'R')) osmId = place.id
                }
            }
        }
        if (destinationId == null && placeId == null && (lat == null || lng == null)) return

        val isHere = s.quickOption == QuickOption.Here
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            runCatching {
                api.createJourney(
                    CreateJourneyBody(
                        destinationId = destinationId,
                        placeId = placeId,
                        osmId = osmId,
                        lat = lat,
                        lng = lng,
                        arrivalAt = if (isHere) null else s.selectedDate.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z",
                        knowsHowToGet = if (isHere) null else s.knowsHowToGet,
                        hasLodging = if (isHere) null else s.hasLodging,
                    ),
                )
            }.onSuccess { journey ->
                _state.update { it.copy(isCreating = false) }
                onCreated(journey)
            }.onFailure {
                Log.e(TAG, "createTrip failed", it)
                _state.update { it.copy(isCreating = false, showCreateError = true) }
            }
        }
    }

    companion object { private const val TAG = "RegisterTripVM" }
}
