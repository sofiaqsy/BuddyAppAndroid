package com.buddy.app.features.messages

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.app.core.data.SessionStore
import com.buddy.app.core.location.LocationProvider
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.ApiMessage
import com.buddy.app.features.matching.data.ApiPlace
import com.buddy.app.features.messages.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Chat de un match — espejo 1:1 de BuddyChatView (iOS):
 * historial + paginación, SSE (message / presence / match), envío idempotente,
 * ubicación, imagen, audio (MediaRecorder), cierre con encuesta y reporte.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: ChatRepository,
    private val store: SessionStore,
    private val locationProvider: LocationProvider,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val messages: List<ApiMessage> = emptyList(),
        val myTravelerId: String? = null,
        val isSending: Boolean = false,
        val sendFailed: Boolean = false,
        /** Texto restaurable tras un fallo de envío (iOS restaura inputText). */
        val failedDraft: String? = null,
        val match: ApiMatch? = null,
        val matchStatus: String = "",
        /** true mientras el otro participante tiene SSE abierto (event: presence). */
        val buddyIsOnline: Boolean = false,
        // Paginación
        val hasMoreMessages: Boolean = true,
        val isLoadingMore: Boolean = false,
        // Adjuntos
        val isSendingImage: Boolean = false,
        val isSendingLocation: Boolean = false,
        val locationFailed: Boolean = false,
        // Audio
        val isRecording: Boolean = false,
        val recordSeconds: Int = 0,
        /** Ruta local de la burbuja optimista de audio mientras sube. */
        val pendingAudioPath: String? = null,
        // Cierre / reporte
        val closeCardDismissed: Boolean = false,
        val reportSent: Boolean = false,
        /** true cuando el match quedó cerrado por este usuario → la UI hace dismiss. */
        val closedByMe: Boolean = false,
        // Locaciones del trip (place picker)
        val places: List<ApiPlace> = emptyList(),
        val isLoadingPlaces: Boolean = false,
        val destinationId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var sseJob: Job? = null
    private var markReadJob: Job? = null
    private var matchId: String? = null
    private var pendingSendKey: String = ""
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── Grabación de audio (espejo de AudioRecorderVM) ──────────────────────
    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null
    private var recordTimerJob: Job? = null
    private var recordCancelled = false

    fun open(matchId: String, initialCategory: String? = null) {
        if (this.matchId == matchId) {
            // Re-apertura del mismo chat (p.ej. nueva intención desde la home):
            // card optimista al instante, refrescar y enviar sin reiniciar el SSE.
            viewModelScope.launch {
                val tempId = appendTempCategoryCard(initialCategory)
                refresh()
                sendCategoryCard(matchId, initialCategory, tempId)
                repo.markRead(matchId)
            }
            return
        }
        this.matchId = matchId
        viewModelScope.launch {
            _state.update { it.copy(myTravelerId = store.current()?.travelerId) }
            // Card optimista ANTES de cualquier red — la intención se ve al
            // instante mientras el historial y el envío real llegan detrás.
            val tempId = appendTempCategoryCard(initialCategory)
            // El match completo (buddy/traveler/status/fechas) — iOS lo recibe como
            // parámetro; aquí lo resolvemos por id desde /matching/matches.
            val match = runCatching { repo.matches() }.getOrDefault(emptyList())
                .firstOrNull { it.id == matchId }
            _state.update { it.copy(match = match, matchStatus = match?.status ?: "") }
            refresh()
            // category_card como primer mensaje al llegar desde el matching (iOS:
            // se envía ANTES de abrir el SSE para evitar el duplicado por eco).
            sendCategoryCard(matchId, initialCategory, tempId)
            startSse(matchId)
            repo.markRead(matchId)
            // destinationId para el place picker (como fetchHelpRequestInfo en iOS)
            if (match != null) {
                val info = runCatching { repo.requestInfo(match.requestId) }.getOrNull()
                _state.update { it.copy(destinationId = info?.destinationId) }
            }
        }
    }

    /** Burbuja optimista de la card — visible de inmediato, id temp- hasta el eco real. */
    private fun appendTempCategoryCard(category: String?): String? {
        if (category == null || category == "general") return null
        val tempId = "temp-${UUID.randomUUID()}"
        val temp = ApiMessage(
            id = tempId,
            senderId = _state.value.myTravelerId,
            type = "text",
            content = "category_card:$category",
            createdAt = java.time.Instant.now().toString(),
        )
        _state.update { it.copy(messages = it.messages + temp) }
        return tempId
    }

    private suspend fun sendCategoryCard(matchId: String, category: String?, tempId: String?) {
        if (category == null || category == "general") return
        runCatching { repo.send(matchId, "category_card:$category") }
            .onSuccess { msg ->
                _state.update { s ->
                    val without = s.messages.filterNot { it.id == tempId }
                    if (without.any { it.id == msg.id }) s.copy(messages = without)
                    else s.copy(messages = without + msg)
                }
            }
            .onFailure {
                _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == tempId }) }
            }
    }

    private fun startSse(matchId: String) {
        sseJob?.cancel()
        sseJob = viewModelScope.launch {
            repo.messageStream(matchId).collect { event ->
                when (event.event) {
                    "presence" -> handlePresence(event.data)
                    "match" -> handleMatchEvent(event.data)
                    else -> handleIncomingMessage(event.data)
                }
            }
        }
    }

    /** event: presence — {"userId": "...", "status": "online"|"offline"} */
    private fun handlePresence(data: String) {
        runCatching {
            val obj = json.parseToJsonElement(data).jsonObject
            val userId = obj["userId"]?.jsonPrimitive?.content ?: return
            val status = obj["status"]?.jsonPrimitive?.content ?: return
            if (userId != _state.value.myTravelerId) {
                _state.update { it.copy(buddyIsOnline = status == "online") }
            }
        }
    }

    /** event: match — el otro lado cerró/canceló → input pasa a "Conexión cerrada". */
    private fun handleMatchEvent(data: String) {
        runCatching {
            val obj = json.parseToJsonElement(data).jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return
            val status = obj["status"]?.jsonPrimitive?.content ?: return
            if (id == matchId && status in listOf("completed", "cancelled")) {
                _state.update { it.copy(matchStatus = status) }
            }
        }
    }

    private fun handleIncomingMessage(data: String) {
        runCatching { json.decodeFromString<ApiMessage>(data) }
            .onSuccess { msg ->
                _state.update { s ->
                    if (s.messages.any { it.id == msg.id }) s
                    else {
                        // Eco de un mensaje propio → retirar la burbuja optimista equivalente
                        val cleaned = if (msg.senderId != null && msg.senderId == s.myTravelerId)
                            s.messages.filterNot { it.id.startsWith("temp-") && it.content == msg.content }
                        else s.messages
                        s.copy(
                            messages = cleaned + msg,
                            // Llegó el audio real por SSE → retirar burbuja optimista
                            pendingAudioPath = if (msg.type == "audio") null else s.pendingAudioPath,
                        )
                    }
                }
                scheduleMarkRead()
            }
    }

    // Debounce: ráfaga de N mensajes = 1 PATCH (300ms, como iOS)
    private fun scheduleMarkRead() {
        val id = matchId ?: return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(300)
            repo.markRead(id)
        }
    }

    private suspend fun refresh() {
        val id = matchId ?: return
        runCatching { repo.messages(id) }
            .onSuccess { msgs ->
                val sorted = msgs.sortedBy { m -> m.createdAt ?: "" }
                _state.update { s ->
                    // Preservar burbujas optimistas (temp-) al final del historial
                    val temps = s.messages.filter { it.id.startsWith("temp-") }
                    s.copy(
                        isLoading = false,
                        messages = sorted + temps,
                        hasMoreMessages = msgs.size == 30,
                    )
                }
            }
            .onFailure { Log.e(TAG, "messages failed", it) }
    }

    /** Paginación hacia atrás — before = createdAt del mensaje más antiguo. */
    fun loadMore() {
        val id = matchId ?: return
        val s = _state.value
        if (!s.hasMoreMessages || s.isLoadingMore) return
        val oldest = s.messages.firstOrNull()?.createdAt ?: return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repo.messages(id, limit = 30, before = oldest) }
                .onSuccess { fetched ->
                    _state.update { cur ->
                        if (fetched.isEmpty()) cur.copy(isLoadingMore = false, hasMoreMessages = false)
                        else {
                            val existing = cur.messages.map { it.id }.toSet()
                            val older = fetched.sortedBy { it.createdAt ?: "" }.filter { it.id !in existing }
                            cur.copy(
                                isLoadingMore = false,
                                messages = older + cur.messages,
                                hasMoreMessages = fetched.size == 30,
                            )
                        }
                    }
                }
                .onFailure {
                    Log.e(TAG, "loadMore failed", it)
                    _state.update { cur -> cur.copy(isLoadingMore = false) }
                }
        }
    }

    fun send(content: String) {
        val id = matchId ?: return
        val text = content.trim()
        if (text.isEmpty()) return
        // Idempotencia de retry: la clave se reutiliza si el envío anterior falló
        if (pendingSendKey.isEmpty()) pendingSendKey = UUID.randomUUID().toString()
        val key = pendingSendKey
        viewModelScope.launch {
            _state.update { it.copy(isSending = true) }
            runCatching { repo.send(id, text, key) }
                .onSuccess { msg ->
                    pendingSendKey = ""
                    _state.update { s ->
                        if (s.messages.any { it.id == msg.id }) s.copy(isSending = false)
                        else s.copy(isSending = false, messages = s.messages + msg)
                    }
                }
                .onFailure {
                    Log.e(TAG, "send failed", it)
                    _state.update { s -> s.copy(isSending = false, sendFailed = true, failedDraft = text) }
                }
        }
    }

    fun dismissSendFailed() = _state.update { it.copy(sendFailed = false, failedDraft = null) }
    fun dismissLocationFailed() = _state.update { it.copy(locationFailed = false) }
    fun dismissCloseCard() = _state.update { it.copy(closeCardDismissed = true) }
    fun dismissReportToast() = _state.update { it.copy(reportSent = false) }

    // ── Ubicación / imagen ───────────────────────────────────────────────────

    fun sendLocation() {
        val id = matchId ?: return
        viewModelScope.launch {
            val loc = if (locationProvider.hasPermission()) locationProvider.currentLocation() else null
            if (loc == null) {
                _state.update { it.copy(locationFailed = true) }
                return@launch
            }
            _state.update { it.copy(isSendingLocation = true) }
            runCatching { repo.send(id, "location:${loc.lat},${loc.lng}") }
                .onSuccess { msg ->
                    _state.update { s ->
                        if (s.messages.any { it.id == msg.id }) s else s.copy(messages = s.messages + msg)
                    }
                }
            _state.update { it.copy(isSendingLocation = false) }
        }
    }

    fun sendPlace(name: String, lat: Double, lng: Double) {
        val id = matchId ?: return
        viewModelScope.launch {
            runCatching { repo.send(id, "place:$lat|$lng|$name|") }
                .onSuccess { msg ->
                    _state.update { s ->
                        if (s.messages.any { it.id == msg.id }) s else s.copy(messages = s.messages + msg)
                    }
                }
        }
    }

    fun sendImage(jpegBytes: ByteArray) {
        val id = matchId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSendingImage = true) }
            runCatching { repo.uploadImage(id, jpegBytes) }
                .onSuccess { msg ->
                    _state.update { s ->
                        if (s.messages.any { it.id == msg.id }) s.copy(isSendingImage = false)
                        else s.copy(isSendingImage = false, messages = s.messages + msg)
                    }
                }
                .onFailure {
                    Log.e(TAG, "sendImage failed", it)
                    _state.update { s -> s.copy(isSendingImage = false, sendFailed = true) }
                }
        }
    }

    fun loadPlaces() {
        val destId = _state.value.destinationId ?: return
        _state.update { it.copy(isLoadingPlaces = true) }
        viewModelScope.launch {
            val places = runCatching { repo.places(destId) }.getOrDefault(emptyList())
            _state.update { it.copy(places = places, isLoadingPlaces = false) }
        }
    }

    // ── Audio (espejo de AudioRecorderVM: start / cancel / stop+send) ───────

    fun startRecording(): Boolean {
        if (_state.value.isRecording) return true
        recordCancelled = false
        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        return try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioSamplingRate(44100)
            rec.setAudioChannels(1)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            recordFile = file
            _state.update { it.copy(isRecording = true, recordSeconds = 0) }
            recordTimerJob?.cancel()
            recordTimerJob = viewModelScope.launch {
                while (isActive) {
                    delay(1000)
                    _state.update { it.copy(recordSeconds = it.recordSeconds + 1) }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed", e)
            file.delete()
            false
        }
    }

    fun cancelRecording() {
        recordCancelled = true
        stopRecorder()
        recordFile?.delete()
        recordFile = null
        _state.update { it.copy(isRecording = false, recordSeconds = 0) }
    }

    fun stopAndSendRecording() {
        stopRecorder()
        val file = recordFile
        recordFile = null
        _state.update { it.copy(isRecording = false, recordSeconds = 0) }
        if (recordCancelled || file == null || !file.exists()) return
        val id = matchId ?: return
        // Burbuja optimista con el archivo local mientras sube (como iOS)
        _state.update { it.copy(pendingAudioPath = file.absolutePath) }
        viewModelScope.launch {
            runCatching { repo.uploadAudio(id, file) }
                .onSuccess { msg ->
                    _state.update { s ->
                        val msgs = if (s.messages.any { it.id == msg.id }) s.messages else s.messages + msg
                        s.copy(messages = msgs, pendingAudioPath = null)
                    }
                    file.delete()
                }
                .onFailure {
                    Log.e(TAG, "audio upload failed", it)
                    _state.update { s -> s.copy(pendingAudioPath = null, sendFailed = true) }
                    file.delete()
                }
        }
    }

    private fun stopRecorder() {
        recordTimerJob?.cancel()
        recordTimerJob = null
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
    }

    // ── Cierre / reporte ─────────────────────────────────────────────────────

    /** Cierre del buddy (quien ayuda): sin encuesta, solo marca completado. */
    fun closeAsHelper() {
        val id = matchId ?: return
        viewModelScope.launch {
            runCatching { repo.completeMatch(id) }
            _state.update { it.copy(matchStatus = "completed", closedByMe = true) }
        }
    }

    /** Cierre del viajero: PATCH completed + encuesta de reputación. */
    fun closeWithFeedback(feeling: String, pressure: String) {
        val id = matchId ?: return
        viewModelScope.launch {
            runCatching { repo.completeMatch(id) }
            runCatching { repo.submitFeedback(id, feeling, pressure) }
            _state.update { it.copy(matchStatus = "completed", closedByMe = true) }
        }
    }

    fun reportUser(reason: String, details: String?) {
        val id = matchId ?: return
        val s = _state.value
        val match = s.match ?: return
        val isBuddy = s.myTravelerId == match.buddyId
        val reportedId = (if (isBuddy) match.traveler?.id else match.buddy?.id) ?: return
        viewModelScope.launch {
            runCatching { repo.reportUser(reportedId, reason, details, id) }
                .onSuccess { _state.update { it.copy(reportSent = true) } }
                .onFailure { Log.e(TAG, "reportUser failed", it) }
        }
    }

    override fun onCleared() {
        stopRecorder()
        recordFile?.delete()
        super.onCleared()
    }

    companion object { private const val TAG = "ChatVM" }
}
