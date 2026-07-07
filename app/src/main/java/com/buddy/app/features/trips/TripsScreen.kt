package com.buddy.app.features.trips

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import com.buddy.app.core.designsystem.components.BuddyLoading

/**
 * Espejo 1:1 de TripsView (iOS):
 * - Header "TU BITÁCORA" + "Tu trip." (trip. en brand) + menú ⋯ del trip
 *   seleccionado (única acción: Cancelar trip, con confirmación destructiva)
 * - Selector horizontal SOLO si hay >1 trips (long-press → Eliminar lugar)
 * - UN TripFeedCard: el trip seleccionado
 * - Empty state: icono mapa + "Tu próximo trip te espera" + botón cápsula
 *   "＋ Registrar trip" (único punto de registro del tab, igual que iOS)
 */
@Composable
fun TripsScreen(
    modifier: Modifier = Modifier,
    onOpenConexiones: () -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCancelConfirm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ApiJourney?>(null) }

    // Registro a pantalla completa — espejo del navigation push de iOS
    if (state.showRegisterSheet) {
        com.buddy.app.features.trips.register.RegisterTripScreen(
            onCreated = { viewModel.closeRegister(); viewModel.load() },
            onBack = viewModel::closeRegister,
        )
        return
    }

    Column(
        modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState()),
    ) {
        // ── Header — título + acciones del trip seleccionado ──────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TU BITÁCORA",
                    style = BuddyType.Eyebrow.copy(letterSpacing = 2.sp),
                    color = BuddyColor.InkMuted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Tu ") }
                        withStyle(SpanStyle(color = BuddyColor.Brand)) { append("trip.") }
                    },
                    style = BuddyType.Title1,
                )
            }
            if (state.selectedTrip != null) {
                TripActionsMenu(onCancelTrip = { showCancelConfirm = true })
            }
        }

        // ── Selector horizontal — solo con más de un trip ──────────────────
        if (state.visibleTrips.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.edge),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.visibleTrips.forEach { trip ->
                    TripSelectorCard(
                        journey = trip,
                        isSelected = trip.id == state.selectedTrip?.id,
                        onTap = { viewModel.selectTrip(trip.id) },
                        onLongPress = { deleteTarget = trip },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }

        // ── Contenido según estado del trip seleccionado ───────────────────
        when {
            state.isLoading -> BuddyLoading(Modifier.height(480.dp))
            state.selectedTrip != null -> TripFeedCard(
                journey = state.selectedTrip!!,
                buddyName = state.activeBuddyName,
                buddyAvatarUrl = state.activeBuddyAvatarUrl,
                onEdit = { /* editor de momentos (Memoir) — próxima fase */ },
                onBuddyTap = onOpenConexiones,
                modifier = Modifier.padding(horizontal = Spacing.edge),
            )
            else -> EmptyTripsState(onRegister = viewModel::openRegister)
        }

        Spacer(Modifier.height(100.dp))
    }


    // Confirmación "¿Cancelar tu viaje?" — misma copy que iOS
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            containerColor = BuddyColor.Surface,
            title = { Text("¿Cancelar tu viaje?", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = {
                Text(
                    "Se eliminarán todos los lugares de este viaje y sus momentos. No se puede deshacer.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCancelConfirm = false; viewModel.cancelSelectedTrip() }) {
                    Text("Cancelar trip", color = BuddyColor.ErrorRed, style = BuddyType.FootnoteBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("Mantener", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
        )
    }

    // Confirmación "¿Eliminar este lugar?" — misma copy que iOS
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = BuddyColor.Surface,
            title = { Text("¿Eliminar este lugar?", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = {
                Text(
                    "Se quitará este lugar del viaje. El resto se mantiene.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTarget?.let(viewModel::deleteJourney); deleteTarget = null }) {
                    Text("Eliminar lugar", color = BuddyColor.ErrorRed, style = BuddyType.FootnoteBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
        )
    }
}

/** Menú ⋯ del viaje — única acción: cancelar (destructiva), igual que iOS. */
@Composable
private fun TripActionsMenu(onCancelTrip: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Acciones del trip", tint = BuddyColor.Ink)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Cancelar trip", color = BuddyColor.ErrorRed, style = BuddyType.Body) },
                onClick = { expanded = false; onCancelTrip() },
            )
        }
    }
}

/** Espejo de TripSelectorCard (iOS): cover 116×70 + nombre; borde brand al seleccionar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripSelectorCard(
    journey: ApiJourney,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val name = journey.destination?.name ?: journey.title ?: "Trip"
    val outerShape = RoundedCornerShape(Radius.md + 2.dp)
    Column(
        Modifier
            .clip(outerShape)
            .background(if (isSelected) BuddyColor.Surface else Color.Transparent)
            .border(2.dp, if (isSelected) BuddyColor.Brand else Color.Transparent, outerShape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(4.dp)
            .width(116.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AsyncImage(
            model = journey.destination?.coverUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 116.dp, height = 70.dp).clip(RoundedCornerShape(Radius.md)),
        )
        Text(
            name,
            style = BuddyType.Caption1,
            color = if (isSelected) BuddyColor.Ink else BuddyColor.InkMuted,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

/** Empty state — icono mapa, copy exacta y botón cápsula ink "＋ Registrar trip". */
@Composable
private fun EmptyTripsState(onRegister: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(Icons.Filled.Map, contentDescription = null, Modifier.size(40.dp), tint = BuddyColor.InkMuted)
        Text("Tu próximo trip te espera", style = BuddyType.Title3, color = BuddyColor.Ink)
        Text(
            "Registra tu próximo destino\ny conecta con un buddy.",
            style = BuddyType.Callout,
            color = BuddyColor.InkMuted,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier
                .padding(top = Spacing.sm)
                .clip(RoundedCornerShape(50))
                .background(BuddyColor.Ink)
                .clickable(onClick = onRegister)
                .padding(horizontal = Spacing.lg, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.InkInverse)
            Text("Registrar trip", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
        }
    }
}
