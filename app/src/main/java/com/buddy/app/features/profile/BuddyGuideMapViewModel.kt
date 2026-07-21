package com.buddy.app.features.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.features.profile.data.ApiPlaceGuideSpot
import com.buddy.app.features.profile.data.CreateSpotBody
import com.buddy.app.features.profile.data.ProfileApi
import com.buddy.app.features.profile.data.UpdateSpotBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Editor de la guía del lugar — espejo de BuddyGuideMapSheet (iOS): mapa con
 * los spots, modo edición (agregar/mover/editar/borrar), formulario de spot
 * y lista paginada independiente para el modo edición.
 */
@HiltViewModel
class BuddyGuideMapViewModel @Inject constructor(
    private val api: ProfileApi,
) : ViewModel() {

    data class State(
        val zoneId: String = "",
        val source: String = "place",
        val destId: String? = null,
        val zoneName: String = "",
        // Preview de spots (para el mapa + browse sheet) — del guide preview
        val localSpots: List<ApiPlaceGuideSpot> = emptyList(),
        val selectedSpotId: String? = null,
        val editMode: Boolean = false,
        val addingNew: Boolean = false,
        val addFormReady: Boolean = false,
        val movingSpotId: String? = null,
        val editingSpot: ApiPlaceGuideSpot? = null,
        val spotName: String = "",
        val spotPlaceType: String = "landmark",
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val deletingId: String? = null,
        // Lista paginada del modo edición (independiente del preview)
        val editListSpots: List<ApiPlaceGuideSpot> = emptyList(),
        val editListCursor: String? = null,
        val editListHasMore: Boolean = false,
        val editListLoading: Boolean = false,
        val editListError: Boolean = false,
    ) {
        /** Crosshair activo: agregando nuevo (sin form aún) o moviendo uno existente. */
        val inCrosshair: Boolean get() = (addingNew && !addFormReady) || movingSpotId != null
        val showEditForm: Boolean get() = editingSpot != null && movingSpotId == null
        val showAddForm: Boolean get() = addingNew && addFormReady
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun initialize(zoneId: String, source: String, destId: String?, zoneName: String, previewSpots: List<ApiPlaceGuideSpot>) {
        if (_state.value.zoneId == zoneId && _state.value.localSpots.isNotEmpty()) return
        _state.value = State(
            zoneId = zoneId, source = source, destId = destId, zoneName = zoneName,
            localSpots = previewSpots.sortedBy { it.name },
        )
        loadEditList()
    }

    fun selectSpot(id: String?) = _state.update { it.copy(selectedSpotId = if (it.selectedSpotId == id) null else id) }

    fun toggleEditMode() = _state.update {
        val next = !it.editMode
        it.copy(editMode = next, selectedSpotId = if (!next) null else it.selectedSpotId)
    }

    fun cancelSubMode() = _state.update {
        it.copy(addingNew = false, addFormReady = false, movingSpotId = null, editingSpot = null, saveError = null)
    }

    /** Long-press en el mapa (editMode, sin sub-modo activo, con destino vinculado) → crosshair de alta. */
    fun startAddingNew() {
        val s = _state.value
        if (!s.editMode || s.destId == null || s.inCrosshair || s.editingSpot != null) return
        _state.update { it.copy(addingNew = true, addFormReady = false) }
    }

    fun startEditing(spot: ApiPlaceGuideSpot) = _state.update {
        it.copy(editingSpot = spot, spotName = spot.name, spotPlaceType = "landmark", saveError = null)
    }

    fun startMoving() {
        val spot = _state.value.editingSpot ?: return
        _state.update { it.copy(movingSpotId = spot.id, editingSpot = null) }
    }

    fun setSpotName(name: String) = _state.update { it.copy(spotName = name) }
    fun setSpotPlaceType(type: String) = _state.update { it.copy(spotPlaceType = type) }

    fun openAddFromEditSheet() = _state.update { it.copy(addingNew = true, addFormReady = false, saveError = null) }

    /** Confirmar posición del crosshair: para alta pasa al form; para mover, guarda ya. */
    fun confirmPosition(lat: Double, lng: Double) {
        val s = _state.value
        if (s.addingNew) {
            _state.update { it.copy(addFormReady = true, spotName = "", spotPlaceType = "landmark", saveError = null) }
            return
        }
        val movId = s.movingSpotId ?: return
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching { api.updateSpot(movId, UpdateSpotBody(lat = lat, lng = lng)) }
                .onSuccess { updated ->
                    _state.update {
                        it.copy(
                            localSpots = it.localSpots.map { sp -> if (sp.id == movId) updated.copy(coverUrl = updated.coverUrl ?: sp.coverUrl) else sp },
                            editListSpots = it.editListSpots.map { sp -> if (sp.id == movId) updated.copy(coverUrl = updated.coverUrl ?: sp.coverUrl) else sp },
                            movingSpotId = null, isSaving = false,
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "move spot failed", it)
                    _state.update { s2 -> s2.copy(isSaving = false, saveError = "Error al mover el lugar") }
                }
        }
    }

    fun commitCreateSpot(lat: Double, lng: Double) {
        val s = _state.value
        val destId = s.destId ?: return
        val name = s.spotName.trim()
        if (name.isEmpty()) return
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching { api.createSpot(CreateSpotBody(name, lat, lng, destId, s.spotPlaceType)) }
                .onSuccess { created ->
                    _state.update {
                        it.copy(
                            localSpots = (it.localSpots + created).sortedBy { sp -> sp.name },
                            editListSpots = (it.editListSpots + created).sortedBy { sp -> sp.name },
                            addingNew = false, addFormReady = false, isSaving = false, editMode = false,
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "create spot failed", it)
                    _state.update { s2 -> s2.copy(isSaving = false, saveError = "Error al guardar el lugar") }
                }
        }
    }

    fun commitUpdateSpot() {
        val spot = _state.value.editingSpot ?: return
        val name = _state.value.spotName.trim()
        if (name.isEmpty()) return
        _state.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching { api.updateSpot(spot.id, UpdateSpotBody(name = name, placeType = _state.value.spotPlaceType)) }
                .onSuccess { updated ->
                    val merged = updated.copy(coverUrl = updated.coverUrl ?: spot.coverUrl)
                    _state.update {
                        it.copy(
                            localSpots = it.localSpots.map { sp -> if (sp.id == spot.id) merged else sp }.sortedBy { sp -> sp.name },
                            editListSpots = it.editListSpots.map { sp -> if (sp.id == spot.id) merged else sp }.sortedBy { sp -> sp.name },
                            editingSpot = null, isSaving = false,
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "update spot failed", it)
                    _state.update { s2 -> s2.copy(isSaving = false, saveError = "Error al actualizar el lugar") }
                }
        }
    }

    fun deleteSpot(id: String) {
        _state.update { it.copy(deletingId = id) }
        viewModelScope.launch {
            runCatching { api.deleteSpot(id) }
                .onSuccess {
                    _state.update {
                        val spots = it.localSpots.filterNot { sp -> sp.id == id }
                        it.copy(
                            localSpots = spots,
                            editListSpots = it.editListSpots.filterNot { sp -> sp.id == id },
                            editingSpot = null, deletingId = null,
                            editMode = if (spots.isEmpty()) false else it.editMode,
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "delete spot failed", it)
                    _state.update { s2 -> s2.copy(deletingId = null) }
                }
        }
    }

    // ── Lista paginada del modo edición — espejo de BuddyEditSheet (iOS) ─────

    fun loadEditList() {
        val s = _state.value
        if (s.editListLoading) return
        _state.update { it.copy(editListLoading = true, editListError = false, editListSpots = emptyList(), editListCursor = null) }
        viewModelScope.launch {
            runCatching { api.guideSpots(s.zoneId, s.source, cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(editListSpots = page.spots, editListCursor = page.nextCursor, editListHasMore = page.hasMore, editListLoading = false)
                    }
                }
                .onFailure {
                    Log.e(TAG, "loadEditList failed", it)
                    _state.update { s2 -> s2.copy(editListLoading = false, editListError = true) }
                }
        }
    }

    fun loadMoreEditList() {
        val s = _state.value
        if (s.editListLoading || !s.editListHasMore) return
        val cursor = s.editListCursor ?: return
        _state.update { it.copy(editListLoading = true) }
        viewModelScope.launch {
            runCatching { api.guideSpots(s.zoneId, s.source, cursor = cursor) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            editListSpots = it.editListSpots + page.spots,
                            editListCursor = page.nextCursor, editListHasMore = page.hasMore, editListLoading = false,
                        )
                    }
                }
                .onFailure {
                    Log.e(TAG, "loadMoreEditList failed", it)
                    _state.update { s2 -> s2.copy(editListLoading = false) }
                }
        }
    }

    companion object { private const val TAG = "BuddyGuideMapVM" }
}
