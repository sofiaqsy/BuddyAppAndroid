package com.buddy.app.features.trips.register

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Espejo 1:1 de RegisterTripView (iOS): eyebrow dinámico, búsqueda con
 * populares, resultados en vivo, community card por estado, llegada
 * (Ya estoy aquí / Hoy / Mañana / otra fecha), toggles de planificación
 * y CTA cápsula "Crear trip →".
 */
@Composable
fun RegisterTripScreen(
    onCreated: (ApiJourney) -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterTripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState())) {
        // Back (adaptación Android: barra con flecha; iOS usa el nav stack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = BuddyColor.Ink)
            }
        }

        // Eyebrow solo al planificar: si ya está en el lugar el campo viene
        // prellenado y no hace falta título (iOS quitó "DÓNDE ESTÁS AHORA").
        if (state.quickOption != QuickOption.Here) {
            Text(
                "TU PRÓXIMO TRIP",
                style = BuddyType.Eyebrow.copy(letterSpacing = 2.sp),
                color = BuddyColor.InkMuted,
                modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md),
            )
        }

        SectionLabel("¿DÓNDE ESTÁS?")

        // ── Search field ──────────────────────────────────────────────────
        Row(
            Modifier
                .padding(horizontal = Spacing.edge)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(BuddyColor.Surface)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.isSearching) {
                CircularProgressIndicator(Modifier.size(15.dp), color = BuddyColor.Brand, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.InkMuted)
            }
            Box(Modifier.weight(1f)) {
                if (state.searchText.isEmpty()) {
                    Text("¿A dónde vas?", style = BuddyType.Callout, color = BuddyColor.InkFaint)
                }
                BasicTextField(
                    value = state.searchText,
                    onValueChange = viewModel::onSearchTextChange,
                    textStyle = BuddyType.Callout.copy(color = BuddyColor.Ink),
                    cursorBrush = SolidColor(BuddyColor.Brand),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.searchText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Cancel, contentDescription = "Limpiar",
                    Modifier.size(17.dp).clickable(onClick = viewModel::clearSearch),
                    tint = BuddyColor.InkMuted,
                )
            }
        }

        val hasTyped = state.searchText.trim().isNotEmpty()

        // ── Resultados en vivo / populares ────────────────────────────────
        if (hasTyped && !state.hasSelection) {
            if (!state.isSearching && state.searchResults.isEmpty()) {
                Row(
                    Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocationOff, contentDescription = null, Modifier.size(15.dp), tint = BuddyColor.InkMuted)
                    Text("Sin resultados para \"${state.searchText}\"", style = BuddyType.Callout, color = BuddyColor.InkMuted)
                }
            } else if (state.searchResults.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Column(
                    Modifier
                        .padding(horizontal = Spacing.edge)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(BuddyColor.Surface)
                        .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md)),
                ) {
                    state.searchResults.take(8).forEachIndexed { idx, place ->
                        if (idx > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.md), color = BuddyColor.Hairline)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPlace(place) }
                                .padding(horizontal = Spacing.md, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.InkMuted)
                            Column {
                                Text(place.title, style = BuddyType.Callout, color = BuddyColor.Ink)
                                if (!place.subtitle.isNullOrEmpty()) {
                                    Text(place.subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                                }
                            }
                        }
                    }
                }
            }
        } else if (!hasTyped && state.popularDests.isNotEmpty()) {
            Text(
                "POPULARES",
                style = BuddyType.Eyebrow.copy(letterSpacing = 1.5.sp),
                color = BuddyColor.InkMuted,
                modifier = Modifier.padding(start = Spacing.edge, top = Spacing.lg, bottom = Spacing.sm),
            )
            // ChipFlow — wrap de chips de destinos (FlowLayout de iOS)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(horizontal = Spacing.edge),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.popularDests.forEach { dest ->
                    DestinationChip(
                        name = dest.name,
                        isSelected = state.selectedDest?.id == dest.id,
                        onTap = { viewModel.selectDest(dest) },
                    )
                }
            }
        } else if (!hasTyped && state.popularLoadFailed) {
            TextButton(onClick = viewModel::loadPopular, modifier = Modifier.padding(horizontal = Spacing.edge)) {
                Text("Reintentar destinos populares", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }

        // ── Community context card ────────────────────────────────────────
        if (state.hasSelection) {
            Spacer(Modifier.height(Spacing.sm))
            PlaceCommunityCard(
                context = state.placeContext,
                isLoading = state.isLoadingContext,
                modifier = Modifier.padding(horizontal = Spacing.edge),
            )
        }

        // ── Arrival ───────────────────────────────────────────────────────
        Spacer(Modifier.height(Spacing.xl))
        SectionLabel("¿CUÁNDO LLEGAS?")
        val tomorrow = LocalDate.now().plusDays(1)
        Row(
            Modifier.padding(horizontal = Spacing.edge),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            QuickOptionCard(Icons.Filled.LocationOn, "Ya estoy\naquí", "", state.quickOption == QuickOption.Here, Modifier.weight(1f)) {
                viewModel.selectQuick(QuickOption.Here)
            }
            QuickOptionCard(Icons.Filled.WbTwilight, "Hoy", shortDate(LocalDate.now()), state.quickOption == QuickOption.Today, Modifier.weight(1f)) {
                viewModel.selectQuick(QuickOption.Today)
            }
            QuickOptionCard(Icons.Filled.NightsStay, "Mañana", shortDate(tomorrow), state.quickOption == QuickOption.Tomorrow, Modifier.weight(1f)) {
                viewModel.selectQuick(QuickOption.Tomorrow)
            }
        }

        // Fecha específica — camino alternativo
        Spacer(Modifier.height(Spacing.sm))
        val customDate = state.quickOption == null
        Row(
            Modifier
                .padding(horizontal = Spacing.edge)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(BuddyColor.Surface)
                .border(
                    if (customDate) 1.5.dp else 1.dp,
                    if (customDate) BuddyColor.Brand else BuddyColor.Border,
                    RoundedCornerShape(Radius.md),
                )
                .clickable(onClick = viewModel::toggleDatePicker)
                .padding(horizontal = Spacing.md, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CalendarMonth, contentDescription = null, Modifier.size(15.dp),
                tint = if (customDate) BuddyColor.Brand else BuddyColor.InkMuted,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                if (customDate) formattedDate(state.selectedDate) else "O elige otra fecha",
                style = BuddyType.Callout,
                color = if (customDate) BuddyColor.Ink else BuddyColor.InkMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (state.showDatePicker) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.InkMuted,
            )
        }
        if (state.showDatePicker) {
            DatePickerModal(
                initial = state.selectedDate,
                onSelect = viewModel::selectDate,
                onDismiss = viewModel::toggleDatePicker,
            )
        }

        // ── Planning questions ────────────────────────────────────────────
        if (state.showPlanningQuestions) {
            Spacer(Modifier.height(Spacing.md))
            SectionLabel("¿YA LO TIENES RESUELTO?")
            Column(
                Modifier.padding(horizontal = Spacing.edge),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PlanningToggleCard(Icons.Filled.DirectionsBus, "Cómo llegar", "Bus, vuelo, ruta", state.knowsHowToGet, viewModel::setKnowsHowToGet)
                PlanningToggleCard(Icons.Filled.House, "Dónde hospedarte", "Hotel, hostal, casa", state.hasLodging, viewModel::setHasLodging)
            }
        }

        // ── CTA "Crear trip →" ────────────────────────────────────────────
        Spacer(Modifier.height(Spacing.lg))
        Row(
            Modifier
                .padding(horizontal = Spacing.edge)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (state.canCreate) BuddyColor.Ink else BuddyColor.InkMuted.copy(alpha = 0.25f))
                .clickable(enabled = state.canCreate && !state.isCreating) {
                    viewModel.createTrip(onCreated)
                }
                .padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isCreating) {
                CircularProgressIndicator(Modifier.size(18.dp), color = BuddyColor.InkInverse, strokeWidth = 2.dp)
            } else {
                Text(
                    "Crear trip",
                    style = BuddyType.FootnoteBold,
                    color = if (state.canCreate) BuddyColor.InkInverse else BuddyColor.InkMuted,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                    Modifier.size(13.dp),
                    tint = if (state.canCreate) BuddyColor.InkInverse else BuddyColor.InkMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl + 60.dp))
    }

    // Alert "No pudimos crear tu trip" — misma copy que iOS
    if (state.showCreateError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            containerColor = BuddyColor.Surface,
            title = { Text("No pudimos crear tu trip", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = { Text("Revisa tu conexión e inténtalo de nuevo.", style = BuddyType.Subhead, color = BuddyColor.InkMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError(); viewModel.createTrip(onCreated) }) {
                    Text("Reintentar", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text("Cancelar", color = BuddyColor.InkMuted, style = BuddyType.FootnoteBold)
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = BuddyType.Eyebrow.copy(letterSpacing = 1.5.sp),
        color = BuddyColor.InkMuted,
        modifier = Modifier.padding(start = Spacing.edge, bottom = Spacing.sm),
    )
}

/** Espejo de DestinationChip (iOS): cápsula con pin, brand al seleccionar. */
@Composable
private fun DestinationChip(name: String, isSelected: Boolean, onTap: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) BuddyColor.Brand.copy(alpha = 0.10f) else BuddyColor.Surface)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) BuddyColor.Brand else BuddyColor.Border,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            Icons.Filled.Place, contentDescription = null, Modifier.size(12.dp),
            tint = if (isSelected) BuddyColor.Brand else BuddyColor.Ink,
        )
        Text(name, style = BuddyType.Callout, color = if (isSelected) BuddyColor.Brand else BuddyColor.Ink)
    }
}

/** Espejo de QuickOptionCard (iOS). */
@Composable
private fun QuickOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(if (isSelected) BuddyColor.Brand.copy(alpha = 0.07f) else BuddyColor.Surface)
            .border(
                if (isSelected) 1.5.dp else 1.dp,
                if (isSelected) BuddyColor.Brand else BuddyColor.Border,
                RoundedCornerShape(Radius.md),
            )
            .clickable(onClick = onTap)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = if (isSelected) BuddyColor.Brand else BuddyColor.InkMuted)
        Text(title, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, minLines = 2, maxLines = 2)
        Text(subtitle.ifEmpty { " " }, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
    }
}

/** Espejo de PlanningToggleCard (iOS). */
@Composable
private fun PlanningToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (isOn) BuddyColor.Brand.copy(alpha = 0.06f) else BuddyColor.Surface)
            .border(1.dp, if (isOn) BuddyColor.Brand.copy(alpha = 0.4f) else BuddyColor.Border, RoundedCornerShape(Radius.md))
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = if (isOn) BuddyColor.Brand else BuddyColor.InkMuted)
        Column(Modifier.weight(1f)) {
            Text(title, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text(subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BuddyColor.Brand),
        )
    }
}

/** Espejo de PlaceCommunityCard (iOS) — misma copy y semántica de colores. */
@Composable
fun PlaceCommunityCard(context: ApiPlaceContext?, isLoading: Boolean, modifier: Modifier = Modifier) {
    val ctx = context
    if (isLoading || ctx == null) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(BuddyColor.Surface)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
                .padding(Spacing.md)
                .height(56.dp),
        )
        return
    }
    val (dot, bg, borderC) = when (ctx.status) {
        "active" -> Triple(BuddyColor.Accent, BuddyColor.Accent.copy(alpha = 0.06f), BuddyColor.Accent.copy(alpha = 0.3f))
        "growing" -> Triple(BuddyColor.WarningAmber, BuddyColor.WarningAmber.copy(alpha = 0.06f), BuddyColor.WarningAmber.copy(alpha = 0.3f))
        else -> Triple(BuddyColor.InkMuted, BuddyColor.Surface, BuddyColor.Border)
    }
    val headline = when (ctx.status) {
        "active" -> "Comunidad activa"
        "growing" -> "Comunidad creciendo"
        else -> "Sé el primero aquí"
    }
    val copy = when (ctx.status) {
        "active" -> "Siempre encontrarás alguien que te ayude."
        "growing" -> "Ya hay personas ayudando en esta zona."
        else -> "Todavía no hay buddies en este lugar. Puedes crear el primer trip."
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(bg)
            .border(1.dp, borderC, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Text(headline, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
        }
        Text(copy, style = BuddyType.Callout, color = BuddyColor.InkMuted)
        if (ctx.buddies > 0 || ctx.stories > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (ctx.buddies > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.People, contentDescription = null, Modifier.size(12.dp), tint = BuddyColor.InkMuted)
                        Text("${ctx.buddies} buddy${if (ctx.buddies == 1) "" else "s"}", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
                if (ctx.stories > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, Modifier.size(12.dp), tint = BuddyColor.InkMuted)
                        Text("${ctx.stories} histori${if (ctx.stories == 1) "a" else "as"}", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initial: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onSelect(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
                }
            }) { Text("Aceptar", color = BuddyColor.Brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = BuddyColor.InkMuted) } },
    ) {
        DatePicker(state = pickerState)
    }
}

private val esLocale = Locale("es", "PE")
private fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", esLocale))
private fun formattedDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", esLocale)).replaceFirstChar { it.uppercase() }
