package com.buddy.app.features.conexiones

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyAvatar
import com.buddy.app.core.designsystem.components.BuddyCard
import com.buddy.app.core.designsystem.components.BuddyEmptyState
import com.buddy.app.core.designsystem.components.BuddyLoading
import com.buddy.app.core.designsystem.components.BuddyPrimaryButton
import com.buddy.app.core.designsystem.components.BuddySectionHeader
import com.buddy.app.core.designsystem.components.BuddyStatusBadge
import com.buddy.app.core.designsystem.components.BuddyTextButton
import com.buddy.app.features.matching.data.ApiBuddyOffer
import com.buddy.app.features.matching.data.ApiMatch
import com.buddy.app.features.messages.ChatScreen

/**
 * Espejo de ConexionesView (iOS): solicitudes entrantes (para buddies)
 * y conversaciones activas. Tap en un match → chat (back gesture regresa).
 */
@Composable
fun ConexionesScreen(modifier: Modifier = Modifier, viewModel: ConexionesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var openMatch by rememberSaveable { mutableStateOf<String?>(null) }

    val currentMatch = state.matches.firstOrNull { it.id == openMatch }
    if (openMatch != null) {
        BackHandler { openMatch = null }
        ChatScreen(
            matchId = openMatch!!,
            title = currentMatch?.buddy?.fullName ?: currentMatch?.traveler?.fullName ?: "Chat",
            onBack = { openMatch = null },
            modifier = modifier,
        )
        return
    }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Conexiones",
            style = BuddyType.DisplayHero,
            color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge),
        )
        Spacer(Modifier.height(Spacing.sm))

        when {
            state.isLoading -> BuddyLoading()
            state.matches.isEmpty() && state.solicitudes.isEmpty() -> BuddyEmptyState(
                icon = Icons.Filled.People,
                title = "Sin conexiones todavía",
                message = "Cuando te conectes con un buddy, la conversación aparecerá aquí.",
            )
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.edge, end = Spacing.edge, bottom = 100.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.solicitudes.isNotEmpty()) {
                    item { BuddySectionHeader("Solicitudes", Modifier.padding(horizontal = 0.dp)) }
                    items(state.solicitudes, key = { it.id }) { offer ->
                        SolicitudCard(
                            offer = offer,
                            onAccept = { viewModel.acceptSolicitud(offer.requestId) },
                            onDecline = { viewModel.declineSolicitud(offer.requestId) },
                        )
                    }
                }
                if (state.matches.isNotEmpty()) {
                    item { BuddySectionHeader("Conversaciones", Modifier.padding(horizontal = 0.dp)) }
                    items(state.matches, key = { it.id }) { match ->
                        MatchRow(match, onTap = { openMatch = match.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun SolicitudCard(offer: ApiBuddyOffer, onAccept: () -> Unit, onDecline: () -> Unit) {
    val traveler = offer.helpRequest?.users
    BuddyCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BuddyAvatar(imageUrl = traveler?.avatarUrl, name = traveler?.fullName)
            Column(Modifier.weight(1f)) {
                Text(traveler?.fullName ?: "Viajero", style = BuddyType.Headline, color = BuddyColor.Ink)
                if (offer.helpRequest?.description != null) {
                    Text(
                        offer.helpRequest.description,
                        style = BuddyType.Caption1,
                        color = BuddyColor.InkMuted,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            BuddyPrimaryButton("Aceptar", onClick = onAccept, modifier = Modifier.weight(1f))
            BuddyTextButton("Rechazar", onClick = onDecline)
        }
    }
}

@Composable
private fun MatchRow(match: ApiMatch, onTap: () -> Unit) {
    val other = match.buddy ?: match.traveler
    BuddyCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md, onClick = onTap) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BuddyAvatar(imageUrl = other?.avatarUrl, name = other?.fullName)
            Column(Modifier.weight(1f)) {
                Text(other?.fullName ?: "Buddy", style = BuddyType.Headline, color = BuddyColor.Ink)
                Text(
                    when (match.status) {
                        "active", "accepted" -> "Conversación activa"
                        "pending" -> "Pendiente"
                        "completed" -> "Completada"
                        else -> match.status
                    },
                    style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                )
            }
            if (match.status in listOf("active", "accepted")) {
                BuddyStatusBadge("Activo", available = true)
            }
        }
    }
}
