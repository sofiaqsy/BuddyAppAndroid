package com.buddy.app.features.home.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiDestination
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.ProductHit
import com.buddy.app.features.trips.map.MapApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Búsqueda y portada del Inicio en filas (iOS HomeRowsView + loadHeroCover). */
@HiltViewModel
class InicioRowsViewModel @Inject constructor(
    private val homeApi: HomeApi,
    private val mapApi: MapApi,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {

    // La última portada se guarda: al abrir, Inicio la muestra al instante
    // (foto, nombre y eslogan) mientras llega la red, como la copia de disco de iOS.
    private val prefs = context.getSharedPreferences("inicio_hero", android.content.Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private fun cachedHero(): ApiDestination? = prefs.getString("hero", null)
        ?.let { runCatching { json.decodeFromString(ApiDestination.serializer(), it) }.getOrNull() }

    data class UiState(
        val query: String = "",
        /** id del lugar en minúsculas -> productos que coinciden. */
        val productHits: Map<String, List<ProductHit>> = emptyMap(),
        val hero: ApiDestination? = null,
        val reshuffleToken: Int = 0,
        /** Fila "Productos locales": una tarjeta por presentación, tiendas intercaladas. */
        val localProducts: List<LocalProductTile> = emptyList(),
        /** "Lugares turísticos": lugares públicos de la guía del destino (iOS, 24 Nov 2026). */
        val touristPlaces: List<com.buddy.app.core.data.model.ApiPlaceCard> = emptyList(),
    )

    /** Un producto en una presentación, con el lugar que lo vende. */
    data class LocalProductTile(
        val id: String,
        val card: com.buddy.app.core.data.model.ApiPlaceCard,
        val product: com.buddy.app.core.data.model.FincasProduct,
        val pres: com.buddy.app.core.data.model.FincasProduct.Presentation?,
    )

    private val _state = MutableStateFlow(UiState(hero = cachedHero()))
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        val t = q.trim()
        if (t.length < 2) { _state.update { it.copy(productHits = emptyMap()) }; return }
        searchJob = viewModelScope.launch {
            delay(300)   // espera a que deje de escribir (igual que iOS)
            val hits = runCatching { homeApi.productSearch(t).results }.getOrNull() ?: return@launch
            _state.update { s -> s.copy(productHits = hits.associate { it.spotId.lowercase() to it.products }) }
        }
    }

    fun loadHero(destinationId: String?) {
        if (destinationId == null || _state.value.hero?.id == destinationId) return
        viewModelScope.launch {
            runCatching { mapApi.destination(destinationId) }.getOrNull()?.let { d ->
                _state.update { it.copy(hero = d) }
                prefs.edit().putString("hero", json.encodeToString(ApiDestination.serializer(), d)).apply()
            }
        }
    }

    private var productsJob: Job? = null

    /**
     * Lee los productos de los lugares de la zona (máx. 20, en paralelo; los
     * lugares sin negocio responden vacío). Se vuelve a llamar al tocar Inicio
     * otra vez: nuevos datos y otro orden (Keyla, 23 Nov 2026). Un error deja
     * la fila como estaba.
     */
    fun loadLocalProducts(spots: List<com.buddy.app.core.data.model.ApiPlaceCard>) {
        productsJob?.cancel()
        val token = _state.value.reshuffleToken
        val first = spots.take(20)
        if (first.isEmpty()) { _state.update { it.copy(localProducts = emptyList()) }; return }
        productsJob = viewModelScope.launch {
            val pages = kotlinx.coroutines.coroutineScope {
                first.map { c ->
                    async { c to (runCatching { homeApi.spotProducts(c.id.lowercase()).items }.getOrNull() ?: emptyList()) }
                }.awaitAll()
            }
            val perStore = InicioRowsLogic.rotated(first, token).mapNotNull { c ->
                val items = pages.firstOrNull { it.first.id == c.id }?.second.orEmpty()
                val tiles = items.flatMap { p ->
                    p.entries().map { pr -> LocalProductTile("${c.id}-${p.id}-${pr?.id ?: ""}", c, p, pr) }
                }
                InicioRowsLogic.shuffled(tiles, token) { it.id }.takeIf { it.isNotEmpty() }
            }
            val tiles = InicioRowsLogic.interleaveByStore(perStore, storeOf = { it.card.id })
            _state.update { it.copy(localProducts = tiles) }
        }
    }

    /**
     * Lugares turísticos (cataratas, miradores, plaza): salen de la guía del
     * destino, no del feed, porque el feed solo trae lugares con fotos de
     * viajeros y las cataratas no tienen (igual que iOS).
     */
    fun loadTouristPlaces(destinationId: String?) {
        if (destinationId == null) return
        viewModelScope.launch {
            val spots = runCatching { mapApi.guideSpots(destinationId).spots }.getOrNull() ?: return@launch
            val cards = spots.filter { it.categoryName in TOURIST_CATEGORIES }.map { g ->
                com.buddy.app.core.data.model.ApiPlaceCard(
                    id = g.id, name = g.name, destinationId = destinationId, destinationName = _state.value.hero?.name,
                    lat = g.lat, lng = g.lng, coverUrl = g.coverUrl, coverUrls = g.coverUrl?.let { listOf(it) },
                    category = g.categoryName, photoCount = if (g.coverUrl == null) 0 else 1,
                )
            }
            _state.update { it.copy(touristPlaces = cards) }
        }
    }

    companion object {
        /** Categorías de lugares públicos, sin un negocio detrás. */
        val TOURIST_CATEGORIES = setOf("Naturaleza", "Miradores", "Plaza y centro")
    }

    /** Re-tap de Inicio: otro orden neutral. */
    fun reshuffle() = _state.update { it.copy(reshuffleToken = it.reshuffleToken + 1) }
}
