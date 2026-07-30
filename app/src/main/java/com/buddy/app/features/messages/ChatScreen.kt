package com.buddy.app.features.messages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.TravelerAlias
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.matching.data.ApiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Chat 1:1 — espejo 1:1 de BuddyChatView (iOS, ContactarBuddyView.swift):
 * header con avatar/presencia/menú, banner dedicado, burbujas (texto con hora
 * embebida, imagen, audio con waveform, ubicación, locación, category card),
 * separadores de fecha, paginación, badge "N nuevos", CloseCycleCard,
 * input estilo WhatsApp con grabación de audio (mantener + deslizar para
 * cancelar), sheet de adjuntos, encuesta de cierre y reporte de usuario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    matchId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(matchId, initialCategory) { viewModel.open(matchId, initialCategory) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // ── Roles (espejo de isCurrentUserBuddy / buddyName en iOS) ──────────────
    val match = state.match
    val isCurrentUserBuddy = state.myTravelerId != null && state.myTravelerId == match?.buddyId
    val otherPerson = if (isCurrentUserBuddy) match?.traveler else match?.buddy
    val otherPersonId = otherPerson?.id
        ?: if (isCurrentUserBuddy) match?.travelerId else match?.buddyId
    // El nombre real va en minúscula, como el resto del encabezado. El alias no:
    // "Tortuga Azul" funciona como nombre propio y en minúscula se lee como una
    // cosa, no como alguien.
    val realName = otherPerson?.fullName?.trim().orEmpty()
    val buddyName = if (realName.isNotEmpty()) {
        realName.split(" ").firstOrNull()?.lowercase() ?: realName
    } else {
        TravelerAlias.alias(otherPersonId)
    }
    val buddyAvatarUrl = otherPerson?.avatarUrl
    val buddyInitials = TravelerAlias.initials(otherPerson?.fullName, otherPersonId)
    /** Ciudad desde la que se pidió esta ayuda. */
    val helpPlace = match?.helpRequest?.destination?.name?.takeIf { it.isNotBlank() }

    // ── UI state ──────────────────────────────────────────────────────────────
    var showMenu by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showPlacePicker by remember { mutableStateOf(false) }
    var unseenCount by remember { mutableIntStateOf(0) }
    var initialScrollDone by remember { mutableStateOf(false) }

    // Cierre desde este lado → volver (espejo del dismiss() en closeMatch/closeAsHelper)
    LaunchedEffect(state.closedByMe) { if (state.closedByMe) onBack() }

    // true si el usuario está en (o cerca de) el fondo de la lista
    val isNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 2
        }
    }

    // ── Auto-scroll (espejo de onChange(messages.count) en iOS) ─────────────
    var prevCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.messages.size, state.isLoading) {
        val newCount = state.messages.size
        if (state.isLoading) return@LaunchedEffect
        // Un mensaje propio (incluida la burbuja optimista temp-) siempre baja al
        // fondo, aunque el usuario estuviera leyendo historial: quien envía debe
        // ver su mensaje.
        val lastIsMine = state.messages.lastOrNull()
            ?.let { it.senderId != null && it.senderId == state.myTravelerId } == true
        when {
            !initialScrollDone -> {
                // Primera carga: salto instantáneo al fondo, sin animación
                if (newCount > 0) listState.scrollToItem(newCount)
                initialScrollDone = true
            }
            state.isLoadingMore || newCount <= prevCount -> Unit // prepend: mantener posición
            isNearBottom || lastIsMine -> listState.animateScrollToItem(newCount)
            else -> unseenCount += (newCount - prevCount) // leyendo historial → badge
        }
        prevCount = newCount
    }
    // Teclado abierto → mantener el último mensaje visible mientras el IME anima.
    // Se lee el inset por frame: cada cambio re-ancla el fondo (scroll sin animar).
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && initialScrollDone && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size)
        }
    }
    // Burbuja optimista de audio → mantener el fondo visible
    LaunchedEffect(state.pendingAudioPath) {
        if (state.pendingAudioPath != null && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
    }
    // Paginación: al llegar arriba con el scroll, cargar más
    LaunchedEffect(listState) {
        snapshotFirstVisible(listState) { first ->
            if (first == 0 && initialScrollDone && state.hasMoreMessages && !state.isLoadingMore) {
                viewModel.loadMore()
            }
        }
    }

    // ── Launchers: fotos, cámara, permiso de micrófono ───────────────────────
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes != null) viewModel.sendImage(bytes)
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            viewModel.sendImage(out.toByteArray())
        }
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val isCompleted = state.matchStatus == "completed" || state.matchStatus == "cancelled"

    // ── Card de cierre de ciclo (espejo de shouldShowCloseCard) ─────────────
    val shouldShowCloseCard = remember(state.messages, state.matchStatus, state.closeCardDismissed, state.pendingAudioPath, match) {
        if (isCompleted || state.closeCardDismissed || state.pendingAudioPath != null) false
        else {
            val last = state.messages.lastOrNull()
            val isFromOther = last != null && last.senderId != state.myTravelerId
            val matchStart = parseInstant(match?.matchedAt ?: match?.createdAt)
            val tenMinPassed = matchStart != null &&
                Instant.now().epochSecond - matchStart.epochSecond > 10 * 60
            isFromOther && tenMinPassed
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BuddyColor.Canvas).imePadding()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth()
                    .background(BuddyColor.Surface)
                    .padding(horizontal = Spacing.edge, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = BuddyColor.Ink,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onBack() },
                )

                // Avatar 38dp con inicial de fallback
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(BuddyColor.GroupedBg),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!buddyAvatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = buddyAvatarUrl, contentDescription = buddyName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(buddyInitials, style = BuddyType.Caption1.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = BuddyColor.Brand)
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(buddyName, style = BuddyType.Headline, color = BuddyColor.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.buddyIsOnline) {
                            Box(Modifier.size(7.dp).background(BuddyColor.Accent, CircleShape))
                        }
                        Text(
                            if (state.buddyIsOnline) "en línea" else if (isCurrentUserBuddy) "Tu viajero" else "Tu buddy",
                            style = BuddyType.Caption1,
                            color = if (state.buddyIsOnline) BuddyColor.Accent else BuddyColor.InkMuted,
                        )
                        // Desde dónde piden ayuda. Va aquí arriba porque un buddy
                        // puede tener varias ciudades abiertas a la vez y necesita
                        // saberlo sin salir de la conversación.
                        helpPlace?.let { place ->
                            Text("·", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                            Text(place, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1)
                        }
                    }
                }

                Box {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "Opciones de conversación",
                        tint = BuddyColor.Ink,
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable { showMenu = true }.padding(6.dp),
                    )
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!isCompleted) {
                            DropdownMenuItem(
                                text = { Text("Cerrar apoyo", color = BuddyColor.ErrorRed) },
                                leadingIcon = { Icon(Icons.Filled.CheckCircle, null, tint = BuddyColor.ErrorRed) },
                                onClick = {
                                    showMenu = false
                                    // requestClose(): el buddy confirma; el viajero responde la encuesta
                                    if (isCurrentUserBuddy) showCloseConfirm = true else showFeedbackSheet = true
                                },
                            )
                            HorizontalDivider()
                        }
                        DropdownMenuItem(
                            text = { Text("Reportar usuario", color = BuddyColor.ErrorRed) },
                            leadingIcon = { Icon(Icons.Filled.Error, null, tint = BuddyColor.ErrorRed) },
                            onClick = { showMenu = false; showReportSheet = true },
                        )
                    }
                }
            }
            HorizontalDivider(color = BuddyColor.Border)

            // ── Banner dedicado ───────────────────────────────────────────────
            Text(
                if (isCurrentUserBuddy)
                    "Estás acompañando a $buddyName. Respóndele con calma; cuando todo esté resuelto, cierra el apoyo."
                else
                    "$buddyName está dedicado solo a ti. Cierra el ciclo cuando termines para que pueda ayudar a otro viajero.",
                style = BuddyType.Caption1,
                color = BuddyColor.InkMuted,
                modifier = Modifier.fillMaxWidth()
                    .background(BuddyColor.Canvas)
                    .padding(horizontal = Spacing.edge, vertical = 10.dp),
            )
            HorizontalDivider(color = BuddyColor.Hairline)

            // ── Mensajes ──────────────────────────────────────────────────────
            Box(Modifier.weight(1f)) {
                if (state.isLoading && state.messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.5.dp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.edge, vertical = Spacing.md),
                    ) {
                        // Trigger de paginación arriba
                        if (state.hasMoreMessages && state.messages.isNotEmpty()) {
                            item(key = "load-more") {
                                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(Modifier.size(20.dp), color = BuddyColor.InkMuted, strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }
                        if (state.messages.isEmpty()) {
                            item(key = "welcome") {
                                WelcomeMessage(
                                    buddyName = buddyName,
                                    buddyAvatarUrl = buddyAvatarUrl,
                                    buddyInitials = buddyInitials,
                                    isCurrentUserBuddy = isCurrentUserBuddy,
                                )
                            }
                        }
                        itemsIndexedKeyed(state.messages) { i, msg ->
                            // Separador de fecha al cambiar de día
                            val prev = state.messages.getOrNull(i - 1)
                            if (i == 0 || !sameDay(prev?.createdAt, msg.createdAt)) {
                                DateSeparator(msg.createdAt)
                            }
                            val isMe = msg.senderId != null && msg.senderId == state.myTravelerId
                            val prevSame = i > 0 && prev?.senderId == msg.senderId
                            BuddyMessageBubble(msg = msg, isMe = isMe)
                            Spacer(Modifier.height(if (prevSame) 2.dp else 6.dp))
                        }
                        // Card de cierre de ciclo
                        if (shouldShowCloseCard) {
                            item(key = "close-card") {
                                CloseCycleCard(
                                    buddyName = buddyName,
                                    isHelper = isCurrentUserBuddy,
                                    onClose = {
                                        if (isCurrentUserBuddy) showCloseConfirm = true else showFeedbackSheet = true
                                    },
                                    onKeepOpen = { viewModel.dismissCloseCard() },
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                )
                            }
                        }
                        // Burbuja optimista de audio mientras sube
                        val pendingAudio = state.pendingAudioPath
                        if (pendingAudio != null) {
                            item(key = "pending-audio") {
                                Box {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                        AudioPlayerBubble(audioUrl = pendingAudio, isMe = true, timeStr = null)
                                    }
                                    CircularProgressIndicator(
                                        Modifier.size(12.dp).align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
                                        color = Color.White, strokeWidth = 1.5.dp,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        item(key = "bottom-anchor") { Spacer(Modifier.height(1.dp)) }
                    }
                }

                // Badge "↓ N nuevos" — mensajes que llegaron mientras se leía historial
                androidx.compose.animation.AnimatedVisibility(
                    visible = unseenCount > 0,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BuddyColor.Brand)
                            .clickable {
                                val target = unseenCount.let { state.messages.size }
                                unseenCount = 0
                                scope.launch { listState.animateScrollToItem(target) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            if (unseenCount == 1) "1 nuevo" else "$unseenCount nuevos",
                            style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
            // Reset del badge al volver al fondo manualmente
            LaunchedEffect(isNearBottom) { if (isNearBottom) unseenCount = 0 }

            // ── Input bar / barra de cierre ───────────────────────────────────
            if (isCompleted) {
                ClosedBar()
            } else {
                ChatInputBar(
                    draft = draft,
                    onDraftChange = { draft = it },
                    buddyName = buddyName,
                    isSending = state.isSending,
                    isSendingImage = state.isSendingImage,
                    isRecording = state.isRecording,
                    recordSeconds = state.recordSeconds,
                    onSend = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.send(draft)
                        draft = ""
                    },
                    onAttach = { showAttachSheet = true },
                    onMicPressStart = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val ok = viewModel.startRecording()
                            if (ok) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            ok
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            false
                        }
                    },
                    onMicRelease = { cancelled ->
                        if (cancelled) {
                            viewModel.cancelRecording()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            viewModel.stopAndSendRecording()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                )
            }
        }

        // ── Toast de reporte enviado ─────────────────────────────────────────
        AnimatedVisibility(
            visible = state.reportSent,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Text(
                "Reporte enviado. Lo revisaremos pronto.",
                style = BuddyType.Callout, color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Ink.copy(alpha = 0.9f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        LaunchedEffect(state.reportSent) {
            if (state.reportSent) { delay(3000); viewModel.dismissReportToast() }
        }
    }

    // ── Alerta: no se pudo enviar ────────────────────────────────────────────
    if (state.sendFailed) {
        AlertDialog(
            onDismissRequest = {
                state.failedDraft?.let { draft = it }
                viewModel.dismissSendFailed()
            },
            title = { Text("No se pudo enviar") },
            text = { Text("El mensaje no se envió. Inténtalo de nuevo.") },
            confirmButton = {
                TextButton(onClick = {
                    state.failedDraft?.let { draft = it }
                    viewModel.dismissSendFailed()
                }) { Text("OK") }
            },
        )
    }

    // ── Alerta: sin acceso a ubicación ───────────────────────────────────────
    if (state.locationFailed) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLocationFailed() },
            title = { Text("Sin acceso a tu ubicación") },
            text = { Text("Permite el acceso a tu ubicación en Ajustes para compartirla.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissLocationFailed()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Abrir ajustes") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLocationFailed() }) { Text("Cancelar") }
            },
        )
    }

    // ── Confirmación de cierre (buddy, sin encuesta) ─────────────────────────
    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("¿Cerrar acompañamiento?") },
            text = { Text("$buddyName quedará libre para acompañar a otro viajero.") },
            confirmButton = {
                TextButton(onClick = { showCloseConfirm = false; viewModel.closeAsHelper() }) {
                    Text("Cerrar", color = BuddyColor.ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("Cancelar") }
            },
        )
    }

    // ── Sheet de adjuntos ────────────────────────────────────────────────────
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = BuddyColor.Canvas,
        ) {
            AttachSheetContent(
                onPhotos = {
                    showAttachSheet = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onCamera = {
                    showAttachSheet = false
                    cameraLauncher.launch(null)
                },
                onLocation = {
                    showAttachSheet = false
                    viewModel.sendLocation()
                },
                onPlaces = {
                    showAttachSheet = false
                    viewModel.loadPlaces()
                    showPlacePicker = true
                },
            )
        }
    }

    // ── Place picker (Locaciones del trip) ───────────────────────────────────
    if (showPlacePicker) {
        ModalBottomSheet(
            onDismissRequest = { showPlacePicker = false },
            containerColor = BuddyColor.Canvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            PlacePickerContent(
                places = state.places,
                isLoading = state.isLoadingPlaces,
                onSelect = { name, lat, lng ->
                    showPlacePicker = false
                    viewModel.sendPlace(name, lat, lng)
                },
                onCancel = { showPlacePicker = false },
            )
        }
    }

    // ── Encuesta de cierre (viajero) ─────────────────────────────────────────
    if (showFeedbackSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFeedbackSheet = false },
            containerColor = BuddyColor.Canvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CloseFeedbackContent(
                buddyName = buddyName,
                buddyAvatarUrl = buddyAvatarUrl,
                onClose = { feeling, pressure ->
                    showFeedbackSheet = false
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.closeWithFeedback(feeling, pressure)
                },
                onDismiss = { showFeedbackSheet = false },
            )
        }
    }

    // ── Reporte de usuario ───────────────────────────────────────────────────
    if (showReportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReportSheet = false },
            containerColor = BuddyColor.Canvas,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ReportUserContent(
                buddyName = buddyName,
                onSend = { reason, details ->
                    showReportSheet = false
                    viewModel.reportUser(reason, details)
                },
                onCancel = { showReportSheet = false },
            )
        }
    }
}

// ── Helpers de lista ────────────────────────────────────────────────────────

/** items con key estable + índice — evita recomposición completa al prepender. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedKeyed(
    messages: List<ApiMessage>,
    content: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, ApiMessage) -> Unit,
) {
    for (i in messages.indices) {
        item(key = messages[i].id) { content(i, messages[i]) }
    }
}

/** Observa el primer ítem visible para disparar la paginación. */
private suspend fun snapshotFirstVisible(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onFirst: (Int) -> Unit,
) {
    androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }
        .collect { onFirst(it) }
}

// ── Fechas / horas ──────────────────────────────────────────────────────────

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateSepFmt = DateTimeFormatter.ofPattern("EEEE · H:mm", Locale("es", "PE"))

private fun parseInstant(iso: String?): Instant? =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun zoned(iso: String?): ZonedDateTime? =
    parseInstant(iso)?.atZone(ZoneId.systemDefault())

private fun shortTime(iso: String?): String? = zoned(iso)?.format(timeFmt)

private fun sameDay(a: String?, b: String?): Boolean {
    val za = zoned(a) ?: return false
    val zb = zoned(b) ?: return false
    return za.toLocalDate() == zb.toLocalDate()
}

@Composable
private fun DateSeparator(iso: String?) {
    Text(
        zoned(iso)?.format(dateSepFmt) ?: "",
        style = BuddyType.Caption1,
        color = BuddyColor.InkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
    )
}

// ── Welcome (chat vacío) ────────────────────────────────────────────────────

@Composable
private fun WelcomeMessage(
    buddyName: String,
    buddyAvatarUrl: String?,
    buddyInitials: String,
    isCurrentUserBuddy: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.edge).padding(top = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(BuddyColor.GroupedBg),
            contentAlignment = Alignment.Center,
        ) {
            if (!buddyAvatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = buddyAvatarUrl, contentDescription = buddyName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(buddyInitials, style = BuddyType.Title3.copy(fontSize = 22.sp), color = BuddyColor.Brand)
            }
        }
        Text(
            if (isCurrentUserBuddy) "Acompañando a $buddyName" else "Conectado con $buddyName",
            style = BuddyType.Title3, color = BuddyColor.Ink,
        )
        Text(
            if (isCurrentUserBuddy) "Puedes ayudar a $buddyName con lo que necesite al llegar."
            else "Tu buddy te ayudará con todo en tu destino.",
            style = BuddyType.Callout, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
        )
    }
}

// ── Burbuja de mensaje (espejo de BuddyMessageBubble) ──────────────────────

@Composable
private fun BuddyMessageBubble(msg: ApiMessage, isMe: Boolean) {
    val isAudio = msg.type == "audio" || msg.type == "audio_message"
    val isImage = msg.type == "image" && msg.imageUrl != null
    val isLocation = msg.content?.startsWith("location:") == true
    val isPlace = msg.content?.startsWith("place:") == true
    val isCategory = msg.content?.startsWith("category_card:") == true
    val timeStr = shortTime(msg.createdAt)

    // Espejo del HStack de iOS: Spacer(minLength: 56) al lado contrario acota
    // el ancho máximo de la burbuja; Arrangement.End/Start la ancla a su lado.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        if (isMe) Spacer(Modifier.width(56.dp))
        Column(
            Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
        ) {
            when {
                isAudio && msg.audioUrl != null -> AudioPlayerBubble(msg.audioUrl, isMe, timeStr)
                isImage -> ImageBubble(msg.imageUrl!!, timeStr)
                isCategory -> {
                    CategoryCardBubble(msg.content!!, isMe)
                    CardTime(timeStr)
                }
                isPlace -> {
                    PlaceCardBubble(msg.content!!)
                    CardTime(timeStr)
                }
                isLocation -> {
                    LocationCardBubble(msg.content!!)
                    CardTime(timeStr)
                }
                else -> TextBubble(msg.content ?: "", isMe, timeStr)
            }
        }
        if (!isMe) Spacer(Modifier.width(56.dp))
    }
}

@Composable
private fun CardTime(timeStr: String?) {
    if (timeStr != null) {
        Text(timeStr, fontSize = 10.sp, color = BuddyColor.InkMuted, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

@Composable
private fun TextBubble(content: String, isMe: Boolean, timeStr: String?) {
    // Hora embebida: espaciador invisible al final para que la última línea
    // nunca se solape con la hora (mismo truco que iOS)
    val timeSpacer = timeStr?.let { " ".repeat(it.length + 6) } ?: ""
    val linkColor = if (isMe) Color.White.copy(alpha = 0.85f) else BuddyColor.Brand
    val annotated = buildAnnotatedString {
        var last = 0
        for (m in urlRegex.findAll(content)) {
            append(content.substring(last, m.range.first))
            withLink(
                LinkAnnotation.Url(
                    m.value,
                    TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                )
            ) { append(m.value) }
            last = m.range.last + 1
        }
        append(content.substring(last))
        append(timeSpacer)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isMe) BuddyColor.Brand else BuddyColor.Surface)
            .then(
                if (isMe) Modifier
                else Modifier.border(1.dp, BuddyColor.Border, RoundedCornerShape(18.dp))
            )
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Text(
            annotated,
            style = BuddyType.Body,
            color = if (isMe) Color.White else BuddyColor.Ink,
        )
        if (timeStr != null) {
            Text(
                timeStr,
                fontSize = 10.sp,
                color = if (isMe) Color.White.copy(alpha = 0.65f) else BuddyColor.InkMuted,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun ImageBubble(url: String, timeStr: String?) {
    Box {
        AsyncImage(
            model = url, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(BuddyColor.GroupedBg),
        )
        if (timeStr != null) {
            Text(
                timeStr,
                fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun CategoryCardBubble(content: String, isMe: Boolean) {
    val key = content.removePrefix("category_card:")
    val (icon, label, subtitle) = when (key) {
        "transport" -> Triple(Icons.Filled.Map, "Cómo llegar", "Rutas y transporte")
        "food" -> Triple(Icons.Filled.Place, "Comer", "Comida y restaurantes")
        "translation" -> Triple(Icons.Filled.Person, "Traducir", "Frases, señales y más")
        "activities" -> Triple(Icons.Filled.Search, "Qué hacer", "Tours y actividades")
        "accommodation" -> Triple(Icons.Filled.Place, "Alojamiento", "Hoteles, hostales y más")
        "emergency" -> Triple(Icons.Filled.CheckCircle, "Seguridad", "Emergencias y consejos")
        else -> Triple(Icons.Filled.Search, key, "Solicitud de ayuda")
    }
    Row(
        Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.Brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = BuddyColor.Brand, modifier = Modifier.size(17.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (isMe) "Necesito ayuda con" else "Necesita ayuda con", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            Text(label, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text(subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
    }
}

@Composable
private fun PlaceCardBubble(content: String) {
    val context = LocalContext.current
    val raw = content.removePrefix("place:")
    val parts = raw.split("|", limit = 4)
    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
    val lng = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    val name = parts.getOrNull(2) ?: "Lugar"

    Row(
        Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp))
            .clickable { openMap(context, lat, lng, name) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.Brand.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Place, null, tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Locación sugerida", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            Text(name, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 2)
            Text("Ver en el mapa", style = BuddyType.Caption1, color = BuddyColor.Brand)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LocationCardBubble(content: String) {
    val context = LocalContext.current
    val coords = content.removePrefix("location:").split(",")
    val lat = coords.firstOrNull()?.toDoubleOrNull() ?: 0.0
    val lng = coords.lastOrNull()?.toDoubleOrNull() ?: 0.0

    Column(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp))
            .clickable { openMap(context, lat, lng, "Mi ubicación") },
    ) {
        // Vista previa del mapa — sin Maps SDK usamos un lienzo con pin centrado
        Box(
            Modifier.fillMaxWidth().height(130.dp).background(BuddyColor.GroupedBg),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(16.dp).background(BuddyColor.Brand, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(7.dp).background(Color.White, CircleShape))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(BuddyColor.Surface).padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.LocationOn, null, tint = BuddyColor.Brand, modifier = Modifier.size(11.dp))
            Text("Mi ubicación actual", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.NorthEast, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(10.dp))
        }
    }
}

private fun openMap(context: android.content.Context, lat: Double, lng: Double, name: String) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

// ── Audio bubble (espejo de AudioPlayerBubble + AudioPlayerVM) ─────────────

// Waveform — mismas 26 barras que iOS
private val waveHeights = listOf(4, 7, 12, 6, 14, 9, 17, 11, 6, 13, 9, 16, 5, 11, 8, 13, 6, 14, 8, 11, 5, 9, 15, 7, 11, 6)

@Composable
private fun AudioPlayerBubble(audioUrl: String, isMe: Boolean, timeStr: String?) {
    val bubbleBg = if (isMe) BuddyColor.Brand else BuddyColor.Surface
    val playFg = if (isMe) BuddyColor.Brand else Color.White
    val playBg = if (isMe) Color.White else BuddyColor.Ink
    val waveActive = if (isMe) Color.White else BuddyColor.Brand
    val waveIdle = if (isMe) Color.White.copy(alpha = 0.3f) else BuddyColor.InkMuted.copy(alpha = 0.25f)
    val metaFg = if (isMe) Color.White.copy(alpha = 0.6f) else BuddyColor.InkMuted

    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableIntStateOf(0) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { player.value?.release(); player.value = null }
    }

    // Polling de progreso mientras reproduce (50ms como iOS)
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            player.value?.let { p ->
                runCatching {
                    if (durationMs > 0) progress = p.currentPosition.toFloat() / durationMs
                }
            }
            delay(50)
        }
    }

    fun togglePlay() {
        val existing = player.value
        if (existing != null) {
            if (isPlaying) { existing.pause(); isPlaying = false }
            else { existing.start(); isPlaying = true }
            return
        }
        // Carga perezosa en el primer tap (como AVPlayer en iOS)
        if (isLoading) return
        isLoading = true
        val p = MediaPlayer()
        runCatching {
            p.setDataSource(audioUrl)
            p.setOnPreparedListener {
                durationMs = p.duration
                isLoading = false
                p.start()
                isPlaying = true
            }
            p.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                p.seekTo(0)
            }
            p.setOnErrorListener { _, _, _ ->
                isLoading = false; hasError = true; true
            }
            p.prepareAsync()
            player.value = p
        }.onFailure {
            p.release(); isLoading = false; hasError = true
        }
    }

    Row(
        Modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bubbleBg)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Play / Pause / Loading / Error
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(playBg)
                .clickable(enabled = !hasError) { togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.size(16.dp), color = playFg, strokeWidth = 2.dp)
                hasError -> Icon(Icons.Filled.Error, null, tint = playFg, modifier = Modifier.size(14.dp))
                isPlaying -> Icon(Icons.Filled.Pause, null, tint = playFg, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Filled.PlayArrow, null, tint = playFg, modifier = Modifier.size(18.dp))
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            // Waveform con scrubbing por arrastre
            var waveWidthPx by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(durationMs) {
                        waveWidthPx = size.width.toFloat()
                        detectDragGestures { change, _ ->
                            if (durationMs > 0 && waveWidthPx > 0) {
                                val ratio = (change.position.x / waveWidthPx).coerceIn(0f, 1f)
                                progress = ratio
                                player.value?.seekTo((ratio * durationMs).toInt())
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    waveHeights.forEachIndexed { i, h ->
                        val passed = i.toFloat() / waveHeights.size < progress
                        Box(
                            Modifier
                                .weight(1f)
                                .height(h.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (passed) waveActive else waveIdle),
                        )
                    }
                }
                // Punto de scrubbing
                val density = LocalDensity.current
                Box(
                    Modifier
                        .offset(x = with(density) {
                            val w = waveWidthPx.takeIf { it > 0f } ?: 0f
                            max(0f, min(w * progress - 5.dp.toPx(), w - 10.dp.toPx())).toDp()
                        })
                        .size(10.dp)
                        .background(waveActive, CircleShape),
                )
            }

            // Duración (izq) · hora del mensaje (der)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val secs = if (isPlaying) (progress * durationMs / 1000).toInt() else durationMs / 1000
                Text(
                    if (isLoading) "—:——" else fmtSecs(secs),
                    fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                    color = metaFg,
                )
                if (timeStr != null) {
                    Spacer(Modifier.weight(1f))
                    Text(timeStr, fontSize = 10.sp, color = metaFg)
                }
            }
        }
    }
}

private fun fmtSecs(s: Int): String = "${s / 60}:${(s % 60).toString().padStart(2, '0')}"

// ── Close Cycle Card ────────────────────────────────────────────────────────

@Composable
private fun CloseCycleCard(
    buddyName: String,
    isHelper: Boolean,
    onClose: () -> Unit,
    onKeepOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (isHelper) "¿Pudiste ayudar a $buddyName?" else "¿pudimos cerrar tu duda?"
    val subtitle = if (isHelper)
        "Si ya resolviste su duda, cierra el apoyo para quedar libre y acompañar a otro viajero."
    else
        "Si todo está resuelto, cierra la ayuda para que $buddyName pueda apoyar a otro viajero."
    val closeLabel = if (isHelper) "Sí, resuelto" else "Sí, gracias"
    val keepLabel = if (isHelper) "Seguimos en eso" else "Tengo otra pregunta"

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.GroupedBg.copy(alpha = 0.6f))
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink)
            Text(subtitle, fontSize = 14.sp, color = BuddyColor.InkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Ink)
                    .clickable(onClick = onClose)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(closeLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Canvas)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                    .clickable(onClick = onKeepOpen)
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(keepLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BuddyColor.Ink, textAlign = TextAlign.Center)
            }
        }
    }
}

// ── Barra de conexión cerrada ───────────────────────────────────────────────

@Composable
private fun ClosedBar() {
    Column {
        HorizontalDivider(color = BuddyColor.Border)
        Row(
            Modifier.fillMaxWidth().background(BuddyColor.Surface).padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Conexión cerrada · gracias por usar buddy", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        }
    }
}

// ── Input bar estilo WhatsApp ───────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    buddyName: String,
    isSending: Boolean,
    isSendingImage: Boolean,
    isRecording: Boolean,
    recordSeconds: Int,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onMicPressStart: () -> Boolean,
    onMicRelease: (cancelled: Boolean) -> Unit,
) {
    val hasText = draft.trim().isNotEmpty()
    var micDragX by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { 80.dp.toPx() }
    val cancelled = micDragX < -cancelThresholdPx
    val scope = rememberCoroutineScope()

    Column {
        if (isSendingImage) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), color = BuddyColor.InkMuted, strokeWidth = 1.5.dp)
                Text("Enviando imagen…", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }
        HorizontalDivider(color = BuddyColor.Border)
        Row(
            Modifier.fillMaxWidth().background(BuddyColor.Surface)
                .padding(horizontal = Spacing.edge, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // + — siempre presente en el layout, invisible durante grabación
            Box(
                Modifier
                    .size(38.dp)
                    .alpha(if (isRecording) 0f else 1f)
                    .clip(CircleShape)
                    .background(BuddyColor.Canvas)
                    .border(1.dp, BuddyColor.Border, CircleShape)
                    .clickable(enabled = !isRecording, onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, "Adjuntar archivo", tint = BuddyColor.InkMuted, modifier = Modifier.size(17.dp))
            }

            // Centro: TextField (o UI de grabación superpuesta en el mismo frame)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (isRecording) 0f else 1f)
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Canvas)
                        .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        enabled = !isRecording,
                        textStyle = BuddyType.Body.copy(color = BuddyColor.Ink),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text("escríbele a $buddyName…", style = BuddyType.Body, color = BuddyColor.InkFaint)
                            }
                            inner()
                        },
                    )
                }
                if (isRecording) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Punto rojo parpadeante + contador
                        val blink = rememberInfiniteTransition(label = "recBlink")
                        val alpha by blink.animateFloat(
                            initialValue = 1f, targetValue = 0.3f,
                            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
                            label = "recAlpha",
                        )
                        Box(Modifier.size(8.dp).alpha(alpha).background(BuddyColor.ErrorRed, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            fmtSecs(recordSeconds),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                            color = BuddyColor.Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.offset(x = with(density) { max(micDragX * 0.6f, -110 * density.density).toDp() }),
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowLeft, null,
                                tint = if (cancelled) BuddyColor.ErrorRed else BuddyColor.InkMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Desliza para cancelar",
                                style = BuddyType.Callout,
                                color = if (cancelled) BuddyColor.ErrorRed else BuddyColor.InkMuted,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Derecha: enviar (con texto) o micrófono (sin texto / grabando)
            if (hasText && !isRecording) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (isSending) BuddyColor.Brand.copy(alpha = 0.4f) else BuddyColor.Brand)
                        .clickable(enabled = !isSending, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowUpward, "Enviar mensaje", tint = Color.White, modifier = Modifier.size(17.dp))
                }
            } else if (!hasText) {
                // Mic — mantener presionado ≥150ms para grabar, deslizar izq. para cancelar
                Box(contentAlignment = Alignment.Center) {
                    // Halo pulsante durante la grabación
                    if (isRecording) {
                        val pulse = rememberInfiniteTransition(label = "micPulse")
                        val haloScale by pulse.animateFloat(
                            initialValue = 1f, targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                            label = "halo",
                        )
                        Box(
                            Modifier
                                .size(54.dp)
                                .scale(haloScale)
                                .background(
                                    (if (cancelled) BuddyColor.ErrorRed else BuddyColor.Brand).copy(alpha = 0.15f),
                                    CircleShape,
                                ),
                        )
                    }
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording)
                                    (if (cancelled) BuddyColor.ErrorRed else BuddyColor.Brand).copy(alpha = 0.1f)
                                else BuddyColor.Canvas
                            )
                            .then(if (!isRecording) Modifier.border(1.dp, BuddyColor.Border, CircleShape) else Modifier)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    micDragX = 0f
                                    var started = false
                                    // 150ms: un tap corto no inicia la grabación
                                    val startJob = scope.launch {
                                        delay(150)
                                        started = onMicPressStart()
                                    }
                                    var wasCancelledBySlide = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        micDragX = min(0f, change.position.x - down.position.x)
                                        wasCancelledBySlide = micDragX < -cancelThresholdPx
                                    }
                                    startJob.cancel()
                                    if (started) onMicRelease(wasCancelledBySlide)
                                    micDragX = 0f
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Mantén presionado para grabar",
                            tint = if (isRecording) (if (cancelled) BuddyColor.ErrorRed else BuddyColor.Brand) else BuddyColor.InkMuted,
                            modifier = Modifier.size(if (isRecording) 22.dp else 18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Attach sheet ────────────────────────────────────────────────────────────

@Composable
private fun AttachSheetContent(
    onPhotos: () -> Unit,
    onCamera: () -> Unit,
    onLocation: () -> Unit,
    onPlaces: () -> Unit,
) {
    Column(Modifier.padding(bottom = Spacing.xl)) {
        Text(
            "Compartir",
            style = BuddyType.FootnoteBold, color = BuddyColor.InkMuted,
            modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.edge)) {
            AttachOption(Icons.Filled.Photo, "Fotos", Color(0xFF8E5BB5), onPhotos, Modifier.weight(1f))
            AttachOption(Icons.Filled.CameraAlt, "Cámara", BuddyColor.Brand, onCamera, Modifier.weight(1f))
            AttachOption(Icons.Filled.LocationOn, "Ubicación", BuddyColor.Accent, onLocation, Modifier.weight(1f))
            AttachOption(Icons.Filled.Map, "Locaciones", Color(0xFFCB8038), onPlaces, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AttachOption(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(label, style = BuddyType.Caption1, color = BuddyColor.Ink)
    }
}

// ── Place picker (Locaciones del trip) ──────────────────────────────────────

@Composable
private fun PlacePickerContent(
    places: List<com.buddy.app.features.matching.data.ApiPlace>,
    isLoading: Boolean,
    onSelect: (String, Double, Double) -> Unit,
    onCancel: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, places) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) places else places.filter { it.name.lowercase().contains(q) }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.xl)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Locaciones del trip", style = BuddyType.Headline, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
            Text(
                "Cancelar", style = BuddyType.Callout, color = BuddyColor.InkMuted,
                modifier = Modifier.clickable(onClick = onCancel),
            )
        }

        // Buscador
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.edge, vertical = Spacing.md)
                .clip(RoundedCornerShape(12.dp))
                .background(BuddyColor.GroupedBg)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query, onValueChange = { query = it },
                textStyle = BuddyType.Body.copy(color = BuddyColor.Ink),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Filtrar lugares...", style = BuddyType.Body, color = BuddyColor.InkFaint)
                    inner()
                },
            )
        }

        when {
            isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.5.dp)
            }
            filtered.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isEmpty()) "Sin lugares para este destino" else "Sin resultados",
                    style = BuddyType.Callout, color = BuddyColor.InkMuted,
                )
            }
            else -> Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                filtered.forEach { place ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(place.name, place.lat, place.lng) }
                            .padding(horizontal = Spacing.edge, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(BuddyColor.Brand.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Place, null, tint = BuddyColor.Brand, modifier = Modifier.size(14.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(place.name, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                            place.placeType?.let {
                                Text(it, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Border, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

// ── Encuesta de cierre (viajero) ────────────────────────────────────────────

private val FEELINGS = listOf("cómoda", "bienvenida", "inspirada", "segura", "neutral", "incómoda")
private val PRESSURES = listOf("nunca", "un poco", "mucha")

@Composable
private fun CloseFeedbackContent(
    buddyName: String,
    buddyAvatarUrl: String?,
    onClose: (feeling: String, pressure: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Preseleccionados para facilitar (como iOS): la mayoría cierra sin fricción
    var selectedFeeling by remember { mutableStateOf("cómoda") }
    var selectedPressure by remember { mutableStateOf("nunca") }

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(BuddyColor.GroupedBg),
                contentAlignment = Alignment.Center,
            ) {
                if (!buddyAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = buddyAvatarUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(Icons.Filled.Person, null, tint = BuddyColor.Brand, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(20.dp))

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Tu momento con ") }
                    withStyle(SpanStyle(color = BuddyColor.Brand)) {
                        append(buddyName.replaceFirstChar { it.uppercase() })
                    }
                },
                style = BuddyType.Title2, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text("Dos cosas rápidas antes de cerrar.", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            Spacer(Modifier.height(24.dp))

            // ¿Cómo se sintió?
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append("¿cómo te ") }
                    withStyle(SpanStyle(color = BuddyColor.Brand)) { append("sintió") }
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append(" este momento?") }
                },
                fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            // Grid 3 columnas
            for (row in FEELINGS.chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { f ->
                        val selected = selectedFeeling == f
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) BuddyColor.Brand else BuddyColor.Surface)
                                .then(if (!selected) Modifier.border(1.dp, BuddyColor.Border, RoundedCornerShape(50)) else Modifier)
                                .clickable { selectedFeeling = f }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                f, fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color.White else BuddyColor.Ink,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(14.dp))

            // ¿Presión comercial?
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append("¿sentiste ") }
                    withStyle(SpanStyle(color = BuddyColor.Brand)) { append("presión") }
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append(" comercial?") }
                },
                fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESSURES.forEach { p ->
                    val selected = selectedPressure == p
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) BuddyColor.Brand else BuddyColor.Surface)
                            .then(if (!selected) Modifier.border(1.dp, BuddyColor.Border, RoundedCornerShape(50)) else Modifier)
                            .clickable { selectedPressure = p }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            p, fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) Color.White else BuddyColor.Ink,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "$buddyName nunca verá una calificación — solo cómo te sentiste.",
                style = BuddyType.Caption1, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))

            // CTA
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Ink)
                    .clickable { onClose(selectedFeeling, selectedPressure) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        // X para descartar (el viajero inició el cierre)
        Icon(
            Icons.Filled.Close, "Cerrar",
            tint = BuddyColor.Ink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 20.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(BuddyColor.Canvas)
                .clickable(onClick = onDismiss)
                .padding(9.dp),
        )
    }
}

// ── Reporte de usuario ──────────────────────────────────────────────────────

private val REPORT_REASONS = listOf(
    Triple("harassment", "Acoso o amenazas", Icons.Filled.Error),
    Triple("commercial_pressure", "Presión comercial", Icons.Filled.Error),
    Triple("fake_profile", "Perfil falso o suplantación", Icons.Filled.Person),
    Triple("inappropriate_content", "Contenido inapropiado", Icons.Filled.Close),
    Triple("safety_concern", "Preocupación de seguridad", Icons.Filled.CheckCircle),
    Triple("spam", "Spam o publicidad", Icons.Filled.Error),
    Triple("other", "Otro motivo", Icons.Filled.MoreHoriz),
)

@Composable
private fun ReportUserContent(
    buddyName: String,
    onSend: (reason: String, details: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.edge)
            .padding(bottom = Spacing.xl),
    ) {
        // Toolbar: Cancelar · título · Enviar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cancelar", style = BuddyType.Callout, color = BuddyColor.InkMuted,
                modifier = Modifier.clickable(onClick = onCancel),
            )
            Text(
                "Reportar usuario",
                style = BuddyType.Headline, color = BuddyColor.Ink, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Enviar",
                style = BuddyType.Callout.copy(fontWeight = FontWeight.SemiBold),
                color = if (selectedReason != null) BuddyColor.Brand else BuddyColor.InkFaint,
                modifier = Modifier.clickable(enabled = selectedReason != null) {
                    onSend(selectedReason!!, details.ifBlank { null })
                },
            )
        }
        Spacer(Modifier.height(Spacing.md))

        Text("¿Por qué reportas a $buddyName?", style = BuddyType.Title2, color = BuddyColor.Ink)
        Spacer(Modifier.height(Spacing.lg))

        REPORT_REASONS.forEach { (key, label, icon) ->
            val selected = selectedReason == key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(if (selected) BuddyColor.Brand.copy(alpha = 0.08f) else BuddyColor.Surface)
                    .border(1.dp, if (selected) BuddyColor.Brand else BuddyColor.Border, RoundedCornerShape(Radius.md))
                    .clickable { selectedReason = key }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon, null,
                    tint = if (selected) BuddyColor.Brand else BuddyColor.InkMuted,
                    modifier = Modifier.size(20.dp),
                )
                Text(label, style = BuddyType.Callout, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Filled.Check, null, tint = BuddyColor.Brand, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(Spacing.md))
        Text("Detalles adicionales (opcional)", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(BuddyColor.Surface)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
                .padding(12.dp),
        ) {
            BasicTextField(
                value = details, onValueChange = { details = it },
                textStyle = BuddyType.Callout.copy(color = BuddyColor.Ink),
                minLines = 4, maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (details.isEmpty()) Text("Cuéntanos qué ocurrió…", style = BuddyType.Callout, color = BuddyColor.InkFaint)
                    inner()
                },
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Tu reporte es confidencial. El equipo de Buddy lo revisará en menos de 24 horas.",
            style = BuddyType.Caption1, color = BuddyColor.InkMuted,
        )
    }
}
