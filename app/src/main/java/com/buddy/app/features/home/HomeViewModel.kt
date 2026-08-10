package com.buddy.app.features.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.data.model.ApiPulseItem
import com.buddy.app.core.data.model.ApiRecentHelp
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.features.authentication.data.TravelerRepository
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.home.data.ResolveRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Contexto explícito elegido por el viajero para el composer de Home: desde
 * dónde se construye el próximo Help Request. Nunca se decide solo — el
 * usuario elige, Home nunca cambia de contexto en silencio. Espejo de
 * HomeContext (iOS). Trip lleva el journey.id — con más de un trip vivo
 * (ej: San Francisco + Villa Rica) cada uno es una opción propia.
 */
sealed interface HomeContext {
    data object CurrentLocation : HomeContext
    data class Trip(val journeyId: String) : HomeContext
}

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
    private val matchingRepo: com.buddy.app.features.matching.data.MatchingRepository,
    private val travelerRepo: TravelerRepository,
    private val locationProvider: LocationProvider,
    private val sessionStore: com.buddy.app.core.data.SessionStore,
    private val sse: com.buddy.app.core.network.SseClient,
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
        /** Todos los trips vivos (active + planning), no solo activeJourney —
         * necesario para ofrecer cada uno como opción propia en el selector. */
        val liveJourneys: List<ApiJourney> = emptyList(),
        val userLat: Double? = null,
        val userLng: Double? = null,
        val activeMatchId: String? = null,
        val activeBuddyName: String? = null,
        val activeBuddyAvatarUrl: String? = null,
        val lastBuddyMessage: String? = null,
        /** true si el último mensaje lo envié yo → prefijo "Tú:" (estilo WhatsApp). */
        val isLastMessageFromMe: Boolean = false,
        val unreadMessageCount: Int = 0,
        // MARK: – Explora {ciudad}
        /** Lugares que los buddies recomiendan por aquí. Cuando hay, reemplazan
         *  la grilla de categorías por el carrusel: la intención ya no se elige
         *  primero, nace después de que un lugar llamó la atención. */
        val exploreCards: List<ApiPlaceCard> = emptyList(),
        val isLoadingExplore: Boolean = true,
        /** Categoría de una solicitud MÍA todavía sin atender. Con esto el CTA
         *  dice que la búsqueda sigue viva en vez de invitar a empezar otra —
         *  la búsqueda pudo arrancar en otra pantalla. */
        val openRequestCategory: String? = null,
        // MARK: – Comunidad viva
        val recentHelp: List<ApiRecentHelp> = emptyList(),  // actividad local en destino
        val communityPulse: List<ApiPulseItem> = emptyList(), // pulso global (fallback)
        val isLoadingCommunity: Boolean = false,
        // MARK: – Selector de contexto Home (Ubicación actual vs Mi viaje)
        /** Destino GPS resuelto, calculado SIEMPRE en paralelo al trip (igual que
         * iOS) — independiente de destinationId/destinationName, que reflejan el
         * contexto EFECTIVO (el elegido), no necesariamente el GPS. */
        val gpsDestinationId: String? = null,
        val gpsDestinationName: String? = null,
        /** Selección manual del usuario. null = sin override, se aplican las
         * reglas de default (ver effectiveHomeContext). */
        val homeContextOverride: HomeContext? = null,
    ) {
        val hasCurrentLocationContext: Boolean get() = gpsDestinationId != null
        val hasTripContext: Boolean get() = liveJourneys.isNotEmpty()
        /** El trip vivo (si alguno) cuyo destino coincide EXACTO con el GPS (ej:
         * trip a San Francisco y ya estás en San Francisco). Con más de un trip
         * vivo, "Ubicación actual" y ese trip serían la MISMA fila repetida —
         * se fusionan: no se ofrece "Ubicación actual" por separado, ese trip
         * cubre ambas cosas. Los demás trips (ej: Villa Rica) siguen siendo
         * opciones propias. */
        val matchingTripForGPS: ApiJourney?
            get() {
                val gpsId = gpsDestinationId ?: return null
                return liveJourneys.firstOrNull { (it.destination?.id ?: it.destinationId) == gpsId }
            }
        /** "Ubicación actual" solo se ofrece como fila propia cuando NO coincide
         * con ninguno de los trips vivos — si coincide, queda fusionada en ese trip. */
        val shouldOfferCurrentLocationOption: Boolean
            get() = hasCurrentLocationContext && matchingTripForGPS == null
        /** Total de opciones distintas que el selector podría ofrecer. */
        val homeContextOptionCount: Int get() = (if (shouldOfferCurrentLocationOption) 1 else 0) + liveJourneys.size

        /**
         * Contexto efectivo del composer. Respeta la selección manual mientras
         * siga siendo válida (el trip elegido sigue vivo, o el GPS ya no
         * coincide con un trip si eligió "Ubicación actual"); si dejó de serlo,
         * recalcula el default — nunca queda "atascado" en un contexto que ya
         * no existe. Reglas:
         *   GPS coincide con un trip vivo → ese trip (fusionado, ver matchingTripForGPS)
         *   GPS + trip(s), sin coincidir  → Ubicación actual (el usuario puede cambiar)
         *   sin GPS + trip(s)             → el primer trip vivo
         *   GPS + sin trip                → Ubicación actual (única opción)
         *   sin GPS + sin trip            → null (flujo de permisos existente)
         */
        val effectiveHomeContext: HomeContext?
            get() {
                val manualStillValid = when (val override = homeContextOverride) {
                    is HomeContext.CurrentLocation -> shouldOfferCurrentLocationOption
                    is HomeContext.Trip -> liveJourneys.any { it.id == override.journeyId }
                    null -> false
                }
                if (manualStillValid) return homeContextOverride

                matchingTripForGPS?.let { return HomeContext.Trip(it.id) }
                return when {
                    hasCurrentLocationContext && hasTripContext -> HomeContext.CurrentLocation
                    liveJourneys.isNotEmpty() -> HomeContext.Trip(liveJourneys.first().id)
                    hasCurrentLocationContext -> HomeContext.CurrentLocation
                    else -> null
                }
            }

        /** El journey correspondiente al contexto efectivo, si es de tipo Trip. */
        val effectiveTripJourney: ApiJourney?
            get() {
                val jid = (effectiveHomeContext as? HomeContext.Trip)?.journeyId ?: return null
                return liveJourneys.firstOrNull { it.id == jid }
            }
    }

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        load()
        observeRealtime()
    }

    /**
     * Tiempo real del Home — espejo de travelerMatchSignature (iOS): el stream
     * SSE global emite match/offer/message; cualquier evento refresca la card
     * "Tu buddy asignado" (aceptación del buddy, último mensaje, no leídos)
     * sin salir del Home. Debounce: una ráfaga de eventos = un refresh.
     */
    private var realtimeRefreshJob: kotlinx.coroutines.Job? = null
    private fun observeRealtime() {
        viewModelScope.launch {
            // request_closed es de la lista "Oportunidades para ayudar" en
            // Conexiones; aquí no cambia nada y solo provocaba un refresh de más.
            sse.events("stream").filter { it.event != "request_closed" }.collect {
                realtimeRefreshJob?.cancel()
                realtimeRefreshJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(800)
                    runCatching { loadTripAndMatch() }
                }
            }
        }
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
                loadTripAndMatch()
                refreshCommunityContext()
                loadCommunityLive()  // Cargar comunidad viva en paralelo
                refreshOpenRequest()
                loadFeed()
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    /**
     * Refresh ligero al volver del chat: el match pudo cerrarse (por mí o por el
     * buddy) — sin él, la card "buddy asignado" queda huérfana en el Home.
     */
    fun refreshTripState() {
        viewModelScope.launch {
            runCatching { loadTripAndMatch() }
            // La búsqueda pudo cerrarse dentro del chat, o haber arrancado en
            // el mapa. Volver al Home es justo el momento de enterarse.
            refreshOpenRequest()
        }
    }

    /**
     * Los lugares del carrusel "Explora {ciudad}".
     *
     * Se piden por GPS y no por el destino elegido: son lugares de POR AQUÍ.
     * Sin coordenadas el backend responde igual —los más recientes—, así que no
     * se bloquea esperando permiso de ubicación.
     */
    /**
     * Los lugares recomendados del destino en el que se está AHORA.
     *
     * Se limpian antes de pedir: mientras llega la respuesta no puede quedarse
     * en pantalla lo del sitio anterior. Al llegar a Miraflores el título decía
     * Miraflores y las fotos seguían siendo de Lima.
     *
     * Sin relleno con destinos vecinos: si aquí no hay nada recomendado, la
     * sección va vacía. Rellenar esconde justo dónde falta contenido.
     */
    private suspend fun loadExploreCards(destinationId: String?) {
        if (cardsDelDestino == destinationId) return
        cardsDelDestino = destinationId
        _state.update { it.copy(exploreCards = emptyList(), isLoadingExplore = destinationId != null) }
        if (destinationId == null) return

        runCatching { api.placeShares(limit = 12, destinationId = destinationId).items }
            .onSuccess { cards ->
                // El destino pudo cambiar mientras volvía: sin esto, la
                // respuesta lenta de un sitio del que ya te fuiste pisaría la
                // del sitio donde estás.
                if (cardsDelDestino != destinationId) {
                    Log.d(TAG, "placeShares de ${destinationId.take(8)} descartado — el destino cambió")
                    return@onSuccess
                }
                Log.d(TAG, "placeShares ${destinationId.take(8)} → ${cards.size}: ${cards.joinToString { c -> c.name }}")
                _state.update { it.copy(exploreCards = cards, isLoadingExplore = false) }
            }
            .onFailure {
                Log.e(TAG, "placeShares failed", it)
                // Un fallo no es "no hay lugares", pero tampoco puede dejar en
                // pantalla los del destino anterior: ya se vaciaron arriba.
                _state.update { st -> st.copy(isLoadingExplore = false) }
            }
    }

    /** De qué destino son las cards que hay ahora en pantalla. */
    private var cardsDelDestino: String? = null

    /**
     * Mi solicitud abierta, si la hay. Alimenta el estado "Buscando buddy…" del
     * CTA — sin esto el Home invita a empezar una búsqueda que ya está viva.
     */
    private suspend fun refreshOpenRequest() {
        runCatching { matchingRepo.myRequest() }
            .onSuccess { req ->
                Log.d(TAG, "openRequest → ${req?.category ?: "ninguna"}")
                _state.update { it.copy(openRequestCategory = req?.category) }
            }
            .onFailure {
                // Un fallo NO se escribe como "no hay solicitud": eso apagaría
                // el estado de búsqueda de alguien que sí está buscando.
                Log.e(TAG, "myRequest failed — conservo el estado anterior", it)
            }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) load()
        else _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
    }

    /** Selección manual del selector de contexto — nunca automática. */
    fun setHomeContext(context: HomeContext) {
        _state.update { it.copy(homeContextOverride = context) }
        // refreshCommunityContext PRIMERO: loadCommunityLive depende de
        // effectiveHomeContext, que a su vez depende del destinationId que
        // recién escribe refreshCommunityContext — sin este orden, Comunidad
        // Viva quedaba mostrando el contexto anterior hasta el próximo ciclo.
        viewModelScope.launch {
            refreshCommunityContext()
            loadCommunityLive()
        }
    }

    /** Espejo de loadData + activeMatch (iOS). */
    private suspend fun loadTripAndMatch() {
        // tripId != null excluye los journeys de "Compartir un lugar" (Fase 2):
        // uno de esos, con status="active" y sin trip, se colaba como si fuera
        // TU viaje en curso en el Home — mismo bug encontrado y arreglado en
        // TripsViewModel, confirmado en logs de dispositivo iOS.
        val journeys = runCatching { api.myJourneys() }.getOrDefault(emptyList()).filter { it.tripId != null }
        val active = journeys.firstOrNull { it.status == "active" }
            ?: journeys.firstOrNull { it.status == "planning" }
        // Todos los trips vivos, activos primero — igual que liveJourneys (iOS).
        val live = journeys
            .filter { it.status == "active" || it.status == "planning" }
            .sortedBy { if (it.status == "active") 0 else 1 }
        val match = if (active != null) {
            runCatching { matchingApi.matches() }.getOrDefault(emptyList())
                .firstOrNull { it.status in listOf("accepted", "active", "pending") }
        } else null

        // Fetch last message from the active match
        var lastMessage: String? = null
        var unreadCount = 0
        var lastFromMe = false
        if (match != null) {
            try {
                val myTravelerId = sessionStore.current()?.travelerId
                val messages = matchingApi.messages(match.id, limit = 1)
                val lastMsg = messages.firstOrNull()
                lastFromMe = lastMsg?.senderId != null && lastMsg.senderId == myTravelerId
                lastMessage = lastMsg?.let { displayText(it, lastFromMe) }
                // Punto rojo = pendiente de respuesta (la última palabra la tiene
                // el buddy) — la MISMA regla del badge del tab Conexiones
                // (pendingReply), no read_at: aunque hayas visto el mensaje, si
                // no contestaste el badge sigue vivo.
                if (lastMsg != null && !lastFromMe) unreadCount = 1
            } catch (e: Exception) {
                Log.d(TAG, "Failed to fetch last message", e)
            }
        }

        _state.update {
            it.copy(
                activeJourney = active,
                liveJourneys = live,
                activeMatchId = match?.id,
                activeBuddyName = match?.buddy?.fullName?.split(" ")?.firstOrNull()?.replaceFirstChar { c -> c.uppercase() },
                activeBuddyAvatarUrl = match?.buddy?.avatarUrl,
                lastBuddyMessage = lastMessage,
                isLastMessageFromMe = lastFromMe,
                unreadMessageCount = unreadCount,
            )
        }
    }

    /**
     * GPS se resuelve SIEMPRE en paralelo al trip (igual que iOS) — quién
     * "gana" (destinationId/destinationName/communityContext) lo decide el
     * contexto EFECTIVO (elegido por el usuario o el default de las 4 reglas),
     * nunca la sola presencia de un trip.
     */
    private suspend fun refreshCommunityContext() {
        var gpsDestId: String? = null
        var gpsDestName: String? = null
        if (locationProvider.hasPermission()) {
            val loc = locationProvider.currentLocation()
            if (loc != null) {
                _state.update { it.copy(userLat = loc.lat, userLng = loc.lng) }
                Log.d(TAG, "resolving location lat=${loc.lat} lng=${loc.lng}")
                val res = api.resolveLocation(ResolveRequest(loc.lat, loc.lng))
                val resolution = if (res.code() == 204) null else res.body()
                if (resolution != null) {
                    gpsDestId = resolution.destinationId
                    gpsDestName = resolution.destinationName
                }
            }
        }
        _state.update { it.copy(gpsDestinationId = gpsDestId, gpsDestinationName = gpsDestName) }

        // El trip elegido en el selector — no necesariamente activeJourney,
        // con 2+ trips vivos puede ser cualquiera.
        val journey = _state.value.effectiveTripJourney
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
            loadExploreCards(destId)
            return
        }

        if (gpsDestId == null) {
            if (!locationProvider.hasPermission()) {
                _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
            } else {
                // Sin match → pioneer mode (0 buddies), igual que iOS
                _state.update {
                    it.copy(
                        isLoading = false,
                        destinationId = null,
                        destinationName = null,
                        communityContext = ApiPlaceContext(0, 0, 0, "pioneer"),
                    )
                }
            }
            // Sin destino no hay nada que recomendar "aquí".
            loadExploreCards(null)
            return
        }
        val ctx = api.placeContext(gpsDestId, source = "destination")
        Log.d(TAG, "resolved $gpsDestName → buddies=${ctx.buddies}")
        _state.update {
            it.copy(
                isLoading = false,
                destinationId = gpsDestId,
                destinationName = gpsDestName,
                communityContext = ctx,
            )
        }
        loadExploreCards(gpsDestId)
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

    /**
     * Carga comunidad viva. Regla: la actividad local SOLO aplica cuando el
     * contexto EFECTIVO es "Mi viaje" (no solo "hay un trip creado" — con
     * "Ubicación actual" elegida, mostrar la actividad de un trip que no es
     * el que se está usando ahora mismo confunde); en cualquier otro caso →
     * siempre el pulso global (top viajeros por lugar).
     */
    private suspend fun loadCommunityLive() {
        _state.update { it.copy(isLoadingCommunity = true) }
        try {
            val destId = if (_state.value.effectiveTripJourney != null) _state.value.destinationId else null
            if (destId != null) {
                // Cargar actividad local del destino del trip
                val recent = runCatching { api.recentHelpByPlace(destId) }.getOrDefault(emptyList())
                if (recent.isNotEmpty()) {
                    _state.update { it.copy(recentHelp = recent, isLoadingCommunity = false) }
                    return
                }
            }
            // Sin trip (o sin actividad local): limpiar restos de actividad y
            // caer al pulso global
            _state.update { it.copy(recentHelp = emptyList()) }
            // Sin actividad local → cargar pulso global
            val pulse = runCatching { api.communityPulse() }.getOrNull()
            if (pulse != null) {
                _state.update { it.copy(communityPulse = pulse.items, isLoadingCommunity = false) }
            } else {
                _state.update { it.copy(isLoadingCommunity = false) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadCommunityLive failed", e)
            _state.update { it.copy(isLoadingCommunity = false) }
        }
    }

    /**
     * Texto legible del último mensaje — espejo de ChatStore.ConnectionItem.lastText
     * (iOS): audio, ubicación, lugar y category_card se muestran con etiqueta,
     * nunca el contenido crudo tipo "category_card:emergency".
     */
    private fun displayText(msg: com.buddy.app.features.matching.data.ApiMessage, fromMe: Boolean): String {
        if (msg.type == "audio" || msg.type == "audio_message") return "Mensaje de voz"
        val content = msg.content ?: ""
        return when {
            content.startsWith("location:") -> "Ubicación actual"
            content.startsWith("place:") -> {
                val parts = content.removePrefix("place:").split("|")
                if (parts.size > 2 && parts[2].isNotBlank()) "📍 ${parts[2]}" else "Lugar compartido"
            }
            content.startsWith("category_card:") -> {
                val key = content.removePrefix("category_card:")
                val label = when (key) {
                    "transport" -> "Transporte"
                    "food" -> "Comer"
                    "shopping" -> "Compras"
                    "translation" -> "Traducir"
                    "activities" -> "Actividades"
                    "accommodation" -> "Alojamiento"
                    "emergency" -> "Seguridad"
                    "recommendations" -> "Consejos"
                    else -> key
                }
                val verb = if (fromMe) "Necesito" else "Necesita"
                "$verb ayuda con $label"
            }
            else -> content
        }
    }

    /** Formatea "hace X tiempo" desde una fecha ISO. */
    fun formatTimeAgo(isoDate: String?): String {
        if (isoDate == null) return "recién"
        return try {
            val instant = Instant.parse(isoDate)
            val seconds = System.currentTimeMillis() / 1000 - instant.epochSecond
            when {
                seconds < 90 -> "hace un momento"
                seconds < 3600 -> "hace ${seconds / 60} min"
                seconds < 86400 -> "hace ${seconds / 3600} h"
                else -> "hace ${seconds / 86400} d"
            }
        } catch (e: Exception) {
            "recién"
        }
    }

    companion object { private const val TAG = "HomeVM" }
}
