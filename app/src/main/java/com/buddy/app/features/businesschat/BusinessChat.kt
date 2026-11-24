package com.buddy.app.features.businesschat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.features.home.data.HomeApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

// ── Modelos (iOS BizCard, BizMessage, BizThread, BizConversation) ───────────

@Serializable
data class BizCard(
    val kind: String,                 // "place" | "product"
    val title: String,
    val subtitle: String? = null,
    val price: String? = null,
    val imageUrl: String? = null,
    val productId: String? = null,
    val spotId: String? = null,
)

@Serializable data class BizMessage(val id: String, val senderRole: String, val body: String, val card: BizCard? = null, val createdAt: String? = null)
@Serializable data class BizThread(val side: String = "traveler", val messages: List<BizMessage> = emptyList(), val blocked: Boolean? = null, val blockedByMe: Boolean? = null)
@Serializable data class BizConversationRef(val id: String, val businessName: String? = null)
@Serializable data class BizConversation(
    val id: String, val farmId: String? = null, val spotId: String? = null, val businessName: String? = null,
    val travelerName: String? = null, val lastMessage: String? = null, val lastSender: String? = null, val lastAt: String? = null,
)
@Serializable data class BizConversationsResponse(val conversations: List<BizConversation> = emptyList())
@Serializable data class BizStartBody(val spotId: String)
@Serializable data class BizSendBody(val body: String, val card: BizCard? = null)
@Serializable data class BizReportBody(val reason: String)
@Serializable class BizEmpty
@Serializable data class BizOk(val ok: Boolean = true)

/** A quién se le escribe: un lugar (nueva o existente) o una conversación ya abierta. */
data class BusinessChatTarget(
    val title: String,
    val spotId: String? = null,
    val conversationId: String? = null,
    val card: BizCard? = null,
)

/** Motivos que acepta el servidor (iOS APIClient.bizReportReasons). */
val BizReportReasons = listOf(
    "spam" to "Spam o publicidad",
    "harassment" to "Acoso o insultos",
    "inappropriate_content" to "Contenido inapropiado",
    "fake_profile" to "Negocio o persona falsa",
    "safety_concern" to "Me preocupa mi seguridad",
    "other" to "Otro motivo",
)

/** Mensaje del servidor en español (p. ej. "Este es tu negocio"); los técnicos no se muestran. */
private fun serverMessage(e: Throwable, fallback: String): String {
    val body = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
    val msg = body?.let { Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
    val technical = setOf("Unknown error", "Route not found", "Not found", "Unauthorized", "Error interno")
    return if (msg != null && msg !in technical) msg else fallback
}

// ── ViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class BusinessChatViewModel @Inject constructor(private val api: HomeApi) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val conversationId: String? = null,
        val side: String = "traveler",
        val messages: List<BizMessage> = emptyList(),
        val pendingCard: BizCard? = null,
        val sending: Boolean = false,
        val blocked: Boolean = false,
        val blockedByMe: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var polling: Job? = null
    private var openedFor: BusinessChatTarget? = null

    fun open(target: BusinessChatTarget) {
        if (openedFor == target) return
        openedFor = target
        _state.value = UiState(pendingCard = target.card)
        viewModelScope.launch {
            try {
                val id = target.conversationId ?: target.spotId?.let { api.bizStart(BizStartBody(it)).id }
                _state.update { it.copy(conversationId = id) }
                reload()
                // Si ya se mandó una tarjeta igual, no se repite.
                _state.update { s -> if (s.messages.any { it.card == s.pendingCard }) s.copy(pendingCard = null) else s }
                startPolling()
            } catch (e: Throwable) {
                _state.update { it.copy(error = serverMessage(e, "No pudimos abrir la conversación. Revisa tu conexión.")) }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    /** Mientras la pantalla está abierta se revisan respuestas nuevas cada 5 s (igual que iOS). */
    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch { while (true) { delay(5_000); reload() } }
    }

    private suspend fun reload() {
        val id = _state.value.conversationId ?: return
        val t = runCatching { api.bizMessages(id) }.getOrNull() ?: return
        _state.update {
            it.copy(side = t.side, blocked = t.blocked ?: false, blockedByMe = t.blockedByMe ?: false,
                    messages = if (t.messages != it.messages) t.messages else it.messages)
        }
    }

    fun removeCard() = _state.update { it.copy(pendingCard = null) }

    fun send(text: String, onSent: () -> Unit) {
        val id = _state.value.conversationId ?: return
        val body = text.trim()
        if (body.isEmpty() || _state.value.sending) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            try {
                val m = api.bizSend(id, BizSendBody(body, _state.value.pendingCard))
                _state.update { it.copy(messages = it.messages + m, pendingCard = null) }
                onSent()
            } catch (e: Throwable) {
                _state.update { it.copy(error = serverMessage(e, "No pudimos enviar. Revisa tu conexión e intenta de nuevo.")) }
            }
            _state.update { it.copy(sending = false) }
        }
    }

    fun report(reason: String) {
        val id = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching { api.bizReport(id, BizReportBody(reason)) }
                .onSuccess { _state.update { it.copy(notice = "Gracias. Revisaremos este reporte.", error = null) } }
                .onFailure { _state.update { it.copy(error = "No pudimos enviar el reporte. Intenta de nuevo.") } }
        }
    }

    fun block() {
        val id = _state.value.conversationId ?: return
        viewModelScope.launch {
            runCatching { api.bizBlock(id) }
                .onSuccess { _state.update { it.copy(blocked = true, blockedByMe = true, error = null) } }
                .onFailure { _state.update { it.copy(error = "No pudimos bloquear. Intenta de nuevo.") } }
        }
    }
}

// ── Pantalla del chat (iOS BusinessChatView): a pantalla completa ───────────

@Composable
fun BusinessChatScreen(target: BusinessChatTarget, onClose: () -> Unit, vm: BusinessChatViewModel = hiltViewModel()) {
    LaunchedEffect(target) { vm.open(target) }
    BackHandler(onBack = onClose)
    val s by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var reportMenu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.lastIndex) }

    Column(Modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding().navigationBarsPadding().imePadding()) {
        // Barra: volver, nombre del negocio, ⋯ (reportar y bloquear)
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose)
                .semantics { contentDescription = "Volver" }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BuddyColor.Ink)
            }
            Text(target.title, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 1,
                 modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            Box {
                Box(Modifier.size(48.dp).clip(CircleShape)
                    .clickable(enabled = s.conversationId != null) { menu = true }
                    .semantics { contentDescription = "Más opciones" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreVert, null, tint = BuddyColor.Ink)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Reportar") }, onClick = { menu = false; reportMenu = true })
                    if (!s.blocked) DropdownMenuItem(text = { Text("Bloquear") }, onClick = { menu = false; confirmBlock = true })
                }
                DropdownMenu(expanded = reportMenu, onDismissRequest = { reportMenu = false }) {
                    BizReportReasons.forEach { (code, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { reportMenu = false; vm.report(code) })
                    }
                }
            }
        }
        HorizontalDivider(color = BuddyColor.Hairline)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                   contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                   verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.loading) item { Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) } }
            else if (s.messages.isEmpty()) item {
                Text(if (s.side == "traveler") "Escribe tu pregunta. El negocio te responderá aquí." else "Aún no hay mensajes.",
                     style = BuddyType.Footnote, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
                     modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 24.dp, end = 24.dp))
            }
            items(s.messages, key = { it.id }) { m -> Bubble(m, mine = m.senderRole == s.side) }
        }

        // Escribir (o el aviso de bloqueo)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.notice?.let { Text(it, style = BuddyType.Caption1, color = BuddyColor.Brand) }
            if (s.blocked) {
                Text(if (s.blockedByMe) "Bloqueaste esta conversación." else "Esta conversación ya no recibe mensajes.",
                     style = BuddyType.Footnote, color = BuddyColor.InkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else {
                s.pendingCard?.let { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardView(c)
                        Box(Modifier.size(36.dp).clip(CircleShape).background(BuddyColor.SageTint).clickable { vm.removeCard() }
                            .semantics { contentDescription = "Quitar tarjeta" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                s.error?.let { Text(it, style = BuddyType.Caption1, color = BuddyColor.ErrorRed) }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(20.dp)).background(BuddyColor.Surface)
                        .padding(horizontal = 14.dp, vertical = 11.dp)) {
                        if (text.isEmpty()) Text(if (s.side == "traveler") "Escribe tu pregunta…" else "Escribe tu respuesta…",
                                                 style = BuddyType.Callout, color = BuddyColor.InkFaint)
                        BasicTextField(text, { text = it }, textStyle = BuddyType.Callout.copy(color = BuddyColor.Ink),
                                       cursorBrush = SolidColor(BuddyColor.Brand), maxLines = 4, modifier = Modifier.fillMaxWidth())
                    }
                    val canSend = text.isNotBlank() && !s.sending && s.conversationId != null
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .background(if (canSend) BuddyColor.Brand else BuddyColor.InkMuted.copy(alpha = 0.4f))
                        .clickable(enabled = canSend) { vm.send(text) { text = "" } }
                        .semantics { contentDescription = "Enviar" }, contentAlignment = Alignment.Center) {
                        if (s.sending) CircularProgressIndicator(color = BuddyColor.InkInverse, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Icon(Icons.Filled.ArrowUpward, null, tint = BuddyColor.InkInverse)
                    }
                }
            }
        }
    }

    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text(if (s.side == "traveler") "¿Bloquear a este negocio?" else "¿Bloquear a esta persona?") },
        text = { Text("Ya no podrán escribirse en esta conversación y desaparecerá de tu lista.") },
        confirmButton = { TextButton(onClick = { confirmBlock = false; vm.block() }) { Text("Bloquear", color = BuddyColor.ErrorRed) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancelar") } },
    )
}

@Composable
private fun Bubble(m: BizMessage, mine: Boolean) {
    Column(Modifier.fillMaxWidth().padding(start = if (mine) 48.dp else 0.dp, end = if (mine) 0.dp else 48.dp),
           horizontalAlignment = if (mine) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        m.card?.let { CardView(it) }
        Text(m.body, style = BuddyType.Body, color = if (mine) BuddyColor.InkInverse else BuddyColor.Ink,
             modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (mine) BuddyColor.Brand else BuddyColor.Surface)
                 .padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** La tarjeta del lugar o del producto por el que se pregunta. */
@Composable
fun CardView(c: BizCard) {
    Row(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Hairline, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
            if (c.imageUrl != null) AsyncImage(c.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
            else Icon(if (c.kind == "product") Icons.Outlined.ShoppingBag else Icons.Outlined.Storefront, null, tint = BuddyColor.Brand)
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (c.kind == "product") "Pregunta sobre este producto" else "Consulta sobre este lugar",
                 style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            Text(c.title, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 2)
            listOfNotNull(c.subtitle, c.price).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = BuddyType.Caption1, color = BuddyColor.Brand)
            }
        }
    }
}

// ── Fila "Pregúntale al negocio" (pestaña Información) ─────────────────────

@Composable
fun AskBusinessRow(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ChatBubbleOutline, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Pregúntale al negocio", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text("Horario, precios o lo que necesites saber", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand)
    }
}

// ── Conexiones del visitante: sus conversaciones con negocios ─────────────

@HiltViewModel
class VisitorInboxViewModel @Inject constructor(private val api: HomeApi) : ViewModel() {
    data class UiState(val loading: Boolean = true, val items: List<BizConversation> = emptyList(), val error: String? = null)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    fun load() = viewModelScope.launch {
        runCatching { api.bizConversations().conversations }
            .onSuccess { list -> _state.update { UiState(loading = false, items = list) } }
            .onFailure { _state.update { it.copy(loading = false, error = "No pudimos cargar tus conversaciones.") } }
    }
}

/** Lista de conversaciones del visitante; vacía, muestra [empty]. */
@Composable
fun VisitorInbox(onOpen: (BusinessChatTarget) -> Unit, empty: @Composable () -> Unit, reloadKey: Any? = null,
                 vm: VisitorInboxViewModel = hiltViewModel()) {
    LaunchedEffect(reloadKey) { vm.load() }
    val s by vm.state.collectAsStateWithLifecycle()
    when {
        s.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.5.dp) }
        s.items.isEmpty() -> empty()
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp)) {
            items(s.items, key = { it.id }) { c ->
                val name = c.businessName ?: "Negocio"
                Row(Modifier.fillMaxWidth().clickable { onOpen(BusinessChatTarget(title = name, conversationId = c.id)) }
                        .background(BuddyColor.Surface).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                        Text(name.take(1), style = BuddyType.Headline, color = BuddyColor.Brand)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 1)
                        Text((if (c.lastSender == "traveler") "Tú: " else "") + (c.lastMessage ?: ""),
                             style = BuddyType.Footnote, color = BuddyColor.InkMuted, maxLines = 2)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand)
                }
                HorizontalDivider(color = BuddyColor.Hairline)
            }
        }
    }
}
