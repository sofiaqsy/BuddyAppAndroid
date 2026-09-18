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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Place
import com.buddy.app.features.home.data.ApiNearbySpot
import com.buddy.app.features.home.data.ApiSpotCategoryRef
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
    /** Spots curados a la redonda. SON las opciones del primer paso: si la fila
     *  ya dice el nombre del local, tocarla ES la elección — una segunda
     *  pantalla preguntando "¿en cuál estás?" repetiría lo que esa fila ya
     *  respondió. */
    nearbySpots: List<ApiNearbySpot>,
    isPrefetchingNearby: Boolean,
    searchResults: List<ApiNearbySpot>,
    categories: List<ApiSpotCategoryRef>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onStep: (ShareLugarStep) -> Unit,
    onPickSpot: (ApiNearbySpot) -> Unit,
    onQueryChange: (String) -> Unit,
    onPropose: (nombre: String, categoriaId: String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Spacing.edge).padding(bottom = Spacing.lg)) {
            Text("Compartir un lugar", style = BuddyType.Title3, color = BuddyColor.Ink)
            Spacer(Modifier.height(Spacing.md))

            when (step) {
                ShareLugarStep.Choose -> {
                    Text("¿DÓNDE ESTÁS AHORA?", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                    Spacer(Modifier.height(Spacing.sm))

                    nearbySpots.firstOrNull()?.let { actual ->
                        optionRow(
                            icon = Icons.Filled.LocationOn,
                            title = "${actual.name} (Lugar actual)",
                            subtitle = if (actual.estaPendiente) "${actual.distanciaLabel} · por revisar" else actual.distanciaLabel,
                            isLoading = isSubmitting,
                            enabled = !isSubmitting,
                            onClick = { onPickSpot(actual) },
                        )
                        Spacer(Modifier.height(Spacing.sm))

                        // Los demás dentro del radio: el GPS puede errar por unos
                        // metros y dos locales caben en ese margen.
                        nearbySpots.drop(1).take(4).forEach { spot ->
                            optionRow(
                                icon = Icons.Filled.Place,
                                title = spot.name,
                                subtitle = if (spot.estaPendiente) "${spot.distanciaLabel} · por revisar" else spot.distanciaLabel,
                                isLoading = false,
                                enabled = !isSubmitting,
                                onClick = { onPickSpot(spot) },
                            )
                            Spacer(Modifier.height(Spacing.sm))
                        }
                    }

                    // Siempre presente, haya lista o no: el buddy puede estar en
                    // un local que el catálogo todavía no conoce, y buscarlo por
                    // texto no sirve — si no está aquí, tampoco está en el mapa.
                    optionRow(
                        icon = Icons.Filled.AddCircle,
                        title = "Registrar nuevo lugar",
                        subtitle = when {
                            nearbySpots.isNotEmpty() -> "ninguno de estos es"
                            isPrefetchingNearby -> "buscando…"
                            else -> "nombra dónde estás"
                        },
                        isLoading = false,
                        enabled = !isSubmitting && !isPrefetchingNearby,
                        onClick = { onStep(ShareLugarStep.Propose) },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    optionRow(
                        icon = Icons.Filled.Search,
                        title = "Buscar otro lugar",
                        subtitle = null,
                        isLoading = false,
                        enabled = !isSubmitting,
                        onClick = { onStep(ShareLugarStep.Search) },
                    )
                }

                ShareLugarStep.Search -> {
                    var query by remember { mutableStateOf("") }
                    BuddyTextField(
                        value = query,
                        onValueChange = { query = it; onQueryChange(it) },
                        placeholder = "Buscar en tus lugares",
                        // Todo el ancho: sin esto el campo quedaba en el
                        // ancho mínimo de Material, la mitad de la pantalla.
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.sm))

                    if (query.trim().length >= 2 && searchResults.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text("Ningún lugar del catálogo coincide", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                            Text(
                                "Registrarlo como nuevo",
                                style = BuddyType.FootnoteBold, color = BuddyColor.Ink,
                                modifier = Modifier.clickable { onStep(ShareLugarStep.Propose) },
                            )
                        }
                    }

                    LazyColumn(Modifier.height(320.dp)) {
                        items(searchResults, key = { it.id }) { spot ->
                            BuddyGroupedRow(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                                onClick = { if (!isSubmitting) onPickSpot(spot) },
                            ) {
                                Column {
                                    Text(spot.name, style = BuddyType.Body, color = BuddyColor.Ink)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(spot.distanciaLabel, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                                        if (spot.estaPendiente) {
                                            Text(
                                                "por revisar",
                                                style = BuddyType.Caption2, color = BuddyColor.InkMuted,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(BuddyColor.InkMuted.copy(alpha = 0.12f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            )
                                        }
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

                // Sin spots cerca: el buddy NOMBRA el lugar. Queda pendiente de
                // aprobación en el admin, pero puede documentarlo desde ya.
                ShareLugarStep.Propose -> {
                    var nombre by remember { mutableStateOf("") }
                    var categoriaId by remember { mutableStateOf<String?>(null) }

                    Text("NO ENCONTRAMOS ESTE LUGAR", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                    Spacer(Modifier.height(Spacing.sm))
                    Text("¿Cómo se llama?", style = BuddyType.Title3, color = BuddyColor.Ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Escríbelo y lo agregamos al mapa de la comunidad después de revisarlo.",
                        style = BuddyType.Footnote, color = BuddyColor.InkMuted,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    BuddyTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        placeholder = "Ej. Cafetería Rosal",
                        // Todo el ancho: sin esto el campo quedaba en el
                        // ancho mínimo de Material, la mitad de la pantalla.
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // La categoría la elige quien está viendo el local, así la
                    // propuesta llega clasificada al admin en vez de tener que
                    // adivinarla.
                    if (categories.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.md))
                        Text("¿QUÉ TIPO DE LUGAR ES?", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            categories.forEach { categoria ->
                                val elegida = categoriaId == categoria.id
                                Text(
                                    categoria.name,
                                    style = BuddyType.Footnote,
                                    color = if (elegida) BuddyColor.InkInverse else BuddyColor.Ink,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (elegida) BuddyColor.Ink else BuddyColor.Surface)
                                        .border(
                                            1.dp,
                                            if (elegida) Color.Transparent else BuddyColor.Border,
                                            RoundedCornerShape(50),
                                        )
                                        // Volver a tocar la misma categoría la deselecciona.
                                        .clickable { categoriaId = if (elegida) null else categoria.id }
                                        .padding(horizontal = Spacing.md, vertical = 9.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.md))
                    val listo = nombre.trim().isNotEmpty() && !isSubmitting
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(if (listo) BuddyColor.Ink else BuddyColor.InkMuted)
                            .clickable(enabled = listo) { onPropose(nombre, categoriaId) }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.InkInverse,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Text("Compartir aquí", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Buscar en el mapa",
                        style = BuddyType.Footnote, color = BuddyColor.InkMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStep(ShareLugarStep.Search) }
                            .padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
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

enum class ShareLugarStep { Choose, Search, Propose }
