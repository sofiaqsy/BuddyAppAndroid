package com.buddy.app.features.trips

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyLoading
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.features.home.PublishedTripCard
import com.buddy.app.features.home.data.HomeApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Historias de otros viajeros, dentro del tab Trips — espejo de
 * TravelerStoriesSection (iOS). Vivían en el Home; ahí el núcleo es "lugares
 * cerca que recomiendan los buddies" y el feed más pesado de la app sobraba.
 *
 * Sin título: la sección se entiende por su contenido (iOS lo quitó igual).
 */
@HiltViewModel
class TravelerStoriesViewModel @Inject constructor(
    private val api: HomeApi,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    data class State(
        val stories: List<ApiJourney> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val failed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var cursor: String? = null
    private var hasMore = true
    private val seenIds = mutableSetOf<String>()

    private suspend fun location() =
        if (locationProvider.hasPermission()) locationProvider.currentLocation() else null

    /** Primera página, con un reintento: un timeout puntual no puede dejar la
     *  sección vacía en silencio. */
    fun load() {
        if (_state.value.stories.isEmpty()) _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repeat(2) { attempt ->
                try {
                    val loc = location()
                    val page = api.feedStories(limit = 10, lat = loc?.lat, lng = loc?.lng)
                    seenIds.clear(); seenIds += page.items.map { it.id }
                    cursor = page.nextCursor
                    hasMore = page.hasMore && page.nextCursor != null
                    _state.update { State(stories = page.items, isLoading = false) }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "intento ${attempt + 1} falló", e)
                    if (attempt == 0) delay(800)
                }
            }
            _state.update { it.copy(isLoading = false, failed = it.stories.isEmpty()) }
        }
    }

    /**
     * Siguiente página. Una página puede volver ENTERA repetida y entonces no
     * se agrega ninguna tarjeta nueva: sin tarjeta nueva no hay nada que pida la
     * página siguiente y la lista se queda clavada. Por eso se sigue pidiendo
     * mientras el servidor diga que hay más y no llegue nada nuevo, con tope.
     */
    fun loadMore() {
        if (!hasMore || cursor == null || _state.value.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            try {
                repeat(3) {
                    val actual = cursor ?: return@launch
                    if (!hasMore) return@launch
                    val loc = location()
                    val page = api.feedStories(limit = 10, lat = loc?.lat, lng = loc?.lng, cursor = actual)
                    val fresh = page.items.filter { seenIds.add(it.id) }
                    cursor = page.nextCursor
                    hasMore = page.hasMore && page.nextCursor != null
                    if (fresh.isNotEmpty()) {
                        _state.update { it.copy(stories = it.stories + fresh) }
                        return@launch
                    }
                    Log.d(TAG, "página sin novedades (${page.items.size} repetidos) — sigo")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadMore falló", e)
            } finally {
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private companion object { const val TAG = "TravelerStories" }
}

@Composable
fun TravelerStoriesSection(
    /** Cambia al refrescar el tab: vuelve a pedir la primera página. */
    reloadToken: Int,
    /** El scroll de la pantalla que contiene la sección: Trips usa un Column
     *  con scroll, no una lista perezosa, así que todas las tarjetas se
     *  componen de entrada y "la tarjeta apareció" no sirve de señal — se
     *  pagina cuando el scroll se acerca al final. */
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenProfile: (travelerId: String, name: String?, avatarUrl: String?) -> Unit,
    onOpenDestination: (destinationId: String, name: String) -> Unit,
    viewModel: TravelerStoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(reloadToken) { viewModel.load() }
    LaunchedEffect(scrollState) {
        androidx.compose.runtime.snapshotFlow {
            scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 1500
        }.collect { cerca -> if (cerca) viewModel.loadMore() }
    }

    when {
        state.isLoading && state.stories.isEmpty() -> BuddyLoading(Modifier.height(200.dp))

        state.failed -> Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("No pudimos cargar las historias", style = BuddyType.Callout, color = BuddyColor.InkMuted)
            Text(
                "Reintentar",
                style = BuddyType.FootnoteBold,
                color = BuddyColor.Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Surface)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                    .clickable { viewModel.load() }
                    .padding(horizontal = Spacing.lg, vertical = 10.dp),
            )
        }

        state.stories.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            state.stories.forEach { story ->
                PublishedTripCard(
                    story,
                    onOpenProfile = onOpenProfile,
                    onOpenDestination = onOpenDestination,
                    modifier = Modifier.padding(horizontal = Spacing.edge),
                )
            }
            if (state.isLoadingMore) BuddyLoading(Modifier.height(60.dp))
        }

        else -> Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xl, horizontal = Spacing.edge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Aún nadie ha contado su paso por aquí", style = BuddyType.Callout, color = BuddyColor.Ink)
            Text(
                "Cuando termines tu trip, tu historia será la primera.",
                style = BuddyType.Footnote, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
            )
        }
    }
}
