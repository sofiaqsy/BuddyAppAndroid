package com.buddy.app.features.conexiones

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.TravelerAlias
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyAvatar
import com.buddy.app.features.matching.data.ApiBuddyOffer
import com.buddy.app.features.matching.data.ApiHelpRequest
import com.buddy.app.features.messages.ChatScreen
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Espejo 1:1 de ConexionesView (iOS): header "TU GENTE / Tus conexiones.",
 * secciones ASIGNADAS PARA TI (OfferCards con Acompañar/Ahora no), ACOMPAÑAMIENTO
 * ABIERTO (yo ayudo), VÍNCULO ABIERTO (me ayudan), ENCUENTROS ANTERIORES
 * (filas planas) y empty state "Las conexiones nacen de un trip".
 */
@Composable
fun ConexionesScreen(
    modifier: Modifier = Modifier,
    onOpenTrips: () -> Unit = {},
    viewModel: ConexionesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var openMatchId by rememberSaveable { mutableStateOf<String?>(null) }

    // Aceptar una solicitud abre el chat con el viajero de inmediato (iOS)
    state.openMatch?.let { m ->
        openMatchId = m.id
        viewModel.clearOpenMatch()
    }

    val openItem = state.connections.firstOrNull { it.id == openMatchId }
    if (openMatchId != null) {
        BackHandler { openMatchId = null; viewModel.load() }
        ChatScreen(
            matchId = openMatchId!!,
            title = openItem?.displayName ?: "Chat",
            onBack = { openMatchId = null; viewModel.load() },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        // ── Header — misma voz que el tab Trips ───────────────────────────
        Column(Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md)) {
            Text(
                "TU GENTE",
                style = BuddyType.Eyebrow.copy(letterSpacing = 2.sp),
                color = BuddyColor.InkMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Tus ") }
                    withStyle(SpanStyle(color = BuddyColor.Brand)) { append("conexiones.") }
                },
                style = BuddyType.Title1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Las personas que estuvieron contigo cuando llegaste.",
                style = BuddyType.Subhead,
                color = BuddyColor.InkMuted,
            )
        }

        // ── Estado anónimo: invitación a crear perfil, no un tab vacío ─────
        val sessionVm: com.buddy.app.features.authentication.SessionViewModel = hiltViewModel()
        val session by sessionVm.session.collectAsState()
        val isSigningIn by sessionVm.isSigningIn.collectAsState()
        val signInError by sessionVm.error.collectAsState()

        when {
            session?.isVerified != true -> AnonymousConnectionState(
                isLoading = isSigningIn,
                error = signInError,
                onGoogle = { ctx -> sessionVm.signInWithGoogle(ctx) },
            )
            // Spinner SOLO antes de la primera carga (después es silencioso)
            !state.hasLoadedOnce && state.connections.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BuddyColor.Brand, strokeWidth = 2.5.dp)
                }
            state.isEmpty -> EmptyConnectionsState(onCreateTrip = onOpenTrips)
            else -> ConnectionList(state, viewModel, onOpen = { openMatchId = it })
        }
    }
}

// ── Estado anónimo — espejo de anonymousConnectionState (iOS) ──────────────
@Composable
private fun AnonymousConnectionState(
    isLoading: Boolean,
    error: String?,
    onGoogle: (android.content.Context) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = Spacing.lg)) {
        Column(
            Modifier
                .padding(horizontal = Spacing.edge)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(BuddyColor.Surface)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.lg))
                .padding(Spacing.lg),
        ) {
            // Header — icono de camino + mensaje
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(44.dp).background(BuddyColor.GroupedBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Route, contentDescription = null,
                        Modifier.size(18.dp), tint = BuddyColor.Brand,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Las personas son parte del viaje", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                    Text(
                        "Crea tu perfil para que Buddy recuerde a quienes estuvieron contigo en el camino.",
                        style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = BuddyColor.Border)
            Spacer(Modifier.height(Spacing.lg))

            // Beneficios — misma copy que iOS
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                AnonymousBenefit(Icons.AutoMirrored.Filled.Chat, "Tus conversaciones",
                    "Consulta el historial de lo que hablaste con tus buddies.")
                AnonymousBenefit(Icons.Filled.HowToReg, "Tus buddies",
                    "Las personas que te ayudaron, siempre a un mensaje de distancia.")
                AnonymousBenefit(Icons.Filled.FavoriteBorder, "Tu impacto",
                    "Recuerda también a quienes ayudaste tú.")
            }
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = BuddyColor.Border)
            Spacer(Modifier.height(Spacing.md))

            Text(
                "Continúa con",
                style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))

            // Google — cápsula canvas con borde (Credential Manager).
            // Apple llega con la continuación de cuenta vía web OAuth.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Canvas)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                    .clickable(enabled = !isLoading) { onGoogle(context) },
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = BuddyColor.Ink, strokeWidth = 2.dp)
                } else {
                    Text("Continuar con Google", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                }
            }

            if (error != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    error, style = BuddyType.Caption1, color = BuddyColor.ErrorRed,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append("Al continuar confirmas que tienes ") }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted, fontWeight = FontWeight.Bold)) { append("18+ años") }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" y aceptas nuestros ") }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted, fontWeight = FontWeight.Bold)) { append("términos, privacidad") }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" y ") }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted, fontWeight = FontWeight.Bold)) { append("código de conducta") }
                },
                style = BuddyType.Caption1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun AnonymousBenefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = BuddyColor.Brand)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text(subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
    }
}

@Composable
private fun ConnectionList(
    state: ConexionesViewModel.State,
    viewModel: ConexionesViewModel,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        // ── ASIGNADAS PARA TI — solicitudes pendientes para este buddy ─────
        if (state.offers.isNotEmpty()) {
            ListHeader("ASIGNADAS PARA TI", state.offers.size, BuddyColor.Brand)
            Column(
                Modifier.padding(horizontal = Spacing.edge),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.offers.forEach { offer ->
                    OfferCard(
                        offer = offer,
                        isAccepting = state.acceptingOfferId == offer.id,
                        isDeclining = state.decliningOfferId == offer.id,
                        onAccept = { viewModel.acceptOffer(offer) },
                        onDecline = { viewModel.declineOffer(offer) },
                    )
                }
            }
        }

        // ── OPORTUNIDADES PARA AYUDAR — respaldo comunitario: solicitudes
        // de otros buddies que, si no responden a tiempo, cualquiera puede
        // tomar. Nunca incluye la oferta oficial propia: esa ya está arriba.
        if (state.availableHelp.isNotEmpty()) {
            ListHeader("OPORTUNIDADES PARA AYUDAR", state.availableHelp.size, BuddyColor.Accent)
            Column(
                Modifier.padding(horizontal = Spacing.edge),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.availableHelp.forEach { item ->
                    AvailableHelpCard(
                        item = item,
                        fetchedAtMs = state.availableHelpFetchedAtMs,
                        isAccepting = state.acceptingHelpId == item.id,
                        error = state.helpError?.takeIf { it.first == item.id }?.second,
                        onAccept = { viewModel.acceptAvailableHelp(item) },
                    )
                }
            }
        }

        // ── ACOMPAÑAMIENTO ABIERTO — viajeros a los que YO ayudo ───────────
        if (state.activeAsBuddy.isNotEmpty()) {
            ActiveSection("ACOMPAÑAMIENTO ABIERTO", state.activeAsBuddy, BuddyColor.Accent, onOpen)
        }

        // ── VÍNCULO ABIERTO — la persona que ME ayuda ──────────────────────
        if (state.activeAsTraveler.isNotEmpty()) {
            ActiveSection("VÍNCULO ABIERTO", state.activeAsTraveler, BuddyColor.Brand, onOpen)
        }

        // ── ENCUENTROS ANTERIORES — filas planas, sin cajas ───────────────
        if (state.past.isNotEmpty()) {
            ListHeader("ENCUENTROS ANTERIORES", state.past.size, BuddyColor.InkMuted)
            Column {
                state.past.forEachIndexed { i, item ->
                    if (i > 0) {
                        HorizontalDivider(
                            Modifier.padding(start = 76.dp, end = Spacing.edge),
                            color = BuddyColor.Hairline,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.id) }
                            .padding(horizontal = Spacing.edge, vertical = 12.dp),
                    ) {
                        ConnectionRow(item, isActive = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ActiveSection(
    title: String,
    items: List<ConnectionItem>,
    color: Color,
    onOpen: (String) -> Unit,
) {
    // El conteo solo se muestra con más de un item (como iOS)
    ListHeader(title, if (items.size > 1) items.size else 0, color)
    Column(
        Modifier.padding(horizontal = Spacing.edge),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(BuddyColor.Surface)
                    .clickable { onOpen(item.id) }
                    .padding(Spacing.md),
            ) {
                ConnectionRow(item, isActive = true)
            }
        }
    }
}

@Composable
private fun ListHeader(title: String, count: Int, color: Color) {
    Row(
        Modifier.padding(start = Spacing.edge, top = Spacing.lg, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = BuddyType.Eyebrow.copy(letterSpacing = 1.5.sp), color = color)
        if (count > 0) {
            Text("· $count", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted.copy(alpha = 0.7f))
        }
    }
}

// ── Available Help Card — "OPORTUNIDADES PARA AYUDAR" ─────────────────────
//
// Muestra una solicitud que NO es la oferta oficial de este buddy. Durante los
// primeros 30s desde que se creó la cola, el candidato oficial tiene prioridad
// exclusiva: la tarjeta se ve pero el botón está deshabilitado con cuenta
// regresiva. Pasada la ventana, cualquier buddy elegible puede tomarla — el
// servidor vuelve a validarlo en POST /matching/match, así que esto es solo UX.
@Composable
private fun AvailableHelpCard(
    item: ApiHelpRequest,
    fetchedAtMs: Long,
    isAccepting: Boolean,
    error: String?,
    onAccept: () -> Unit,
) {
    val travelerName = TravelerAlias.shortDisplayName(item.users?.fullName, item.users?.id ?: item.travelerId)
    val travelerInitials = TravelerAlias.initials(item.users?.fullName, item.users?.id ?: item.travelerId)
    val categoryLabel = CATEGORY_LABELS[item.category] ?: item.category.replaceFirstChar { it.uppercase() }
    val destinationName = item.destination?.name.orEmpty()

    // Reloj local de 1s: la cuenta regresiva avanza sin repreguntar al servidor.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(item.id) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val elapsedSec = if (fetchedAtMs == 0L) 0 else ((nowMs - fetchedAtMs) / 1000).toInt()
    val unlocksIn = ((item.communityUnlocksIn ?: 0) - elapsedSec).coerceAtLeast(0)
    val isUnlocked = (item.isCommunityUnlocked ?: true) && unlocksIn <= 0

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Accent.copy(alpha = 0.2f), RoundedCornerShape(Radius.lg)),
    ) {
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(BuddyColor.GroupedBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    travelerInitials,
                    style = BuddyType.Subhead.copy(fontWeight = FontWeight.Bold),
                    color = BuddyColor.Accent,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(travelerName, style = BuddyType.Headline, color = BuddyColor.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (destinationName.isNotEmpty()) {
                        Text(destinationName, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                        Text("·", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                    Text(categoryLabel, style = BuddyType.Caption1, color = BuddyColor.Accent)
                }
            }
            item.candidateCount?.takeIf { it > 1 }?.let { count ->
                Text(
                    "$count buddies pueden atender",
                    style = BuddyType.Caption1,
                    color = BuddyColor.InkMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(90.dp),
                )
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = BuddyColor.Hairline)

        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when {
                    error != null -> error
                    isUnlocked -> "Disponible ahora"
                    else -> "Otro buddy tiene prioridad · ${unlocksIn}s"
                },
                style = BuddyType.Caption1,
                color = when {
                    error != null -> BuddyColor.ErrorRed
                    isUnlocked -> BuddyColor.Accent
                    else -> BuddyColor.InkMuted
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(88.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isUnlocked) BuddyColor.Accent else BuddyColor.InkMuted.copy(alpha = 0.3f))
                    .clickable(enabled = isUnlocked && !isAccepting, onClick = onAccept),
                contentAlignment = Alignment.Center,
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("Ayudar", style = BuddyType.FootnoteBold, color = Color.White)
                }
            }
        }
    }
}

// ── Offer Card — "ASIGNADAS PARA TI" (Acompañar / Ahora no) ───────────────
private val CATEGORY_LABELS = mapOf(
    "transport" to "Cómo llegar", "food" to "Comer", "translation" to "Traducir",
    "activities" to "Qué hacer", "accommodation" to "Alojamiento",
    "emergency" to "Seguridad", "general" to "Ayuda",
)

@Composable
private fun OfferCard(
    offer: ApiBuddyOffer,
    isAccepting: Boolean,
    isDeclining: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val travelerName = TravelerAlias.shortDisplayName(
        offer.helpRequest?.users?.fullName, offer.helpRequest?.users?.id
    )
    val travelerInitials = TravelerAlias.initials(
        offer.helpRequest?.users?.fullName, offer.helpRequest?.users?.id
    )
    val category = offer.helpRequest?.category.orEmpty()
    val categoryLabel = CATEGORY_LABELS[category] ?: category.replaceFirstChar { it.uppercase() }
    val destinationName = offer.helpRequest?.destination?.name.orEmpty()
    val busy = isAccepting || isDeclining

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Brand.copy(alpha = 0.25f), RoundedCornerShape(Radius.lg)),
    ) {
        // Header — viajero + contexto + llegada
        Row(
            Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(BuddyColor.GroupedBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    travelerInitials,
                    style = BuddyType.Subhead.copy(fontWeight = FontWeight.Bold),
                    color = BuddyColor.Brand,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(travelerName, style = BuddyType.Headline, color = BuddyColor.Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (destinationName.isNotEmpty()) {
                        Text(destinationName, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                        Text("·", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                    Text(categoryLabel, style = BuddyType.Caption1, color = BuddyColor.Brand)
                }
            }
            relativeArrival(offer.helpRequest?.arrivalAt)?.let {
                Text(it, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }

        // Mensaje del viajero, si existe
        val desc = offer.helpRequest?.description
        if (!desc.isNullOrEmpty()) {
            Text(
                "\"$desc\"",
                style = BuddyType.Callout, color = BuddyColor.Ink, maxLines = 2,
                modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md),
            )
        }

        HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = BuddyColor.Hairline)

        // CTAs — Acompañar (brand) / Ahora no (canvas + borde)
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BuddyColor.Brand)
                    .clickable(enabled = !busy, onClick = onAccept),
                contentAlignment = Alignment.Center,
            ) {
                if (isAccepting) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Acompañar", style = BuddyType.FootnoteBold, color = Color.White)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BuddyColor.Canvas)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy, onClick = onDecline),
                contentAlignment = Alignment.Center,
            ) {
                if (isDeclining) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = BuddyColor.InkMuted, strokeWidth = 2.dp)
                } else {
                    Text("Ahora no", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                }
            }
        }
    }
}

private fun relativeArrival(iso: String?): String? {
    val date = iso?.let { runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull() } ?: return null
    return when (val days = ChronoUnit.DAYS.between(LocalDate.now(), date).toInt()) {
        0 -> "Llega hoy"
        1 -> "Llega mañana"
        else -> "En $days días"
    }
}

// ── Connection Row — estilo WhatsApp ───────────────────────────────────────
@Composable
private fun ConnectionRow(item: ConnectionItem, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar con punto online
        Box {
            BuddyAvatar(
                imageUrl = item.avatarUrl,
                name = item.displayName,
                size = if (isActive) 50.dp else 44.dp,
            )
            if (isActive) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .background(BuddyColor.Canvas, CircleShape)
                        .padding(2.dp)
                        .background(BuddyColor.Accent, CircleShape),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.displayName,
                    style = if (isActive) BuddyType.Headline else BuddyType.Callout,
                    color = BuddyColor.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(item.lastTime, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
            // Desde dónde piden ayuda — mismo dato que ya muestran las tarjetas
            // de oportunidades. Sin esto, con dos ciudades abiertas a la vez las
            // conversaciones son indistinguibles.
            item.contextLine?.let { context ->
                Text(context, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.lastMessage != null && item.isLastFromMe) {
                    Text("Tú:", style = BuddyType.Footnote, color = BuddyColor.InkMuted.copy(alpha = 0.8f))
                }
                Text(
                    item.lastText,
                    style = BuddyType.Footnote.copy(
                        fontWeight = if (item.unreadCount > 0) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (item.unreadCount > 0) BuddyColor.Ink else BuddyColor.InkMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (item.unreadCount > 0) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(BuddyColor.ErrorRed, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${item.unreadCount}",
                            style = BuddyType.Caption1.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = Color.White,
                        )
                    }
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        Modifier.size(14.dp),
                        tint = BuddyColor.InkMuted.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

// ── Empty state — "Las conexiones nacen de un trip" ────────────────────────
@Composable
private fun EmptyConnectionsState(onCreateTrip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.People, contentDescription = null,
            Modifier.size(44.dp), tint = BuddyColor.InkMuted.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(Spacing.md))
        Text("Las conexiones nacen de un trip", style = BuddyType.Callout, color = BuddyColor.Ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cuando llegues a tu destino, un buddy te estará esperando.",
            style = BuddyType.Footnote, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(BuddyColor.Ink)
                .clickable(onClick = onCreateTrip)
                .padding(horizontal = Spacing.lg, vertical = 12.dp),
        ) {
            Text("Crear mi trip", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
        }
    }
}
