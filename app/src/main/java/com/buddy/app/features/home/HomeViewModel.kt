package com.buddy.app.features.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.data.model.ApiPulseItem
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.core.location.DistanceResolver
import com.buddy.app.core.location.LocationFilter
import com.buddy.app.core.location.UserLocation
import com.buddy.app.features.home.data.SpotsRepository
import kotlinx.coroutines.Job
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
 * Desde dónde se construye el próximo Help Request en el composer de Home.
 * Lo decide el GPS (ver effectiveHomeContext); Trip solo queda como reserva
 * para cuando no hay ubicación resuelta. Espejo de HomeContext (iOS).
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
    private val spotsRepo: SpotsRepository,
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
        val communityPulse: List<ApiPulseItem> = emptyList(), // pulso global (fallback)
        val isLoadingCommunity: Boolean = false,
        /** Destino que resolvió el backend para el GPS. */
        val gpsDestinationId: String? = null,
        val gpsDestinationName: String? = null,
        /** Última ubicación que pasó LocationFilter — las distancias de las cards
         *  usan esta; userLat/userLng siguen siendo el último fix para el resto. */
        val stableLat: Double? = null,
        val stableLng: Double? = null,
        /** Lugar más cercano con margen: solo esa card puede decir "Estás aquí". */
        val nearestSpotId: String? = null,
        /** "Ahora en X" cuando el destino resuelto CAMBIA. */
        val locationChangeMessage: String? = null,
    ) {
        val hasCurrentLocationContext: Boolean get() = gpsDestinationId != null

        /**
         * El GPS MANDA. Antes había un selector para elegir entre "Ubicación
         * actual" y cada trip vivo; se quitó igual que en iOS: con el GPS
         * resolviendo bien aparecía en cuanto había un trip en otra ciudad,
         * una decisión que el viajero no había pedido tomar.
         *   GPS resuelto        → CurrentLocation
         *   sin GPS + trip(s)   → el primer trip vivo
         *   sin GPS + sin trip  → null (flujo de permisos existente)
         */
        val effectiveHomeContext: HomeContext?
            get() = when {
                hasCurrentLocationContext -> HomeContext.CurrentLocation
                liveJourneys.isNotEmpty() -> HomeContext.Trip(liveJourneys.first().id)
                else -> null
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
        observeSpots()
        load()
        observeRealtime()
        trackLocation()
    }

    /** La lista, el más cercano y la carga viven en SpotsRepository. */
    private fun observeSpots() {
        viewModelScope.launch { spotsRepo.spots.collect { cards -> _state.update { it.copy(exploreCards = cards) } } }
        viewModelScope.launch { spotsRepo.nearestId.collect { id -> _state.update { it.copy(nearestSpotId = id) } } }
        viewModelScope.launch {
            spotsRepo.isLoading.collect { cargando ->
                // Esqueleto solo si no hay nada que mostrar: con cache la lista
                // se ve al instante y se refresca detrás.
                _state.update { it.copy(isLoadingExplore = cargando && it.exploreCards.isEmpty()) }
            }
        }
    }

    private var trackingJob: Job? = null
    private var lastQueryLat: Double? = null
    private var lastQueryLng: Double? = null

    /**
     * Sigue al viajero. Cada fix aceptado reordena los spots en memoria (sin
     * red); la red solo va cada 150 m. Espejo del onChange del GPS en InicioView.
     */
    private fun trackLocation() {
        if (trackingJob?.isActive == true || !locationProvider.hasPermission()) return
        trackingJob = viewModelScope.launch {
            var hasStable = false
            locationProvider.updates().collect { fix ->
                if (!LocationFilter.accept(fix.accuracy, hasStable)) {
                    Log.d(TAG, "📡 [gps] descartado ±${fix.accuracy?.toInt()}m (umbral ${LocationFilter.MAX_ACCURACY_M.toInt()}m)")
                    return@collect
                }
                hasStable = true
                _state.update { it.copy(stableLat = fix.lat, stableLng = fix.lng, userLat = fix.lat, userLng = fix.lng) }
                spotsRepo.reorder(fix.lat, fix.lng)

                val prevLat = lastQueryLat
                val prevLng = lastQueryLng
                val movido = if (prevLat != null && prevLng != null)
                    DistanceResolver.meters(prevLat, prevLng, fix.lat, fix.lng) else null
                if (movido == null || movido > LOCATION_REFRESH_METERS) {
                    Log.d(TAG, "🏠 [gps] ${movido?.let { "${it.toInt()}m" } ?: "primer fix"} desde la última consulta → refresco ubicación + spots")
                    lastQueryLat = fix.lat
                    lastQueryLng = fix.lng
                    launch { runCatching { refreshCommunityContext(UserLocation(fix.lat, fix.lng)) } }
                    launch { spotsRepo.refresh(fix.lat, fix.lng, "gps") }
                }
            }
        }
    }

    /** La app pasó a segundo plano: apagar el GPS. Nadie mira el Home y el
     *  flujo seguía vivo en viewModelScope con la app minimizada. */
    fun pauseTracking() {
        if (trackingJob?.isActive == true) Log.d(TAG, "📡 [gps] tracking OFF (background)")
        trackingJob?.cancel()
        trackingJob = null
    }

    /** De vuelta al frente: reanudar. trackLocation ya ignora si sigue activo. */
    fun resumeTracking() {
        if (trackingJob?.isActive != true) Log.d(TAG, "📡 [gps] tracking ON")
        trackLocation()
    }

    fun consumeLocationChangeMessage() = _state.update { it.copy(locationChangeMessage = null) }

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

    private var loadJob: Job? = null
    private var lastLoadAt = 0L

    /**
     * Una carga completa del Home a la vez. init ya llama a load(), y al
     * arrancar con el permiso concedido onPermissionResult(true) volvía a
     * llamarla: dos rondas enteras de trip, match, contexto, comunidad y feed.
     * Si hay una en vuelo o terminó hace menos de 5 s se ignora; los gestos
     * explícitos (reintentar, pull to refresh) pasan force = true.
     */
    fun load(force: Boolean = false) {
        val edadMs = System.currentTimeMillis() - lastLoadAt
        if (!force && (loadJob?.isActive == true || edadMs < 5_000)) {
            Log.d(TAG, "load ignorado — ${if (loadJob?.isActive == true) "ya hay una carga en vuelo" else "última hace ${edadMs / 1000}s"}")
            return
        }
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                travelerRepo.ensureSession()
                loadTripAndMatch()
                // Con permiso los spots los pide el primer fix, con coordenadas.
                // Pedirlos aquí también era la petición duplicada del arranque.
                if (!locationProvider.hasPermission()) launch { spotsRepo.refresh(null, null, "load") }
                refreshCommunityContext()
                loadCommunityLive()  // Cargar comunidad viva en paralelo
                refreshOpenRequest()
                loadFeed()
                lastLoadAt = System.currentTimeMillis()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        if (granted) { load(); trackLocation() }
        else _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
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
     * Resuelve el GPS y DESPUÉS decide: si hay destino resuelto manda el GPS;
     * el trip solo es reserva sin ubicación. [known] evita otra lectura
     * puntual cuando ya viene de trackLocation.
     *
     * Un fallo de red al resolver NO borra la ubicación anterior: borrarla
     * mandaba el Home a modo pioneer por un túnel.
     */
    private suspend fun refreshCommunityContext(known: UserLocation? = null) {
        val prevId = _state.value.gpsDestinationId
        val prevName = _state.value.gpsDestinationName
        var gpsDestId: String? = null
        var gpsDestName: String? = null
        if (locationProvider.hasPermission()) {
            val loc = known ?: locationProvider.currentLocation()
            if (loc != null) {
                _state.update { it.copy(userLat = loc.lat, userLng = loc.lng) }
                Log.d(TAG, "resolving location lat=${loc.lat} lng=${loc.lng}")
                val res = runCatching { api.resolveLocation(ResolveRequest(loc.lat, loc.lng)) }.getOrNull()
                if (res == null) {
                    Log.w(TAG, "resolveLocation falló por red — conservo ${prevName ?: "nil"}")
                    gpsDestId = prevId
                    gpsDestName = prevName
                } else {
                    val resolution = if (res.code() == 204) null else res.body()
                    gpsDestId = resolution?.destinationId
                    gpsDestName = resolution?.destinationName
                }
            }
        }
        // Aviso solo en el CAMBIO, no en la primera resolución.
        val aviso = if (prevId != null && gpsDestId != null && prevId != gpsDestId) "Ahora en $gpsDestName" else null
        if (aviso != null) Log.d(TAG, "🏠 [location] $prevName → $gpsDestName")
        _state.update {
            it.copy(
                gpsDestinationId = gpsDestId,
                gpsDestinationName = gpsDestName,
                locationChangeMessage = aviso ?: it.locationChangeMessage,
            )
        }

        if (gpsDestId != null) {
            // Con el GPS: buddies que CUBREN este punto (migración 018), no solo
            // los que tienen el destino en su lista.
            val s = _state.value
            val ctx = runCatching {
                api.placeContext(gpsDestId, source = "destination",
                    lat = s.stableLat ?: s.userLat, lng = s.stableLng ?: s.userLng)
            }.getOrNull()
                ?: ApiPlaceContext(0, 0, 0, "pioneer")
            Log.d(TAG, "resolved $gpsDestName → buddies=${ctx.buddies}")
            _state.update {
                it.copy(isLoading = false, destinationId = gpsDestId, destinationName = gpsDestName, communityContext = ctx)
            }
            return
        }

        val journey = _state.value.liveJourneys.firstOrNull()
        if (journey != null) {
            val destId = journey.destination?.id ?: journey.destinationId
            val ctx = destId?.let { runCatching { api.placeContext(it, "destination") }.getOrNull() }
                ?: ApiPlaceContext(0, 0, 0, "pioneer")
            _state.update {
                it.copy(isLoading = false, destinationId = destId, destinationName = journey.destination?.name, communityContext = ctx)
            }
            return
        }

        if (!locationProvider.hasPermission()) {
            _state.update { it.copy(isLoading = false, needsLocationPermission = true) }
        } else {
            _state.update {
                it.copy(isLoading = false, destinationId = null, destinationName = null, communityContext = ApiPlaceContext(0, 0, 0, "pioneer"))
            }
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

    /**
     * Comunidad viva: SIEMPRE el pulso global (espejo de iOS).
     *
     * Antes anteponía la actividad local del destino del trip y solo caía al
     * pulso si no había ninguna. Se abandonó esa regla: la sección existe para
     * mostrar que la red está viva, y con la actividad local decía justo lo
     * contrario en los destinos tranquilos —que es donde más falta hace—,
     * además de cambiar de contenido al cambiar de selector sin que nada en la
     * sección explicara por qué.
     */
    private suspend fun loadCommunityLive() {
        _state.update { it.copy(isLoadingCommunity = true) }
        try {
            val pulse = runCatching { api.communityPulse() }.getOrNull()
            _state.update {
                it.copy(
                    communityPulse = pulse?.items ?: it.communityPulse,
                    isLoadingCommunity = false,
                )
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
        // El sobre nuevo primero: sin esto la línea del CTA mostraría el JSON
        // crudo del mensaje.
        com.buddy.app.core.data.model.ChatCard.resumen(content)?.let { return it }
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

    companion object {
        private const val TAG = "HomeVM"
        /** Cuánto hay que moverse para volver a preguntar ubicación y spots. */
        private const val LOCATION_REFRESH_METERS = 150.0
    }
}
