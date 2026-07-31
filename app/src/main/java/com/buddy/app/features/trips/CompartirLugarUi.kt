package com.buddy.app.features.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.buddy.app.core.data.model.ApiPlaceResult
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyCard
import com.buddy.app.core.designsystem.components.BuddyGroupedRow
import com.buddy.app.core.designsystem.components.BuddyTextField

// Fase 2 de "Buddy Community Places": el CTA en Tu Trip para que un buddy
// aprobado documente un lugar suelto, sin que eso toque su trip personal —
// reutiliza TripsViewModel.shareCurrentLocation/shareSearchResult
// (createJourney con attachToTrip=false) y el mismo editor Memoir del flujo
// normal. Espejo 1:1 de CompartirLugarView.swift. Deliberadamente discreta:
// si el uso confirma la idea, se le da más protagonismo en una segunda versión.

@Composable
fun CompartirLugarCard(onTap: () -> Unit, modifier: Modifier = Modifier) {
    BuddyCard(modifier = modifier, onClick = onTap) {
        Text("¿ERES BUDDY?", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌍", style = BuddyType.Headline) // 🌍
            Spacer(Modifier.width(Spacing.sm))
            Text("Compartir un lugar", style = BuddyType.Headline, color = BuddyColor.Ink)
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Ayuda a futuros viajeros compartiendo fotos de un lugar que conoces.",
            style = BuddyType.Footnote,
            color = BuddyColor.InkMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text("Compartir", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompartirLugarSheet(
    step: ShareLugarStep,
    searchResults: List<ApiPlaceResult>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSearchInstead: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPickResult: (ApiPlaceResult) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.edge).padding(bottom = Spacing.lg)) {
            Text("Compartir un lugar", style = BuddyType.Title3, color = BuddyColor.Ink)
            Spacer(Modifier.height(Spacing.md))

            when (step) {
                ShareLugarStep.Choose -> {
                    Text("¿DÓNDE ESTÁS AHORA?", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                    Spacer(Modifier.height(Spacing.sm))
                    optionRow(
                        icon = Icons.Filled.LocationOn,
                        title = "Lugar actual",
                        subtitle = "recomendado",
                        isLoading = isSubmitting,
                        enabled = !isSubmitting,
                        onClick = onUseCurrentLocation,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    optionRow(
                        icon = Icons.Filled.Search,
                        title = "Buscar otro lugar",
                        subtitle = null,
                        isLoading = false,
                        enabled = !isSubmitting,
                        onClick = onSearchInstead,
                    )
                }
                ShareLugarStep.Search -> {
                    var query by remember { mutableStateOf("") }
                    BuddyTextField(
                        value = query,
                        onValueChange = { query = it; onQueryChange(it) },
                        placeholder = "Buscar un lugar",
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    LazyColumn(Modifier.height(320.dp)) {
                        items(searchResults) { result ->
                            BuddyGroupedRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                onClick = { if (!isSubmitting) onPickResult(result) },
                            ) {
                                Column {
                                    Text(result.title, style = BuddyType.Body, color = BuddyColor.Ink)
                                    result.subtitle?.let {
                                        Text(it, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                                    }
                                }
                            }
                        }
                    }
                    if (isSubmitting) {
                        Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = BuddyType.Caption1, color = BuddyColor.ErrorRed)
            }
        }
    }
}

@Composable
private fun optionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BuddyGroupedRow(modifier = Modifier.fillMaxWidth(), onClick = if (enabled) onClick else null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(title, style = BuddyType.Headline, color = BuddyColor.Ink)
                    subtitle?.let { Text(it, style = BuddyType.Caption1, color = BuddyColor.InkMuted) }
                }
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp))
        }
    }
}

enum class ShareLugarStep { Choose, Search }
