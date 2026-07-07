package com.buddy.app.features.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyCard
import com.buddy.app.core.designsystem.components.BuddyEmptyState
import com.buddy.app.core.designsystem.components.BuddyLoading

/** Espejo de TripsView (iOS) — TripFeedCard por journey + registro. */
@Composable
fun TripsScreen(
    modifier: Modifier = Modifier,
    onOpenConexiones: () -> Unit = {},
    viewModel: TripsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().background(BuddyColor.Canvas)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Tu trip",
            style = BuddyType.DisplayHero,
            color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge),
        )
        Spacer(Modifier.height(Spacing.md))

        when {
            state.isLoading -> BuddyLoading(Modifier.height(300.dp))
            state.journeys.isEmpty() -> Column {
                BuddyEmptyState(
                    icon = Icons.Filled.Luggage,
                    title = "Aún no tienes trips",
                    message = "Registra tu próximo viaje y prepara tu llegada.",
                )
                com.buddy.app.core.designsystem.components.BuddyPrimaryButton(
                    text = "Registrar trip",
                    onClick = viewModel::openRegister,
                    modifier = Modifier.padding(horizontal = Spacing.edge),
                )
            }
            else -> Column(
                Modifier.padding(horizontal = Spacing.edge),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                state.journeys.forEach { journey ->
                    TripFeedCard(
                        journey = journey,
                        buddyName = state.activeBuddyName,
                        buddyAvatarUrl = state.activeBuddyAvatarUrl,
                        onEdit = { /* editor de momentos (Memoir) — próxima fase */ },
                        onBuddyTap = onOpenConexiones,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                com.buddy.app.core.designsystem.components.BuddySecondaryButton(
                    text = "Registrar otro trip",
                    onClick = viewModel::openRegister,
                )
            }
        }
        Spacer(Modifier.height(100.dp))
    }

    if (state.showRegisterSheet) {
        RegisterTripSheet(
            results = state.searchResults,
            onQueryChange = viewModel::search,
            onSelect = viewModel::registerTrip,
            onDismiss = viewModel::closeRegister,
        )
    }
}

@Composable
private fun TripRow(journey: ApiJourney) {
    BuddyCard(modifier = Modifier.fillMaxWidth(), contentPadding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (journey.destination?.coverUrl != null) {
                AsyncImage(
                    model = journey.destination.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(Radius.sm)),
                )
            }
            Column {
                Text(
                    journey.destination?.name ?: journey.title ?: "Trip",
                    style = BuddyType.Headline,
                    color = BuddyColor.Ink,
                )
                Text(journey.status, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }
    }
}
