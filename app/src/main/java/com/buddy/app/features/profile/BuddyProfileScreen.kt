package com.buddy.app.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.features.profile.data.ApiBuddyMe
import com.buddy.app.features.profile.data.ApiPlaceGuide
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/** Categorías de ayuda — mismas keys/labels que categoryOptions (iOS). */
private val categoryOptions = listOf(
    "transport" to "Transporte",
    "food" to "Comer",
    "shopping" to "Compras",
    "activities" to "Actividades",
    "accommodation" to "Alojamiento",
    "recommendations" to "Consejos",
)

/**
 * "Sé buddy en mi ciudad" — espejo 1:1 de BuddyProfileView (iOS): estados de
 * verificación (approved/pending/rejected), disponibilidad, zonas de
 * cobertura con picker de lugares, especialidades por zona y guía (spots /
 * visitas / stickers) con preview de mapa.
 */
@Composable
fun BuddyProfileScreen(
    profile: ApiBuddyMe.BuddyProfile,
    onBack: () -> Unit,
    onUpdated: (ApiBuddyMe.BuddyProfile) -> Unit,
    viewModel: BuddyProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showReapplyAlert by remember { mutableStateOf(false) }
    var expandedZone by remember { mutableStateOf<ZoneEntry?>(null) }

    LaunchedEffect(profile.id) { viewModel.initialize(profile) }
    LaunchedEffect(state.profile) { state.profile?.let(onUpdated) }

    Column(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = BuddyColor.Ink)
            }
            Spacer(Modifier.size(4.dp))
            Text("Tu perfil de Buddy", style = BuddyType.Headline, color = BuddyColor.Ink)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
            when (state.status) {
                "approved" -> ApprovedContent(state, viewModel, onZoneExpand = { expandedZone = it })
                "pending" -> PendingContent(state, viewModel, onZoneExpand = { expandedZone = it })
                else -> RejectedContent(onReapply = { showReapplyAlert = true })
            }
        }
    }

    if (state.showZonePicker) {
        ZonePickerDialog(state, viewModel)
    }

    expandedZone?.let { zone ->
        state.placeGuides[zone.id]?.let { guide ->
            if (guide.lat != null && guide.lng != null) {
                BuddyGuideMapScreen(
                    zoneId = zone.id, source = zone.source, destId = guide.destId, zoneName = zone.name,
                    initialCenter = guide.lat to guide.lng, previewSpots = guide.spots.orEmpty(),
                    onDismiss = {
                        expandedZone = null
                        viewModel.loadGuides(force = true) // refresca stats tras editar (como iOS)
                    },
                )
            }
        }
    }

    if (showReapplyAlert) {
        AlertDialog(
            onDismissRequest = { showReapplyAlert = false },
            containerColor = BuddyColor.Surface,
            title = { Text("Gracias por tu interés", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = {
                Text(
                    "Pronto habilitaremos la opción para volver a solicitar. Si tienes preguntas, escríbenos.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showReapplyAlert = false }) {
                    Text("OK", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
        )
    }
}

// ── Estado: Aprobado ─────────────────────────────────────────────────────────
@Composable
private fun ApprovedContent(
    state: BuddyProfileViewModel.State,
    viewModel: BuddyProfileViewModel,
    onZoneExpand: (ZoneEntry) -> Unit,
) {
    Column {
        Column(
            Modifier
                .padding(horizontal = Spacing.edge)
                .padding(top = Spacing.lg)
                .fillMaxWidth()
                .background(BuddyColor.Brand.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                .padding(Spacing.md),
        ) {
            Text(
                "Ayuda a viajeros hoy y construye la guía para quienes lleguen mañana.",
                style = BuddyType.Title2, color = BuddyColor.Brand,
            )
        }

        // Toggle disponibilidad
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge).padding(top = Spacing.xl, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Disponible ahora", style = BuddyType.Callout, color = BuddyColor.Ink)
                Text(
                    if (state.isAvailable) "Los viajeros pueden contactarte" else "No recibirás solicitudes",
                    style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                )
            }
            if (state.savingAvailability) {
                CircularProgressIndicator(Modifier.size(20.dp), color = BuddyColor.Brand, strokeWidth = 2.dp)
            } else {
                Switch(
                    checked = state.isAvailable,
                    onCheckedChange = { viewModel.setAvailable(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = BuddyColor.Brand),
                )
            }
        }

        HorizontalDivider(color = BuddyColor.Border, modifier = Modifier.padding(horizontal = Spacing.edge))

        state.zones.forEach { zone ->
            ZoneCard(
                zone = zone, guide = state.placeGuides[zone.id], specialties = state.specialties,
                onRemove = { viewModel.removeZone(zone.id) },
                onToggleSpecialty = { viewModel.toggleSpecialty(it) },
                onExpandMap = { onZoneExpand(zone) },
                modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.md),
            )
        }

        AddZoneButton(
            hasZones = state.zones.isNotEmpty(),
            onTap = viewModel::openZonePicker,
            modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.sm),
        )

        val helps = state.profile?.totalHelps ?: 0
        if (helps > 0) {
            Text(
                if (helps == 1) "1 viajero acompañado" else "$helps viajeros acompañados",
                style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.xl),
            )
        }
    }
}

// ── Estado: Pendiente ────────────────────────────────────────────────────────
@Composable
private fun PendingContent(
    state: BuddyProfileViewModel.State,
    viewModel: BuddyProfileViewModel,
    onZoneExpand: (ZoneEntry) -> Unit,
) {
    Column {
        Column(
            Modifier
                .padding(horizontal = Spacing.edge)
                .padding(top = Spacing.lg)
                .fillMaxWidth()
                .background(BuddyColor.Canvas, RoundedCornerShape(16.dp))
                .padding(Spacing.md),
        ) {
            Text("Tu solicitud\nestá en camino.", style = BuddyType.Title2, color = BuddyColor.BrandDeep)
            Spacer(Modifier.height(6.dp))
            Text(
                "Estamos revisando tu perfil. Mientras tanto puedes preparar dónde y cómo quieres ayudar.",
                style = BuddyType.Callout, color = BuddyColor.Brand,
            )
        }

        state.zones.forEach { zone ->
            ZoneCard(
                zone = zone, guide = state.placeGuides[zone.id], specialties = state.specialties,
                onRemove = { viewModel.removeZone(zone.id) },
                onToggleSpecialty = { viewModel.toggleSpecialty(it) },
                onExpandMap = { onZoneExpand(zone) },
                modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.md),
            )
        }

        AddZoneButton(
            hasZones = state.zones.isNotEmpty(),
            onTap = viewModel::openZonePicker,
            modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.sm),
        )
    }
}

// ── Estado: No aprobado ──────────────────────────────────────────────────────
@Composable
private fun RejectedContent(onReapply: () -> Unit) {
    Column {
        Column(
            Modifier
                .padding(horizontal = Spacing.edge)
                .padding(top = Spacing.lg)
                .fillMaxWidth()
                .background(BuddyColor.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp))
                .padding(Spacing.md),
        ) {
            Text("Por ahora no podemos\nincluirte en la comunidad.", style = BuddyType.Title2, color = BuddyColor.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "A veces necesitamos más tiempo para revisar los perfiles. Puedes volver a solicitarlo cuando quieras.",
                style = BuddyType.Callout, color = BuddyColor.InkMuted,
            )
        }

        Text(
            "Volver a solicitar",
            style = BuddyType.Callout, color = BuddyColor.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = Spacing.edge)
                .padding(top = Spacing.md)
                .fillMaxWidth()
                .background(BuddyColor.Surface, RoundedCornerShape(14.dp))
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(14.dp))
                .clickable(onClick = onReapply)
                .padding(vertical = 14.dp),
        )

        Text(
            "Si tienes preguntas, escríbenos. Respondemos a cada solicitud con atención.",
            style = BuddyType.Caption1, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.edge).padding(top = Spacing.md),
        )
    }
}

// ── Card por zona: especialidades + guía del lugar ───────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneCard(
    zone: ZoneEntry,
    guide: ApiPlaceGuide?,
    specialties: Set<String>,
    onRemove: () -> Unit,
    onToggleSpecialty: (String) -> Unit,
    onExpandMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp)),
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Brand)
            Text(zone.name, style = BuddyType.Callout.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Ink, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(BuddyColor.Canvas)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar", Modifier.size(10.dp), tint = BuddyColor.InkMuted)
            }
        }
        HorizontalDivider(color = BuddyColor.Border, modifier = Modifier.padding(horizontal = 14.dp))

        // Especialidades
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("Cómo puedo ayudar", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categoryOptions.forEach { (key, label) ->
                    val on = key in specialties
                    Text(
                        label,
                        style = BuddyType.Caption1.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (on) BuddyColor.Brand else BuddyColor.InkMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) BuddyColor.Brand.copy(alpha = 0.12f) else BuddyColor.Canvas)
                            .border(
                                if (on) 1.dp else 0.5.dp,
                                if (on) BuddyColor.Brand else BuddyColor.Border,
                                RoundedCornerShape(50),
                            )
                            .clickable { onToggleSpecialty(key) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = BuddyColor.Border, modifier = Modifier.padding(horizontal = 14.dp))

        // Guía del lugar
        Text(
            "GUÍA DEL LUGAR", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
        )
        when {
            guide == null -> CircularProgressIndicator(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp).size(16.dp),
                color = BuddyColor.InkMuted, strokeWidth = 2.dp,
            )
            guide.spotCount == 0 -> Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).padding(bottom = 12.dp)) {
                Text("Ningún lugar todavía.", style = BuddyType.Callout, color = BuddyColor.Ink)
                Text("Comienza agregando el primero.", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
            else -> Row(
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                GuideStat(guide.spotCount, if (guide.spotCount == 1) "lugar" else "lugares")
                if (guide.visitCount > 0) GuideStat(guide.visitCount, if (guide.visitCount == 1) "visita" else "visitas")
                if (guide.stickerCount > 0) GuideStat(guide.stickerCount, if (guide.stickerCount == 1) "sticker" else "stickers")
            }
        }

        if (guide?.lat != null && guide.lng != null) {
            ZoneMapPreview(
                lat = guide.lat, lng = guide.lng, spots = guide.spots.orEmpty(),
                onTap = onExpandMap,
                modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp).fillMaxWidth().height(180.dp),
            )
        }
    }
}

@Composable
private fun GuideStat(value: Int, label: String) {
    Column {
        Text("$value", style = BuddyType.Title3, color = BuddyColor.Ink)
        Text(label, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
    }
}

@Composable
private fun AddZoneButton(hasZones: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BuddyColor.Brand.copy(alpha = 0.06f))
            .border(1.dp, BuddyColor.Brand.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.AddCircleOutline, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Brand)
        Spacer(Modifier.size(6.dp))
        Text(
            if (hasZones) "Agregar ciudad" else "Agregar mi primera ciudad",
            style = BuddyType.Callout.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Brand,
        )
    }
}

// ── Preview de mapa (no interactivo) — espejo de ZoneMapView (iOS) ──────────
@Composable
private fun ZoneMapPreview(
    lat: Double, lng: Double, spots: List<com.buddy.app.features.profile.data.ApiPlaceGuideSpot>,
    onTap: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTap)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(false)
                    setOnTouchListener { _, _ -> true } // no interactivo — solo preview (iOS: .disabled(true))
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(13.5)
                    controller.setCenter(GeoPoint(lat, lng))
                    spots.forEach { spot ->
                        overlays.add(
                            Marker(this).apply {
                                position = GeoPoint(spot.lat, spot.lng)
                                title = spot.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            },
                        )
                    }
                }
            },
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(BuddyColor.Surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(6.dp),
        ) {
            Icon(Icons.Filled.OpenInFull, contentDescription = null, Modifier.size(11.dp), tint = BuddyColor.Ink.copy(alpha = 0.75f))
        }
    }
}

// ── Picker de lugar/ciudad — espejo de PlaceZonePickerSheet (iOS) ───────────
@Composable
private fun ZonePickerDialog(state: BuddyProfileViewModel.State, viewModel: BuddyProfileViewModel) {
    Dialog(onDismissRequest = viewModel::closeZonePicker, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Elegir ciudad", style = BuddyType.Headline, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
                Text(
                    "Cancelar", style = BuddyType.Callout, color = BuddyColor.Brand,
                    modifier = Modifier.clickable(onClick = viewModel::closeZonePicker),
                )
            }
            Row(
                Modifier
                    .padding(horizontal = Spacing.edge)
                    .padding(bottom = Spacing.sm)
                    .fillMaxWidth()
                    .background(BuddyColor.Surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(15.dp), tint = BuddyColor.InkMuted)
                androidx.compose.foundation.text.BasicTextField(
                    value = state.pickerQuery,
                    onValueChange = viewModel::searchZoneQuery,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = BuddyColor.Ink),
                    decorationBox = { inner ->
                        if (state.pickerQuery.isEmpty()) {
                            Text("Buscar ciudad o país…", style = BuddyType.Callout, color = BuddyColor.InkMuted)
                        }
                        inner()
                    },
                )
                if (state.pickerQuery.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Limpiar", Modifier.size(15.dp).clickable {
                            viewModel.searchZoneQuery("")
                        },
                        tint = BuddyColor.InkMuted,
                    )
                }
            }
            HorizontalDivider(color = BuddyColor.Border)

            when {
                // Sin búsqueda activa: ofrecer el lugar actual como atajo —
                // espejo de la sugerencia por GPS en PlaceZonePickerSheet (iOS).
                state.pickerQuery.trim().isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.suggestion != null -> {
                            Text(
                                "SUGERIDO CERCA DE TI", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted,
                                modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.sm),
                            )
                            SuggestionRow(state.suggestion, state.isResolvingPick) { viewModel.pickResult(state.suggestion) }
                        }
                        state.isLoadingSuggestion -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BuddyColor.InkMuted, strokeWidth = 2.dp)
                        }
                        else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Icon(Icons.Filled.LocationOff, contentDescription = null, Modifier.size(30.dp), tint = BuddyColor.InkMuted)
                                Text("Escribe para buscar", style = BuddyType.Callout, color = BuddyColor.InkMuted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                state.isSearching && state.pickerResults.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BuddyColor.InkMuted, strokeWidth = 2.dp)
                }
                state.pickerResults.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Filled.LocationOff, contentDescription = null, Modifier.size(30.dp), tint = BuddyColor.InkMuted)
                        Text(
                            "Sin resultados para \"${state.pickerQuery}\"",
                            style = BuddyType.Callout, color = BuddyColor.InkMuted, textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(state.pickerResults, key = { it.id }) { place ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !state.isResolvingPick) { viewModel.pickResult(place) }
                                .padding(horizontal = Spacing.edge, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(BuddyColor.Brand.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (place.source == "destination") Icons.Filled.LocationOn else Icons.Filled.Public,
                                    contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.Brand,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(place.title, style = BuddyType.Callout, color = BuddyColor.Ink)
                                place.subtitle?.let { Text(it, style = BuddyType.Caption1, color = BuddyColor.InkMuted) }
                            }
                            if (state.isResolvingPick) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Misma fila visual que un resultado de búsqueda, pero con ícono de
 *  ubicación actual y subtítulo fijo (en vez de país/tipo de lugar). */
@Composable
private fun SuggestionRow(
    place: com.buddy.app.core.data.model.ApiPlaceResult,
    isResolving: Boolean,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isResolving, onClick = onTap)
            .padding(horizontal = Spacing.edge, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(BuddyColor.Brand.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.Brand)
        }
        Column(Modifier.weight(1f)) {
            Text(place.title, style = BuddyType.Callout, color = BuddyColor.Ink)
            Text("Tu ubicación actual", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
        if (isResolving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    }
}
