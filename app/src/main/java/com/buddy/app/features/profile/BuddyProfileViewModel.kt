package com.buddy.app.features.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.ResolveRequest
import com.buddy.app.features.profile.data.ApiBuddyMe
import com.buddy.app.features.profile.data.ApiPlaceGuide
import com.buddy.app.features.profile.data.BuddyCoverageInput
import com.buddy.app.features.profile.data.ProfileApi
import com.buddy.app.features.profile.data.UpdateBuddyMeBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lugar donde el buddy ayuda — espejo de ZoneEntry (iOS): id + nombre + source ("place"). */
data class ZoneEntry(val id: String, val name: String, val source: String)

/**
 * Espejo de BuddyProfileView (iOS) — "Sé buddy en mi ciudad": disponibilidad,
 * zonas de cobertura (picker de lugares), especialidades por zona y guía
 * (spots/visitas/stickers) por zona con preview de mapa.
 */
@HiltViewModel
class BuddyProfileViewModel @Inject constructor(
    private val api: ProfileApi,
    private val homeApi: HomeApi,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    data class State(
        val profile: ApiBuddyMe.BuddyProfile? = null,
        val isAvailable: Boolean = false,
        val specialties: Set<String> = emptySet(),
        val zones: List<ZoneEntry> = emptyList(),
        val savingAvailability: Boolean = false,
        val savingZones: Boolean = false,
        val savingSpecialties: Boolean = false,
        val placeGuides: Map<String, ApiPlaceGuide> = emptyMap(),
        // Picker de lugar/ciudad
        val showZonePicker: Boolean = false,
        val pickerQuery: String = "",
        val pickerResults: List<ApiPlaceResult> = emptyList(),
        val isSearching: Boolean = false,
        val isResolvingPick: Boolean = false,
        // Sugerencia por GPS — el lugar donde el buddy está parado ahora mismo.
        val suggestion: ApiPlaceResult? = null,
        val isLoadingSuggestion: Boolean = false,
    ) {
        val status: String get() = profile?.verificationStatus ?: ""
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Cobertura de ciudad (source "destination") elegida en el picker — se envía junto a place_ids. */
    private var selectedCoverage: BuddyCoverageInput? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var guidesLoadedAt: Long = 0L
    private var lastValidResults: List<ApiPlaceResult> = emptyList()

    fun initialize(profile: ApiBuddyMe.BuddyProfile) {
        if (_state.value.profile?.id == profile.id) return
        val zones = (profile.placeIds ?: emptyList()).map { ZoneEntry(it, it, "place") }
        _state.update {
            it.copy(
                profile = profile,
                isAvailable = profile.isAvailable,
                specialties = (profile.specialties ?: emptyList()).toSet(),
                zones = zones,
            )
        }
        resolveZoneNames()
        loadGuides()
    }

    /** Nombre real de cada zona vía /places/geo/:id — espejo de resolveZoneNames (iOS). */
    private fun resolveZoneNames() {
        _state.value.zones.forEach { zone ->
            viewModelScope.launch {
                val place = runCatching { api.geoPlace(zone.id) }.getOrNull() ?: return@launch
                _state.update { s ->
                    s.copy(zones = s.zones.map { if (it.id == zone.id) it.copy(name = place.name) else it })
                }
            }
        }
    }

    fun setAvailable(available: Boolean) {
        val previous = _state.value.isAvailable
        _state.update { it.copy(isAvailable = available, savingAvailability = true) }
        viewModelScope.launch {
            runCatching { api.updateBuddyMe(UpdateBuddyMeBody(isAvailable = available)) }
                .onSuccess { onUpdated(it) }
                .onFailure {
                    Log.e(TAG, "saveAvailability failed", it)
                    _state.update { s -> s.copy(isAvailable = previous) }
                }
            _state.update { it.copy(savingAvailability = false) }
        }
    }

    fun toggleSpecialty(key: String) {
        val current = _state.value.specialties
        val updated = if (key in current) current - key else current + key
        _state.update { it.copy(specialties = updated) }
        saveSpecialties()
    }

    private fun saveSpecialties() {
        _state.update { it.copy(savingSpecialties = true) }
        viewModelScope.launch {
            runCatching { api.updateBuddyMe(UpdateBuddyMeBody(specialties = _state.value.specialties.toList())) }
                .onSuccess { onUpdated(it) }
                .onFailure { Log.e(TAG, "saveSpecialties failed", it) }
            _state.update { it.copy(savingSpecialties = false) }
        }
    }

    fun removeZone(zoneId: String) {
        _state.update { it.copy(zones = it.zones.filterNot { z -> z.id == zoneId }) }
        saveZones()
    }

    private fun addPlaceZone(zone: ZoneEntry) {
        if (_state.value.zones.any { it.id == zone.id }) return
        _state.update { it.copy(zones = it.zones + zone) }
        saveZones()
    }

    private fun setCityCoverage(coverage: BuddyCoverageInput) {
        selectedCoverage = coverage
        saveZones()
    }

    /** SIEMPRE envía place_ids (incluso vacío) — [] limpia la cobertura, como iOS. */
    private fun saveZones() {
        _state.update { it.copy(savingZones = true) }
        val placeIds = _state.value.zones.map { it.id }
        viewModelScope.launch {
            runCatching {
                api.updateBuddyMe(UpdateBuddyMeBody(coverage = selectedCoverage, placeIds = placeIds))
            }
                .onSuccess { onUpdated(it); selectedCoverage = null; loadGuides(force = true) }
                .onFailure { Log.e(TAG, "saveZones failed", it) }
            _state.update { it.copy(savingZones = false) }
        }
    }

    private fun onUpdated(updated: ApiBuddyMe) {
        val p = updated.profile ?: return
        _state.update { it.copy(profile = p) }
    }

    /** TTL 60s — evita N×3 requests al reabrir la pantalla (igual que iOS). */
    fun loadGuides(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - guidesLoadedAt < 60_000) return
        val zones = _state.value.zones
        if (zones.isEmpty()) return
        viewModelScope.launch {
            coroutineScope {
                val results = zones.map { zone ->
                    async { zone.id to runCatching { api.placeGuide(zone.id, zone.source) }.getOrNull() }
                }.map { it.await() }
                _state.update { s ->
                    s.copy(placeGuides = s.placeGuides + results.mapNotNull { (id, g) -> g?.let { id to it } })
                }
            }
            guidesLoadedAt = System.currentTimeMillis()
        }
    }

    // ── Picker de lugar/ciudad — espejo de PlaceZonePickerSheet (iOS) ────────

    fun openZonePicker() {
        _state.update { it.copy(showZonePicker = true, pickerQuery = "", pickerResults = emptyList()) }
        loadSuggestion()
    }
    fun closeZonePicker() = _state.update { it.copy(showZonePicker = false) }

    /**
     * Resuelve el GPS del buddy a un lugar sugerido — SIEMPRE vía /places/resolve
     * (el mismo flujo pioneer que ensureActiveTripForGps), nunca /location/resolve:
     * un destino curado (source "destination") solo declara cobertura de ciudad
     * en el servidor sin aparecer como zona visible, así que la sugerencia
     * "no hacía nada" al tocarla. /places/resolve siempre da un lugar real
     * (auto-creado si hace falta) que SÍ se agrega como card visible.
     */
    private fun loadSuggestion() {
        if (_state.value.suggestion != null || !locationProvider.hasPermission()) return
        _state.update { it.copy(isLoadingSuggestion = true) }
        viewModelScope.launch {
            val loc = locationProvider.currentLocation()
            if (loc == null) {
                _state.update { it.copy(isLoadingSuggestion = false) }
                return@launch
            }
            val place = runCatching { homeApi.resolvePlace(ResolveRequest(loc.lat, loc.lng)) }.getOrNull()
            val suggested = place?.let {
                ApiPlaceResult(id = it.id, source = "place", title = it.name, subtitle = null, lat = loc.lat, lng = loc.lng)
            }
            _state.update { it.copy(suggestion = suggested, isLoadingSuggestion = false) }
        }
    }

    fun searchZoneQuery(query: String) {
        _state.update { it.copy(pickerQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            lastValidResults = emptyList()
            _state.update { it.copy(pickerResults = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(320)
            runCatching { homeApi.searchPlaces(q).items }
                .onSuccess { items ->
                    if (items.isNotEmpty()) lastValidResults = items
                    _state.update { it.copy(pickerResults = items.ifEmpty { lastValidResults }, isSearching = false) }
                }
                .onFailure {
                    Log.e(TAG, "searchPlaces failed", it)
                    _state.update { s -> s.copy(isSearching = false) }
                }
        }
    }

    fun pickResult(place: ApiPlaceResult) {
        _state.update { it.copy(isResolvingPick = true) }
        viewModelScope.launch {
            when (place.source) {
                "destination" -> setCityCoverage(
                    BuddyCoverageInput(
                        destinationId = place.id, city = place.title,
                        countryCode = place.subtitle ?: "", lat = place.lat, lng = place.lng,
                    ),
                )
                "place" -> addPlaceZone(ZoneEntry(place.id, place.title, "place"))
                else -> {
                    val lat = place.lat; val lng = place.lng
                    if (lat != null && lng != null) {
                        val resolved = runCatching { homeApi.resolvePlace(ResolveRequest(lat, lng)) }.getOrNull()
                        if (resolved != null) addPlaceZone(ZoneEntry(resolved.id, place.title, "place"))
                    }
                }
            }
            _state.update { it.copy(isResolvingPick = false, showZonePicker = false) }
        }
    }

    companion object { private const val TAG = "BuddyProfileVM" }
}
