package com.buddy.app.features.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.features.profile.data.ApiPlaceGuideSpot
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import kotlinx.coroutines.launch

private val spotCategories = listOf(
    Triple("landmark", "Cultura", Icons.Filled.AccountBalance),
    Triple("cafe", "Café", Icons.Filled.LocalCafe),
    Triple("park", "Naturaleza", Icons.Filled.Park),
    Triple("market", "Mercado", Icons.Filled.Storefront),
    Triple("activity", "Actividad", Icons.Filled.DirectionsWalk),
    Triple("hidden", "Secreto", Icons.Filled.AutoAwesome),
)

/**
 * Mapa completo de la guía del buddy — espejo de BuddyGuideMapSheet (iOS).
 * Soporta: navegar spots, editar (agregar / mover / renombrar / borrar).
 * Long-press en modo edición activa el crosshair para agregar un spot nuevo.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BuddyGuideMapScreen(
    zoneId: String,
    source: String,
    destId: String?,
    zoneName: String,
    initialCenter: Pair<Double, Double>,
    previewSpots: List<ApiPlaceGuideSpot>,
    onDismiss: () -> Unit,
    viewModel: BuddyGuideMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(zoneId) { viewModel.initialize(zoneId, source, destId, zoneName, previewSpots) }

    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var currentCenter by remember { mutableStateOf(GeoPoint(initialCenter.first, initialCenter.second)) }
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Encuadre inicial — espejo de .onAppear { fitAll() } (iOS): una vez que
    // el mapa y los spots del preview están listos, encuadra todos.
    LaunchedEffect(mapRef, state.localSpots) {
        val map = mapRef ?: return@LaunchedEffect
        // post {}: el View recién adjuntado por AndroidView puede no tener aún
        // tamaño medido — zoomToBoundingBox necesita ancho/alto reales.
        map.post {
            if (state.localSpots.isEmpty()) {
                map.controller.setZoom(14.0)
                map.controller.setCenter(GeoPoint(initialCenter.first, initialCenter.second))
            } else {
                val box = BoundingBox.fromGeoPoints(state.localSpots.map { GeoPoint(it.lat, it.lng) })
                map.zoomToBoundingBox(box, false, 120)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = screenHeight * 0.42f,
            sheetContainerColor = BuddyColor.Canvas,
            sheetShape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            sheetDragHandle = {
                Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).background(BuddyColor.Border, RoundedCornerShape(2.dp)))
            },
            sheetContent = {
                // El centro vivo del mapa es la posición real al guardar — igual que
                // currentCenter en iOS (se lee al tocar "Guardar", no al abrir el form).
                BuddyGuideSheetContent(state, viewModel, centerLat = currentCenter.latitude, centerLng = currentCenter.longitude)
            },
        ) { _ ->
            Box(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        Configuration.getInstance().userAgentValue = ctx.packageName
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(14.0)
                            controller.setCenter(currentCenter)
                            addMapListener(object : MapListener {
                                override fun onScroll(event: ScrollEvent?): Boolean {
                                    mapCenter.let { currentCenter = GeoPoint(it.latitude, it.longitude) }
                                    return true
                                }
                                override fun onZoom(event: ZoomEvent?): Boolean = true
                            })
                            // Long-press → agregar spot (solo editMode, con destino, sin sub-modo activo)
                            val receiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    viewModel.selectSpot(null)
                                    return false
                                }
                                override fun longPressHelper(p: GeoPoint?): Boolean {
                                    viewModel.startAddingNew()
                                    return true
                                }
                            }
                            overlays.add(MapEventsOverlay(receiver))
                            mapRef = this
                        }
                    },
                    update = { map ->
                        map.overlays.removeAll { it is Marker }
                        state.localSpots.forEach { spot ->
                            map.overlays.add(
                                Marker(map).apply {
                                    position = GeoPoint(spot.lat, spot.lng)
                                    title = spot.name
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    setOnMarkerClickListener { _, _ ->
                                        if (!state.editMode || state.inCrosshair) viewModel.selectSpot(spot.id)
                                        true
                                    }
                                },
                            )
                        }
                        map.invalidate()
                    },
                )

                // Crosshair — centro exacto del mapa mientras se agrega/mueve un spot
                if (state.inCrosshair) {
                    Icon(
                        Icons.Filled.AddCircle, contentDescription = null,
                        Modifier.align(Alignment.Center).size(40.dp), tint = BuddyColor.Brand,
                    )
                }

                // Barra superior
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp, start = Spacing.edge, end = Spacing.edge),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Cerrar",
                        Modifier.size(30.dp).clickable(onClick = onDismiss),
                        tint = BuddyColor.InkMuted,
                    )
                    Spacer(Modifier.weight(1f))
                    when {
                        state.inCrosshair || state.showEditForm || state.showAddForm -> Text(
                            "Cancelar", style = BuddyType.Callout.copy(fontWeight = FontWeight.Medium), color = BuddyColor.Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f))
                                .clickable { viewModel.cancelSubMode() }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                        else -> Text(
                            if (state.editMode) "Listo" else "Editar",
                            style = BuddyType.Callout.copy(fontWeight = FontWeight.Medium),
                            color = if (state.editMode) BuddyColor.Brand else BuddyColor.Ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.85f))
                                .clickable { viewModel.toggleEditMode() }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }

                // Fit-all — solo en modo navegación
                if (!state.editMode && !state.inCrosshair) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Spacing.md, bottom = screenHeight * 0.42f + Spacing.xl)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                val map = mapRef ?: return@clickable
                                if (state.localSpots.isEmpty()) {
                                    map.controller.setCenter(currentCenter)
                                } else {
                                    val box = BoundingBox.fromGeoPoints(state.localSpots.map { GeoPoint(it.lat, it.lng) })
                                    map.zoomToBoundingBox(box, true, 120)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = "Ver todo", Modifier.size(16.dp), tint = BuddyColor.Ink)
                    }
                }
            }
        }

        // Confirmar posición (alta/mover) — panel fijo abajo, superpuesto al
        // BottomSheetScaffold; depende del centro vivo del mapa (currentCenter).
        if (state.inCrosshair) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BuddyColor.Canvas)
                    .padding(Spacing.edge)
                    .padding(bottom = Spacing.xl),
            ) {
                Text(
                    if (state.addingNew) "Nuevo lugar" else "Mover lugar",
                    style = BuddyType.Title2, color = BuddyColor.Ink, modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Arrastra el mapa para posicionar el marcador",
                    style = BuddyType.Callout, color = BuddyColor.InkMuted,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = Spacing.md),
                )
                PrimaryActionButton(
                    label = if (state.addingNew) "Confirmar posición" else "Mover aquí",
                    isLoading = state.isSaving,
                    onClick = { viewModel.confirmPosition(currentCenter.latitude, currentCenter.longitude) },
                )
                Text(
                    "Cancelar", style = BuddyType.Callout, color = BuddyColor.InkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.cancelSubMode() }.padding(vertical = 13.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun BuddyGuideSheetContent(
    state: BuddyGuideMapViewModel.State,
    viewModel: BuddyGuideMapViewModel,
    centerLat: Double,
    centerLng: Double,
) {
    when {
        state.showAddForm -> SpotFormContent(
            title = "Nuevo lugar", name = state.spotName, placeType = state.spotPlaceType,
            isSaving = state.isSaving, saveError = state.saveError,
            onNameChange = viewModel::setSpotName, onTypeChange = viewModel::setSpotPlaceType,
            onSave = { viewModel.commitCreateSpot(centerLat, centerLng) },
            onCancel = { viewModel.setSpotName(""); viewModel.cancelSubMode() },
        )
        state.showEditForm -> {
            val spot = state.editingSpot!!
            SpotFormContent(
                title = "Editar lugar", name = state.spotName, placeType = state.spotPlaceType,
                isSaving = state.isSaving, saveError = state.saveError,
                onNameChange = viewModel::setSpotName, onTypeChange = viewModel::setSpotPlaceType,
                onSave = viewModel::commitUpdateSpot,
                onCancel = viewModel::cancelSubMode,
                onMove = viewModel::startMoving,
                onDelete = { viewModel.deleteSpot(spot.id) },
            )
        }
        state.inCrosshair -> Box(Modifier.height(1.dp)) // el picker de posición vive como overlay fijo (ver arriba)
        state.editMode -> EditSpotsList(state, viewModel)
        else -> BrowseSpotsList(state, viewModel)
    }
}

@Composable
private fun PrimaryActionButton(label: String, isLoading: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isLoading || !enabled) BuddyColor.Border else BuddyColor.Brand)
            .clickable(enabled = !isLoading && enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(label, style = BuddyType.Callout.copy(fontWeight = FontWeight.Medium), color = Color.White)
    }
}

@Composable
private fun SpotFormContent(
    title: String, name: String, placeType: String,
    isSaving: Boolean, saveError: String?,
    onNameChange: (String) -> Unit, onTypeChange: (String) -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit,
    onMove: (() -> Unit)? = null, onDelete: (() -> Unit)? = null,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
        Text(title, style = BuddyType.Title2, color = BuddyColor.Ink, modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.md))

        Text("Nombre", style = BuddyType.Caption1, color = BuddyColor.InkMuted, modifier = Modifier.padding(horizontal = Spacing.edge))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .padding(horizontal = Spacing.edge)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BuddyColor.Surface)
                .border(1.dp, BuddyColor.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (name.isEmpty()) Text("ej. Catarata El León", style = BuddyType.Callout, color = BuddyColor.InkMuted.copy(alpha = 0.6f))
            BasicTextField(
                value = name, onValueChange = onNameChange,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = BuddyColor.Ink),
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Text("Categoría", style = BuddyType.Caption1, color = BuddyColor.InkMuted, modifier = Modifier.padding(horizontal = Spacing.edge))
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.edge),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            spotCategories.forEach { (key, label, icon) ->
                val selected = placeType == key
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) BuddyColor.Brand else BuddyColor.Surface)
                        .border(if (selected) 0.dp else 1.dp, BuddyColor.Border, RoundedCornerShape(50))
                        .clickable { onTypeChange(key) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(icon, contentDescription = null, Modifier.size(13.dp), tint = if (selected) Color.White else BuddyColor.Ink)
                    Text(label, style = BuddyType.Footnote, color = if (selected) Color.White else BuddyColor.Ink)
                }
            }
        }

        saveError?.let {
            Text(it, style = BuddyType.Caption1, color = BuddyColor.ErrorRed, modifier = Modifier.padding(horizontal = Spacing.edge, vertical = 6.dp))
        }

        if (onMove != null) {
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier
                    .padding(horizontal = Spacing.edge)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BuddyColor.Brand.copy(alpha = 0.08f))
                    .border(1.dp, BuddyColor.Brand.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onMove)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Brand)
                Spacer(Modifier.size(6.dp))
                Text("Mover en el mapa", style = BuddyType.Callout, color = BuddyColor.Brand)
            }
        }

        Spacer(Modifier.height(Spacing.md))
        Box(Modifier.padding(horizontal = Spacing.edge)) {
            PrimaryActionButton(label = "Guardar", isLoading = isSaving, enabled = name.trim().isNotEmpty(), onClick = onSave)
        }

        if (onDelete != null) {
            Text(
                "Eliminar lugar", style = BuddyType.Callout, color = BuddyColor.ErrorRed,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.edge).clickable(onClick = onDelete).padding(vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun EditSpotsList(state: BuddyGuideMapViewModel.State, viewModel: BuddyGuideMapViewModel) {
    Column(Modifier.padding(bottom = Spacing.xl)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge).padding(top = Spacing.md, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Editar guía", style = BuddyType.Title2, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
            if (state.destId != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Brand.copy(alpha = 0.1f))
                        .clickable(onClick = viewModel::openAddFromEditSheet)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Brand)
                    Text("Agregar", style = BuddyType.Footnote.copy(fontWeight = FontWeight.Medium), color = BuddyColor.Brand)
                }
            }
        }

        when {
            state.editListLoading && state.editListSpots.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BuddyColor.InkMuted, strokeWidth = 2.dp)
            }
            state.editListSpots.isEmpty() -> Column(Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.md)) {
                Text("No hay lugares en esta guía todavía.", style = BuddyType.Callout, color = BuddyColor.Ink)
                if (state.destId != null) {
                    Text("Toca \"Agregar\" o mantén presionado el mapa.", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                }
            }
            else -> Column(
                Modifier
                    .padding(horizontal = Spacing.edge)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BuddyColor.Surface)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(16.dp)),
            ) {
                state.editListSpots.forEachIndexed { idx, spot ->
                    SpotEditRow(spot, state.deletingId, onEdit = { viewModel.startEditing(spot) }, onDelete = { viewModel.deleteSpot(spot.id) })
                    if (idx < state.editListSpots.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(color = BuddyColor.Border, modifier = Modifier.padding(start = 72.dp))
                    }
                }
                if (state.editListHasMore) {
                    Text(
                        if (state.editListLoading) "Cargando…" else "Cargar más",
                        style = BuddyType.Callout, color = BuddyColor.Brand,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !state.editListLoading) { viewModel.loadMoreEditList() }.padding(vertical = 14.dp),
                    )
                }
            }
        }
        if (state.editListError) {
            Text(
                "Error cargando lugares. Toca para reintentar.", style = BuddyType.Caption1, color = BuddyColor.ErrorRed,
                modifier = Modifier.padding(horizontal = Spacing.edge, vertical = Spacing.sm).clickable { viewModel.loadEditList() },
            )
        }
    }
}

@Composable
private fun SpotEditRow(spot: ApiPlaceGuideSpot, deletingId: String?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (spot.coverUrl != null) {
            AsyncImage(
                model = spot.coverUrl, contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(BuddyColor.Brand.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, Modifier.size(20.dp), tint = BuddyColor.Brand)
            }
        }
        Text(spot.name, style = BuddyType.Callout, color = BuddyColor.Ink, modifier = Modifier.weight(1f), maxLines = 2)
        if (deletingId == spot.id) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.InkMuted)
        } else {
            Icon(Icons.Filled.Edit, contentDescription = "Editar ${spot.name}", Modifier.size(18.dp).clickable(onClick = onEdit), tint = BuddyColor.Ink)
            Spacer(Modifier.size(14.dp))
            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${spot.name}", Modifier.size(18.dp).clickable(onClick = onDelete), tint = BuddyColor.ErrorRed)
        }
    }
}

@Composable
private fun BrowseSpotsList(state: BuddyGuideMapViewModel.State, viewModel: BuddyGuideMapViewModel) {
    Column(Modifier.padding(bottom = Spacing.xl)) {
        Text(
            state.zoneName, style = BuddyType.Title2, color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge).padding(top = Spacing.md, bottom = Spacing.sm),
        )
        if (state.localSpots.isEmpty()) {
            Text(
                "Ningún lugar todavía.", style = BuddyType.Callout, color = BuddyColor.InkMuted,
                modifier = Modifier.padding(horizontal = Spacing.edge),
            )
        } else {
            LazyColumn(Modifier.height(320.dp)) {
                items(state.localSpots, key = { it.id }) { spot ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectSpot(spot.id) }
                            .padding(horizontal = Spacing.edge, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (spot.coverUrl != null) {
                            AsyncImage(
                                model = spot.coverUrl, contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(BuddyColor.Brand.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null, Modifier.size(18.dp), tint = BuddyColor.Brand)
                            }
                        }
                        Text(
                            spot.name, style = BuddyType.Callout,
                            color = if (state.selectedSpotId == spot.id) BuddyColor.Brand else BuddyColor.Ink,
                            modifier = Modifier.weight(1f), maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
