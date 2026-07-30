package com.buddy.app.features.conexiones

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.TravelerAlias
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.network.SseClient
import com.buddy.app.features.matching.data.ApiBuddyOffer
import com.buddy.app.features.matching.data.ApiHelpRequest
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.ApiMessage
import com.buddy.app.features.matching.data.MatchingRepository
import com.buddy.app.features.messages.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val CATEGORY_LABELS = mapOf(
    "transport" to "Cómo llegar", "food" to "Comer", "translation" to "Traducir",
    "activities" to "Qué hacer", "accommodation" to "Alojamiento",
    "emergency" to "Seguridad", "general" to "Ayuda",
)

/**
 * Espejo de ChatStore.ConnectionItem (iOS) — TODAS sus validaciones:
 * rol buddy/viajero por travelerId, "Tú:" cuando el último mensaje es mío,
 * unread por read_at, previews especiales (audio, location:, place:,
 * category_card:) y hora estilo WhatsApp (hoy → HH:mm, Ayer, d/M).
 */
data class ConnectionItem(
    val match: ApiMatch,
    val lastMessage: ApiMessage?,
    val unreadCount: Int,
    private val myTravelerId: String?,
) {
    val id: String get() = match.id

    /** Cuando YO soy el buddy, "la otra persona" es el viajero. */
    val isBuddyRole: Boolean get() = myTravelerId != null && match.buddyId == myTravelerId

    val displayName: String
        get() = if (isBuddyRole) {
            TravelerAlias.shortDisplayName(match.traveler?.fullName, match.traveler?.id ?: match.travelerId)
        } else {
            TravelerAlias.shortDisplayName(match.buddy?.fullName, match.buddy?.id ?: match.buddyId)
        }

    val avatarUrl: String? get() = if (isBuddyRole) match.traveler?.avatarUrl else match.buddy?.avatarUrl

    /**
     * Desde dónde piden ayuda, con la categoría: "Lima · Transporte".
     * Un buddy puede estar atendiendo varias ciudades a la vez, así que sin
     * esto dos conversaciones abiertas se ven iguales.
     */
    val contextLine: String?
        get() {
            val place = match.helpRequest?.destination?.name
            val category = match.helpRequest?.category?.let { CATEGORY_LABELS[it] ?: it }
            val parts = listOfNotNull(place, category).filter { it.isNotBlank() }
            return if (parts.isEmpty()) null else parts.joinToString(" · ")
        }

    /** El otro respondió y yo aún no contesto (cuenta para el badge del tab). */
    val pendingReply: Boolean
        get() = lastMessage != null && myTravelerId != null && lastMessage.senderId != myTravelerId

    val isLastFromMe: Boolean
        get() = lastMessage != null && myTravelerId != null && lastMessage.senderId == myTravelerId

    val lastText: String
        get() {
            val msg = lastMessage ?: return "Nueva conexión"
            if (msg.type == "audio") return "Mensaje de voz"
            val content = msg.content.orEmpty()
            return when {
                content.startsWith("location:") -> "Ubicación actual"
                content.startsWith("place:") -> {
                    val parts = content.removePrefix("place:").split("|")
                    if (parts.size > 2) "📍 ${parts[2]}" else "Lugar compartido"
                }
                content.startsWith("category_card:") -> {
                    val key = content.removePrefix("category_card:")
                    val label = CATEGORY_LABELS[key] ?: key
                    val verb = if (isBuddyRole) "Necesita" else "Necesito"
                    "$verb ayuda con $label"
                }
                else -> content
            }
        }

    val lastTime: String
        get() {
            val date = lastMessage?.createdAt?.let {
                runCatching { OffsetDateTime.parse(it) }.getOrNull()
            } ?: return ""
            val local = date.atZoneSameInstant(java.time.ZoneId.systemDefault())
            val today = LocalDate.now()
            return when (local.toLocalDate()) {
                today -> local.format(DateTimeFormatter.ofPattern("HH:mm"))
                today.minusDays(1) -> "Ayer"
                else -> local.format(DateTimeFormatter.ofPattern("d/M"))
            }
        }
}

@HiltViewModel
class ConexionesViewModel @Inject constructor(
    private val repo: MatchingRepository,
    private val chatRepo: ChatRepository,
    private val sessionStore: SessionStore,
    private val sse: SseClient,
) : ViewModel() {

    data class State(
        val hasLoadedOnce: Boolean = false,
        val connections: List<ConnectionItem> = emptyList(),
        val offers: List<ApiBuddyOffer> = emptyList(),
        val totalUnread: Int = 0,
        val acceptingOfferId: String? = null,
        val decliningOfferId: String? = null,
        /**
         * Solicitudes en la cobertura que NO eran la oferta oficial de este
         * buddy cuando se consultó al servidor. Sin topar — el tope se aplica
         * en [availableHelp], tras descartar las que ya subieron a [offers].
         */
        val availableHelpPool: List<ApiHelpRequest> = emptyList(),
        /** Ancla para que las tarjetas cuenten hacia atrás sin repreguntar. */
        val availableHelpFetchedAtMs: Long = 0L,
        val acceptingHelpId: String? = null,
        /** requestId → mensaje, cuando aceptar falló (ya tomada, o aún bloqueada). */
        val helpError: Pair<String, String>? = null,
        /** Match recién aceptado → abrir su chat de inmediato (como iOS). */
        val openMatch: ApiMatch? = null,
    ) {
        private val active get() = connections.filter { it.match.status in listOf("pending", "accepted", "active") }
        /** Activos donde YO ayudo → ACOMPAÑAMIENTO ABIERTO. */
        val activeAsBuddy: List<ConnectionItem> get() = active.filter { it.isBuddyRole }
        /** Activos donde ME ayudan → VÍNCULO ABIERTO. */
        val activeAsTraveler: List<ConnectionItem> get() = active.filter { !it.isBuddyRole }
        /** ENCUENTROS ANTERIORES. */
        val past: List<ConnectionItem> get() = connections.filter { it.match.status == "completed" }

        /**
         * OPORTUNIDADES PARA AYUDAR. Se deriva en vez de almacenarse para que
         * una solicitud NUNCA salga en dos secciones a la vez: si el matching
         * escaló hacia este buddy y ya es su oferta oficial, desaparece de aquí
         * en el mismo instante, sin depender de que ambas listas se hayan
         * refrescado en el mismo ciclo.
         */
        val availableHelp: List<ApiHelpRequest>
            get() {
                val mine = offers.flatMap { listOfNotNull(it.requestId, it.helpRequest?.id) }.toSet()
                return availableHelpPool.filter { it.id !in mine }.take(3)
            }

        val isEmpty: Boolean
            get() = connections.isEmpty() && offers.isEmpty() && availableHelp.isEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sse.events("stream").collect { ev ->
                // El viajero canceló, o ya la tomó otro buddy: la tarjeta se
                // retira al instante y sin red. Seguir ofreciendo ayuda que ya
                // no existe es peor que esperar, porque el buddy toca y se
                // lleva un error.
                val closedId = if (ev.event == "request_closed") {
                    runCatching { sseJson.decodeFromString<RequestClosed>(ev.data).requestId }.getOrNull()
                } else null
                if (closedId != null) removeAvailableHelp(closedId) else load()
            }
        }
        // "Oportunidades para ayudar" no tiene canal push propio (a diferencia
        // de las ofertas oficiales, que llegan por notificación), así que se
        // refresca sola mientras la pantalla vive.
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                refreshAvailableHelp()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val myId = sessionStore.current()?.travelerId
                val matches = repo.matches()
                val cached = _state.value.connections.associateBy { it.match.id }
                val items = coroutineScope {
                    matches.map { match ->
                        async {
                            // Completed no acumula mensajes nuevos ni unread:
                            // reusa el último mensaje cacheado (evita N fetches).
                            if (match.status == "completed") {
                                ConnectionItem(match, cached[match.id]?.lastMessage, 0, myId)
                            } else {
                                val msgs = runCatching { chatRepo.messages(match.id) }.getOrDefault(emptyList())
                                    .sortedBy { it.createdAt ?: "" }
                                val unread = msgs.count { it.senderId != myId && it.readAt == null }
                                ConnectionItem(match, msgs.lastOrNull(), unread, myId)
                            }
                        }
                    }.awaitAll()
                }.sortedByDescending { it.lastMessage?.createdAt ?: "" }
                val offers = runCatching { repo.myOffers() }.getOrDefault(emptyList())
                val available = runCatching { repo.availableHelp() }.getOrDefault(emptyList())
                _state.update { s ->
                    s.copy(
                        hasLoadedOnce = true,
                        connections = items,
                        offers = offers,
                        availableHelpPool = sortAvailable(available),
                        availableHelpFetchedAtMs = System.currentTimeMillis(),
                        totalUnread = items.count {
                            it.match.status in listOf("pending", "accepted", "active") && it.pendingReply
                        } + offers.size,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(hasLoadedOnce = true) }
            }
        }
    }

    /** Aceptar solicitud — al éxito abre el chat con el viajero (como iOS). */
    /**
     * Descarta la oferta oficial propia (ya vive en `offers`) y ordena las
     * liberadas primero, luego por cercanía a liberarse.
     */
    private fun sortAvailable(fetched: List<ApiHelpRequest>): List<ApiHelpRequest> =
        fetched
            .filter { it.isActive && it.isPriorityForMe != true }
            .sortedWith(
                compareBy<ApiHelpRequest> { it.isCommunityUnlocked != true }
                    .thenBy { it.communityUnlocksIn ?: 0 },
            )

    /** Recarga ligera: solo esta lista, no matches ni mensajes. */
    fun refreshAvailableHelp() {
        viewModelScope.launch {
            val fetched = runCatching { repo.availableHelp() }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    availableHelpPool = sortAvailable(fetched),
                    availableHelpFetchedAtMs = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun removeAvailableHelp(requestId: String) {
        _state.update { s ->
            if (s.availableHelpPool.none { it.id == requestId }) s
            else s.copy(availableHelpPool = s.availableHelpPool.filterNot { it.id == requestId })
        }
    }

    fun acceptAvailableHelp(item: ApiHelpRequest) {
        if (_state.value.acceptingHelpId != null) return
        _state.update { it.copy(acceptingHelpId = item.id, helpError = null) }
        viewModelScope.launch {
            runCatching { repo.acceptOffer(item.id) }
                .onSuccess { match ->
                    _state.update { it.copy(acceptingHelpId = null, openMatch = match) }
                    load()
                }
                .onFailure { e ->
                    // El servidor vuelve a validar la ventana de exclusividad y
                    // la carrera entre buddies; el botón deshabilitado es solo
                    // UX, no la barrera.
                    val msg = when ((e as? HttpException)?.code()) {
                        409 -> "Ya fue tomada"
                        403 -> "Aún tiene prioridad otro buddy"
                        else -> "No se pudo aceptar"
                    }
                    Log.w(TAG, "acceptAvailableHelp failed", e)
                    _state.update { it.copy(acceptingHelpId = null, helpError = item.id to msg) }
                    refreshAvailableHelp()
                }
        }
    }

    fun acceptOffer(offer: ApiBuddyOffer) {
        val requestId = offer.helpRequest?.id ?: return
        if (_state.value.acceptingOfferId != null || _state.value.decliningOfferId != null) return
        _state.update { it.copy(acceptingOfferId = offer.id) }
        viewModelScope.launch {
            runCatching { repo.acceptOffer(requestId) }
                .onSuccess { match ->
                    _state.update { it.copy(acceptingOfferId = null, openMatch = match) }
                    load()
                }
                .onFailure {
                    Log.e(TAG, "accept failed", it)
                    _state.update { it.copy(acceptingOfferId = null) }
                }
        }
    }

    fun declineOffer(offer: ApiBuddyOffer) {
        if (_state.value.acceptingOfferId != null || _state.value.decliningOfferId != null) return
        _state.update { it.copy(decliningOfferId = offer.id) }
        viewModelScope.launch {
            runCatching { repo.declineOffer(offer.requestId) }
            _state.update { it.copy(decliningOfferId = null) }
            load()
        }
    }

    fun clearOpenMatch() = _state.update { it.copy(openMatch = null) }

    companion object { private const val TAG = "ConexionesVM" }
}

private val sseJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class RequestClosed(@SerialName("request_id") val requestId: String? = null)
