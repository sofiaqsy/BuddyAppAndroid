package com.buddy.app.features.trips.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceBuddy
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.navigation.ComoLlegarDialog
import com.buddy.app.features.home.data.HomeApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import javax.inject.Inject

/** Datos del mapa: lugares del destino con portada, presencia y galería. */
@HiltViewModel
class TripMapViewModel @Inject constructor(
    private val mapApi: MapApi,
    private val homeApi: HomeApi,
) : ViewModel() {
    suspend fun spots(destinationId: String): List<ApiGuideSpot> =
        runCatching { mapApi.guideSpots(destinationId).spots }.getOrDefault(emptyList())

    suspend fun buddyCount(destinationId: String): Int? =
        runCatching { homeApi.placeContext(destinationId, "destination").buddies }.getOrNull()

    suspend fun fotos(spotId: String): List<FotoDeLugar> =
        runCatching { mapApi.spotGallery(spotId).fotos() }.getOrDefault(emptyList())

    suspend fun buddies(destinationId: String): List<ApiPlaceBuddy> =
        runCatching { mapApi.destinationBuddies(destinationId).buddies }.getOrDefault(emptyList())
}

/**
 * Mapa del destino — espejo de TripDetailView (iOS) sobre OpenStreetMap
 * (osmdroid: sin API keys).
 *
 * EL PANEL ES UNO SOLO
 *
 * Abajo hay un panel de alto FIJO que contiene dos cosas alternativas: el rail
 * de lugares o la ficha del lugar elegido. Elegir uno no levanta nada encima
 * del mapa ni cambia el alto del panel — el contenido se reemplaza en su sitio.
 * Una hoja modal aquí se siente como una interrupción; esto se lee como la
 * respuesta a haber tocado el lugar, con el mapa todavía visible detrás
 * diciendo dónde queda.
 */
@Composable
fun TripMapScreen(
    journey: ApiJourney,
    onBack: () -> Unit,
    viewModel: TripMapViewModel = hiltViewModel(),
) {
    val destName = journey.destination?.name ?: journey.title ?: "Trip"
    val destLat = journey.destination?.lat
    val destLng = journey.destination?.lng
    val destId = journey.destination?.id ?: journey.destinationId

    val spots by produceState(emptyList<ApiGuideSpot>(), destId) {
        value = if (destId != null) withContext(Dispatchers.IO) { viewModel.spots(destId) } else emptyList()
    }
    val buddyCount by produceState<Int?>(null, destId) {
        value = if (destId != null) withContext(Dispatchers.IO) { viewModel.buddyCount(destId) } else null
    }

    var selectedSpotId by remember { mutableStateOf<String?>(null) }
    var navigationTarget by remember { mutableStateOf<ApiGuideSpot?>(null) }
    var fotoAmpliada by remember { mutableStateOf<FotoDeLugar?>(null) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    val selectedSpot = spots.firstOrNull { it.id == selectedSpotId }

    // Galería y buddies se piden al ABRIR la ficha, no al cargar el mapa: son
    // decenas de fotos por lugar y casi ninguna se llega a mirar.
    var fotos by remember { mutableStateOf<List<FotoDeLugar>>(emptyList()) }
    var isLoadingFotos by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSpotId) {
        val id = selectedSpotId ?: return@LaunchedEffect
        fotos = emptyList()
        isLoadingFotos = true
        val cargadas = withContext(Dispatchers.IO) { viewModel.fotos(id) }
        // Descartar respuestas tardías: si mientras tanto se eligió otro lugar,
        // pintarlas mostraría las fotos de un sitio bajo el nombre de otro.
        if (selectedSpotId == id) {
            fotos = cargadas
            isLoadingFotos = false
        }
    }

    var buddies by remember { mutableStateOf<List<ApiPlaceBuddy>>(emptyList()) }
    var isLoadingBuddies by remember { mutableStateOf(true) }
    LaunchedEffect(destId) {
        if (destId == null) { isLoadingBuddies = false; return@LaunchedEffect }
        buddies = withContext(Dispatchers.IO) { viewModel.buddies(destId) }
        isLoadingBuddies = false
    }

    // Presencia humana — lo único que el mapa no puede mostrar solo (iOS)
    val presenceText = when {
        buddyCount == null -> null
        buddyCount!! <= 0 -> "Un buddy puede ayudarte si tienes una duda"
        buddyCount == 1 -> "1 buddy aquí, listo si tienes una duda"
        else -> "$buddyCount buddies aquí, listos si tienes una duda"
    }

    // El panel reserva además la barra de gestos: sin eso la fila de fotos
    // —lo último que se dibuja— quedaba cortada por el borde de la pantalla.
    val insetInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val center = remember(destLat, destLng, spots) {
        when {
            spots.isNotEmpty() -> GeoPoint(spots.map { it.lat }.average(), spots.map { it.lng }.average())
            destLat != null && destLng != null -> GeoPoint(destLat, destLng)
            else -> null
        }
    }

    navigationTarget?.let { spot ->
        ComoLlegarDialog(
            placeName = spot.name,
            lat = spot.lat,
            lng = spot.lng,
            onDismiss = { navigationTarget = null },
        )
    }

    fotoAmpliada?.let { foto ->
        FotoAmpliada(foto = foto, onClose = { fotoAmpliada = null })
    }

    DisposableEffect(Unit) { onDispose { mapRef?.onDetach() } }

    Box(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        if (center != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(if (spots.isEmpty()) 15.0 else 13.5)
                        controller.setCenter(center)
                        mapRef = this
                    }
                },
                update = { map ->
                    map.overlays.removeAll { it is Marker }
                    val markerSpots = spots.ifEmpty {
                        if (destLat != null && destLng != null) {
                            listOf(ApiGuideSpot(id = "dest", name = destName, lat = destLat, lng = destLng))
                        } else emptyList()
                    }
                    markerSpots.forEach { spot ->
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(spot.lat, spot.lng)
                                title = spot.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { m, mv ->
                                    // El marcador abre la MISMA ficha que la
                                    // tarjeta: tocar el pin y tocar la tarjeta
                                    // son la misma pregunta sobre el mismo sitio.
                                    selectedSpotId = spot.id
                                    mv.controller.animateTo(m.position, 15.5, 400L)
                                    true
                                }
                            },
                        )
                    }
                    map.invalidate()
                },
            )
            Text(
                "© OpenStreetMap",
                fontSize = 10.sp, color = BuddyColor.InkMuted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = PanelHeight + insetInferior + 4.dp)
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(Spacing.edge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Este lugar aún no tiene mapa", style = BuddyType.Title3, color = BuddyColor.Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pronto podrás explorar $destName aquí.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            }
        }

        // ── Chrome superior: back + nombre ───────────────────────────────────
        // La presencia se mudó al panel de abajo (como iOS): arriba competía
        // con el nombre del destino y tapaba mapa sin que nadie se lo pidiera.
        Row(
            Modifier.fillMaxWidth().padding(top = 56.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Volver", tint = BuddyColor.Ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                destName,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink,
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // ── Panel inferior: rail de lugares o ficha del elegido ──────────────
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PanelHeight + insetInferior)
                .shadow(14.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .padding(bottom = insetInferior),
        ) {
            AnimatedContent(
                targetState = selectedSpot,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "panel",
            ) { spot ->
                if (spot != null) {
                    PlaceGuideDetail(
                        spot = spot,
                        presenceText = presenceText,
                        fotos = fotos,
                        isLoadingFotos = isLoadingFotos,
                        buddies = buddies,
                        isLoadingBuddies = isLoadingBuddies,
                        onNavigate = { navigationTarget = spot },
                        onOpenPhoto = { fotoAmpliada = it },
                        onClose = { selectedSpotId = null },
                    )
                } else {
                    RailDeLugares(
                        spots = spots,
                        presenceText = presenceText,
                        onSelect = { elegido ->
                            selectedSpotId = elegido.id
                            mapRef?.controller?.animateTo(GeoPoint(elegido.lat, elegido.lng), 15.5, 400L)
                        },
                    )
                }
            }
        }
    }
}

/** Alto fijo del panel — el mismo con rail o con ficha, para que elegir un
 *  lugar no reacomode el mapa bajo el dedo. */
private val PanelHeight = 258.dp

@Composable
private fun RailDeLugares(
    spots: List<ApiGuideSpot>,
    presenceText: String?,
    onSelect: (ApiGuideSpot) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        // La presencia primero: las personas son el corazón de Buddy, antes que
        // los lugares.
        if (presenceText != null) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(BuddyColor.Accent))
                Text(
                    presenceText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = BuddyColor.Ink, maxLines = 1,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (spots.isEmpty()) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("Sin explorar aún", style = BuddyType.Callout, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink)
                Text(
                    "Nadie ha agregado recomendaciones aquí todavía. La guía de este lugar la construye su comunidad.",
                    style = BuddyType.Caption1, color = BuddyColor.InkMuted,
                )
            }
        } else {
            Text(
                "Recomendado por buddies",
                style = BuddyType.Title3, color = BuddyColor.Ink,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(spots, key = { _, s -> s.id }) { i, spot ->
                    PlacePhotoCard(spot = spot, index = i, isSelected = false) { onSelect(spot) }
                }
            }
        }
    }
}

/** La foto sola, a pantalla completa sobre negro. Tocar en cualquier sitio
 *  cierra: no hay nada más que hacer aquí que mirar. */
@Composable
private fun FotoAmpliada(foto: FotoDeLugar, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = foto.url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
            if (foto.autor != null) {
                Text(
                    "Foto de ${foto.autor}",
                    style = BuddyType.Caption1, color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                )
            }
        }
    }
}
