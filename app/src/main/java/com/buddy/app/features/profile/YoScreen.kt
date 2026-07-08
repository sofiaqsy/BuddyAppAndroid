package com.buddy.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyTextField
import com.buddy.app.features.authentication.SessionViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Espejo 1:1 de YoView (iOS): header "TU PERFIL / Tu historia." + menú ⋯
 * (Cerrar sesión / Eliminar mi cuenta), profileHeader (avatar + metaLine +
 * "Viajando desde"), bio editable inline, fila/CTA de Buddy, STICKERS con
 * slots punteados y TRIPS en grid 3 col con celdas fantasma.
 * Guests ven la invitación "Tu historia viaja contigo".
 */
@Composable
fun YoScreen(
    modifier: Modifier = Modifier,
    onOpenTrips: () -> Unit = {},
    viewModel: YoViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val session by sessionViewModel.session.collectAsState()
    val isSigningIn by sessionViewModel.isSigningIn.collectAsState()
    val signInError by sessionViewModel.error.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBecomeBuddyConfirm by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState())) {
        // ── Header editorial + menú de cuenta ──────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text("TU PERFIL", style = BuddyType.Eyebrow.copy(letterSpacing = 2.sp), color = BuddyColor.InkMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Tu ") }
                        withStyle(SpanStyle(color = BuddyColor.Brand)) { append("historia.") }
                    },
                    style = BuddyType.Title1,
                )
            }
            if (session?.isVerified == true) {
                AccountMenu(
                    onLogout = { showLogoutConfirm = true },
                    onDelete = { showDeleteConfirm = true },
                )
            }
        }

        if (session?.isVerified != true) {
            // ── Vista anónima — "Tu historia viaja contigo" ─────────────────
            AnonymousProfileState(
                isLoading = isSigningIn,
                error = signInError,
                onGoogle = { sessionViewModel.signInWithGoogle(context) },
            )
        } else if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BuddyColor.InkMuted, strokeWidth = 2.5.dp)
            }
        } else {
            // 1 — Identidad
            ProfileHeader(state, Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.lg))
            // 2 — Bio
            BioSection(state, viewModel, Modifier.padding(horizontal = Spacing.edge))
            Spacer(Modifier.height(Spacing.xl))
            // 3 — Rol buddy: fila nav si es buddy; CTA discreto si no
            if (state.buddyMe?.isBuddy == true) {
                BuddyNavRow(state, Modifier.padding(horizontal = Spacing.edge))
            } else {
                BecomeBuddyCTA(
                    isLoading = state.isBecomingBuddy,
                    onTap = { showBecomeBuddyConfirm = true },
                    modifier = Modifier.padding(horizontal = Spacing.edge),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            // 4 — Colección
            StickerSection(state)
            Spacer(Modifier.height(Spacing.xl))
            TripsSection(state, onOpenTrips)
        }
        Spacer(Modifier.height(100.dp))
    }

    // ── Diálogos — misma copy que iOS ──────────────────────────────────────
    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "¿Cerrar sesión?",
            message = "Tendrás que volver a verificar tu identidad. Puedes regresar cuando quieras.",
            confirmLabel = "Cerrar sesión",
            onConfirm = { showLogoutConfirm = false; sessionViewModel.signOut() },
            onDismiss = { showLogoutConfirm = false },
        )
    }
    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "¿Eliminar tu cuenta?",
            message = "Se eliminarán todos tus datos personales. Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar mi cuenta",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteAccount { sessionViewModel.signOut() }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
    if (showBecomeBuddyConfirm) {
        AlertDialog(
            onDismissRequest = { showBecomeBuddyConfirm = false },
            containerColor = BuddyColor.Surface,
            title = { Text("Alguien está llegando a tu ciudad.", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = {
                Text(
                    "Los Buddies son personas locales que eligen estar cuando alguien llega por primera vez. Tú sabes cosas que ningún mapa puede mostrar.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showBecomeBuddyConfirm = false; viewModel.becomeBuddy() }) {
                    Text("Ser Buddy en mi ciudad", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBecomeBuddyConfirm = false }) {
                    Text("Ahora no", color = BuddyColor.InkMuted, style = BuddyType.FootnoteBold)
                }
            },
        )
    }
    if (state.bioSaveFailed) {
        ConfirmDialog(
            title = "No pudimos guardar",
            message = "Tu bio no se guardó. Verifica tu conexión e inténtalo de nuevo.",
            confirmLabel = "Entendido",
            onConfirm = viewModel::dismissBioError,
            onDismiss = viewModel::dismissBioError,
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BuddyColor.Surface,
        title = { Text(title, style = BuddyType.Headline, color = BuddyColor.Ink) },
        text = { Text(message, style = BuddyType.Subhead, color = BuddyColor.InkMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = BuddyColor.ErrorRed, style = BuddyType.FootnoteBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = BuddyColor.Brand, style = BuddyType.FootnoteBold) }
        },
    )
}

@Composable
private fun AccountMenu(onLogout: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Opciones de cuenta", tint = BuddyColor.InkMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Cerrar sesión", style = BuddyType.Body, color = BuddyColor.Ink) },
                onClick = { expanded = false; onLogout() },
            )
            DropdownMenuItem(
                text = { Text("Eliminar mi cuenta", style = BuddyType.Body, color = BuddyColor.ErrorRed) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

// ── Profile header — avatar 88 + identidad ─────────────────────────────────
@Composable
private fun ProfileHeader(state: YoViewModel.State, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Box {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(BuddyColor.GroupedBg),
                contentAlignment = Alignment.Center,
            ) {
                if (state.user?.avatarUrl != null) {
                    AsyncImage(
                        model = state.user.avatarUrl, contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp),
                    )
                } else {
                    Icon(Icons.Filled.Person, contentDescription = null, Modifier.size(36.dp), tint = BuddyColor.Brand)
                }
            }
            // Badge cámara — el upload de avatar llega en la fase de Memoir/fotos
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .background(BuddyColor.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Cambiar foto", Modifier.size(11.dp), tint = BuddyColor.InkInverse)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(state.user?.fullName ?: "Tú", style = BuddyType.Title3, color = BuddyColor.Ink, maxLines = 1)
            Text(state.metaLine, style = BuddyType.Subhead, color = BuddyColor.InkMuted)
            memberSinceLabel(state.user?.memberSince)?.let {
                Text("Viajando desde $it", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }
    }
}

private fun memberSinceLabel(iso: String?): String? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull()
        ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "PE")))
}

// ── Bio — vista/edición inline ─────────────────────────────────────────────
@Composable
private fun BioSection(state: YoViewModel.State, viewModel: YoViewModel, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Column(modifier) {
        if (editing) {
            BuddyTextField(
                value = draft, onValueChange = { draft = it },
                placeholder = "Cuéntanos algo sobre ti…",
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancelar", style = BuddyType.Footnote, color = BuddyColor.InkMuted,
                    modifier = Modifier.clickable { editing = false },
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Ink)
                        .clickable(enabled = !state.isSavingBio) {
                            viewModel.saveBio(draft) { editing = false }
                        }
                        .padding(horizontal = Spacing.md, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isSavingBio) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Guardar", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { draft = state.user?.bio.orEmpty(); editing = true }
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    state.user?.bio?.takeIf { it.isNotEmpty() } ?: "Cuéntale al mundo quién eres…",
                    style = BuddyType.Callout,
                    color = if (state.user?.bio.isNullOrEmpty()) BuddyColor.InkMuted.copy(alpha = 0.7f) else BuddyColor.Ink,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.Edit, contentDescription = "Editar bio", Modifier.size(13.dp), tint = BuddyColor.InkMuted)
            }
        }
    }
}

// ── Buddy row / CTA ─────────────────────────────────────────────────────────
@Composable
private fun BuddyNavRow(state: YoViewModel.State, modifier: Modifier = Modifier) {
    val p = state.buddyMe?.profile
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.Filled.HowToReg, contentDescription = null, Modifier.size(18.dp), tint = BuddyColor.Accent)
        Column(Modifier.weight(1f)) {
            Text("Perfil de Buddy", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text(
                if (p?.isAvailable == true) "Disponible para ayudar" else "No disponible ahora",
                style = BuddyType.Caption1, color = BuddyColor.InkMuted,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BuddyColor.InkMuted.copy(alpha = 0.5f))
    }
}

@Composable
private fun BecomeBuddyCTA(isLoading: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        HorizontalDivider(color = BuddyColor.Hairline)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading, onClick = onTap)
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Ser Buddy en mi ciudad", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            Spacer(Modifier.weight(1f))
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(12.dp), color = BuddyColor.InkMuted, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    Modifier.size(11.dp), tint = BuddyColor.InkMuted.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Stickers — colección con slots de progresión ───────────────────────────
@Composable
private fun StickerSection(state: YoViewModel.State) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("STICKERS", state.stickers.size)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.edge),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            state.stickers.forEach { s ->
                Column(
                    Modifier.width(72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(BuddyColor.GroupedBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.stickerCatalog?.imageUrl != null) {
                            AsyncImage(
                                model = s.stickerCatalog.imageUrl, contentDescription = s.stickerCatalog.name,
                                contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp),
                            )
                        } else {
                            Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(24.dp), tint = BuddyColor.Brand)
                        }
                    }
                    Text(s.stickerCatalog?.name ?: "Sticker", style = BuddyType.Caption1, color = BuddyColor.Ink, maxLines = 1)
                    stickerDate(s.unlockedAt)?.let {
                        Text(it, style = BuddyType.Caption2, color = BuddyColor.InkMuted)
                    }
                }
            }
            // Slots vacíos punteados — hay más por coleccionar
            repeat(maxOf(0, 3 - state.stickers.size)) {
                Box(
                    Modifier
                        .width(72.dp)
                        .size(64.dp)
                        .drawBehind {
                            drawCircle(
                                color = BuddyColor.InkMuted.copy(alpha = 0.25f),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.QuestionMark, contentDescription = null,
                        Modifier.size(18.dp), tint = BuddyColor.InkMuted.copy(alpha = 0.3f),
                    )
                }
            }
        }
        Text(
            "Cada sticker guarda un lugar que te recibió.",
            style = BuddyType.Caption1, color = BuddyColor.InkMuted,
            modifier = Modifier.padding(horizontal = Spacing.edge),
        )
    }
}

private fun stickerDate(iso: String?): String? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull()
        ?.format(DateTimeFormatter.ofPattern("d MMM", Locale("es", "PE")))
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.padding(horizontal = Spacing.edge),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = BuddyType.Eyebrow.copy(letterSpacing = 1.5.sp), color = BuddyColor.Ink)
        if (count > 0) Text("· $count", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
    }
}

// ── Trips — grid 3 columnas con celdas fantasma ────────────────────────────
@Composable
private fun TripsSection(state: YoViewModel.State, onOpenTrips: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SectionHeader("TRIPS", state.journeys.size)
        if (state.journeys.isEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = Spacing.edge)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(BuddyColor.Surface)
                    .clickable(onClick = onOpenTrips)
                    .padding(vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, Modifier.size(32.dp), tint = BuddyColor.InkMuted)
                Text("Tu primer trip te espera", style = BuddyType.Callout, color = BuddyColor.InkMuted)
            }
        } else {
            val remainder = state.journeys.size % 3
            val ghosts = if (remainder == 0) 0 else 3 - remainder
            Column(Modifier.padding(horizontal = Spacing.edge), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (state.journeys.map { TripCell.Journey(it) } + List(ghosts) { TripCell.Ghost })
                    .chunked(3)
                    .forEach { rowCells ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            rowCells.forEach { cell ->
                                when (cell) {
                                    is TripCell.Journey -> TripGridCell(cell.journey, Modifier.weight(1f))
                                    TripCell.Ghost -> Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(Radius.sm))
                                            .background(BuddyColor.Surface)
                                            .clickable(onClick = onOpenTrips),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Add, contentDescription = null,
                                            Modifier.size(22.dp), tint = BuddyColor.InkMuted.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

private sealed interface TripCell {
    data class Journey(val journey: ApiJourney) : TripCell
    data object Ghost : TripCell
}

@Composable
private fun TripGridCell(journey: ApiJourney, modifier: Modifier = Modifier) {
    val thumb = journey.pageThumbs?.firstOrNull() ?: journey.coverUrl ?: journey.destination?.coverUrl
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(Radius.sm)).background(BuddyColor.GroupedBg)) {
        if (thumb != null) {
            AsyncImage(
                model = thumb, contentDescription = journey.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
        if (journey.isPublic == false) {
            Text(
                "Privado",
                style = BuddyType.Caption2.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

// ── Vista anónima — "Tu historia viaja contigo" ────────────────────────────
@Composable
private fun AnonymousProfileState(
    isLoading: Boolean,
    error: String?,
    onGoogle: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = Spacing.edge, vertical = Spacing.lg)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.lg))
            .padding(Spacing.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).background(BuddyColor.GroupedBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Route, contentDescription = null, Modifier.size(18.dp), tint = BuddyColor.Brand)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Tu historia viaja contigo", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                Text(
                    "Crea tu perfil para que Buddy recuerde cada parte del camino.",
                    style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        HorizontalDivider(color = BuddyColor.Border)
        Spacer(Modifier.height(Spacing.lg))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AnonymousBenefit(Icons.Filled.Person, "Tus viajes", "Cada destino pasa a ser parte de tu historia.")
            AnonymousBenefit(Icons.Filled.CameraAlt, "Tus momentos", "Las fotos y recuerdos que guardaste te siguen a donde vayas.")
            AnonymousBenefit(Icons.Filled.Star, "Tus stickers", "Recuerdos de los lugares que te recibieron.")
            AnonymousBenefit(Icons.Filled.HowToReg, "Tu perfil de Buddy", "Si decides ayudar a otros viajeros, puedes configurarlo desde aquí.")
        }
        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = BuddyColor.Border)
        Spacer(Modifier.height(Spacing.md))
        Text(
            "Continúa con", style = BuddyType.Caption1, color = BuddyColor.InkMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .background(BuddyColor.Canvas)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                .clickable(enabled = !isLoading, onClick = onGoogle),
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
            Text(error, style = BuddyType.Caption1, color = BuddyColor.ErrorRed, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            style = BuddyType.Caption1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
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
