package com.buddy.app.features.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.navigation.ComoLlegarDialog
import com.buddy.app.features.home.data.HomeApi
import com.buddy.app.features.matching.data.ApiPlace
import com.buddy.app.features.messages.data.ChatRepository
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

/** Datos del mapa: lugares del destino + presencia de buddies (placeContext). */
@HiltViewModel
class TripMapViewModel @Inject constructor(
    private val chatRepo: ChatRepository,
    private val homeApi: HomeApi,
) : ViewModel() {
    suspend fun places(destinationId: String): List<ApiPlace> =
        runCatching { chatRepo.places(destinationId) }.getOrDefault(emptyList())

    suspend fun buddyCount(destinationId: String): Int? =
        runCatching { homeApi.placeContext(destinationId, "destination").buddies }.getOrNull()
}

/**
 * Mapa del trip — espejo de TripDetailView (iOS) con OpenStreetMap (osmdroid):
 * sin API keys. Markers de los lugares del destino, rail inferior de cards
 * (tap → centra el mapa, "Cómo llegar" por lugar) y píldora de presencia.
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

    val places by produceState(emptyList<ApiPlace>(), destId) {
        value = if (destId != null) {
            withContext(Dispatchers.IO) { viewModel.places(destId) }
        } else emptyList()
    }
    val buddyCount by produceState<Int?>(null, destId) {
        value = if (destId != null) {
            withContext(Dispatchers.IO) { viewModel.buddyCount(destId) }
        } else null
    }

    var selectedPlaceId by remember { mutableStateOf<String?>(null) }
    var navigationTarget by remember { mutableStateOf<ApiPlace?>(null) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    // Presencia humana — lo único que el mapa no puede mostrar solo (iOS)
    val presenceText = when {
        buddyCount == null -> null
        buddyCount!! <= 0 -> "Un buddy puede ayudarte si tienes una duda"
        buddyCount == 1 -> "1 buddy aquí, listo si tienes una duda"
        else -> "$buddyCount buddies aquí, listos si tienes una duda"
    }

    val center = remember(destLat, destLng, places) {
        when {
            places.isNotEmpty() -> GeoPoint(
                places.map { it.lat }.average(),
                places.map { it.lng }.average(),
            )
            destLat != null && destLng != null -> GeoPoint(destLat, destLng)
            else -> null
        }
    }

    navigationTarget?.let { place ->
        ComoLlegarDialog(
            placeName = place.name,
            lat = place.lat,
            lng = place.lng,
            onDismiss = { navigationTarget = null },
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapRef?.onDetach() }
    }

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
                        controller.setZoom(if (places.isEmpty()) 15.0 else 13.5)
                        controller.setCenter(center)
                        mapRef = this
                    }
                },
                update = { map ->
                    // Redibujar markers en cada cambio de lugares/selección
                    map.overlays.removeAll { it is Marker }
                    val markerPlaces = places.ifEmpty {
                        if (destLat != null && destLng != null) {
                            listOf(ApiPlace(id = "dest", name = destName, lat = destLat, lng = destLng))
                        } else emptyList()
                    }
                    markerPlaces.forEach { place ->
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(place.lat, place.lng)
                                title = place.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                setOnMarkerClickListener { m, mv ->
                                    selectedPlaceId = place.id
                                    mv.controller.animateTo(m.position, 15.5, 400L)
                                    m.showInfoWindow()
                                    true
                                }
                            },
                        )
                    }
                    map.invalidate()
                },
            )
            // Atribución OSM (requerida por la licencia de los tiles)
            Text(
                "© OpenStreetMap",
                fontSize = 10.sp, color = BuddyColor.InkMuted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
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

        // ── Chrome superior: back + nombre + presencia ───────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (presenceText != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .shadow(4.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.People, contentDescription = null,
                        Modifier.size(14.dp), tint = BuddyColor.Accent,
                    )
                    Text(presenceText, style = BuddyType.Caption1, color = BuddyColor.Ink)
                }
            }
        }

        // ── Rail inferior de lugares (espejo del rail de TripDetailView) ────
        if (places.isNotEmpty()) {
            LazyRow(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentPadding = PaddingValues(horizontal = Spacing.edge),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(places, key = { it.id }) { place ->
                    val isSelected = selectedPlaceId == place.id
                    Row(
                        Modifier
                            .shadow(6.dp, RoundedCornerShape(Radius.md))
                            .clip(RoundedCornerShape(Radius.md))
                            .background(Color.White)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, BuddyColor.Brand, RoundedCornerShape(Radius.md))
                                } else Modifier,
                            )
                            .clickable {
                                selectedPlaceId = place.id
                                mapRef?.controller?.animateTo(GeoPoint(place.lat, place.lng), 15.5, 400L)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocationOn, contentDescription = null,
                            Modifier.size(16.dp),
                            tint = if (isSelected) BuddyColor.Brand else BuddyColor.InkMuted,
                        )
                        Column {
                            Text(place.name, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1)
                            place.placeType?.let {
                                Text(it, style = BuddyType.Caption2, color = BuddyColor.InkMuted, maxLines = 1)
                            }
                        }
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(BuddyColor.GroupedBg)
                                .clickable { navigationTarget = place },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = "Cómo llegar a ${place.name}",
                                Modifier.size(14.dp), tint = BuddyColor.Brand,
                            )
                        }
                    }
                }
            }
        }
    }
}
