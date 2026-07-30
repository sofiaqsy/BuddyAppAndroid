package com.buddy.app.features.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.model.ApiJourney
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
        val userLat: Double? = null,
        val userLng: Double? = null,
        val activeMatchId: String? = null,
        val activeBuddyName: String? = null,
        val activeBuddyAvatarUrl: String? = null,
        val lastBuddyMessage: String? = null,
        /** true si el último mensaje lo envié yo → prefijo "Tú:" (estilo WhatsApp). */
        val isLastMessageFromMe: Boolean = false,
        val unreadMessageCount: Int = 0,
        // MARK: – Comunidad viva
        val recentHelp: List<ApiRecentHelp> = emptyList(),  // actividad local en destino
        val communityPulse: List<ApiPulseItem> = emptyList(), // pulso global (fallback)
        val isLoadingCommunity: Boolean = false,
    )

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
                activeMatchId = match?.id,
                activeBuddyName = match?.buddy?.fullName?.split(" ")?.firstOrNull()?.replaceFirstChar { c -> c.uppercase() },
                activeBuddyAvatarUrl = match?.buddy?.avatarUrl,
                lastBuddyMessage = lastMessage,
                isLastMessageFromMe = lastFromMe,
                unreadMessageCount = unreadCount,
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

    /**
     * Carga comunidad viva. Regla: la actividad local SOLO aplica cuando el
     * usuario tiene un trip creado (y su destino tiene actividad de buddies);
     * sin trip → siempre el pulso global (top viajeros por lugar).
     */
    private suspend fun loadCommunityLive() {
        _state.update { it.copy(isLoadingCommunity = true) }
        try {
            val destId = if (_state.value.activeJourney != null) _state.value.destinationId else null
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
                    "transport" -> "Cómo llegar"
                    "food" -> "Comer"
                    "translation" -> "Traducir"
                    "activities" -> "Qué hacer"
                    "accommodation" -> "Alojamiento"
                    "emergency" -> "Seguridad"
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
