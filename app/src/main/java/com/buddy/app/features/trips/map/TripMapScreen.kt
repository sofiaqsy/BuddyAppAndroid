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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.launch
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
    private val tripRepo: com.buddy.app.features.trips.data.TripRepository,
) : ViewModel() {
    suspend fun spots(destinationId: String): List<ApiGuideSpot> =
        runCatching { mapApi.guideSpots(destinationId).spots }.getOrDefault(emptyList())

    /** null si falló o si el mapa está demasiado alejado: quien llama conserva
     *  los pins que ya tenía en vez de vaciar el mapa. */
    suspend fun spotsInBounds(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List<ApiGuideSpot>? =
        runCatching { mapApi.spotsInBounds(minLat, minLng, maxLat, maxLng) }
            .onFailure { android.util.Log.w("TripMap", "spotsInBounds falló", it) }
            .getOrNull()
            ?.takeUnless { it.tooWide }
            ?.spots
            ?.also { r -> android.util.Log.d("TripMap", "en pantalla: ${r.size} lugar(es) — ${r.take(5).joinToString { it.name }}") }

    suspend fun buddyCount(destinationId: String): Int? =
        runCatching { homeApi.placeContext(destinationId, "destination").buddies }.getOrNull()

    suspend fun fotos(spotId: String): List<FotoDeLugar> =
        runCatching { mapApi.spotGallery(spotId).fotos() }.getOrDefault(emptyList())

    suspend fun coordenadas(destinationId: String): Pair<Double, Double>? =
        runCatching { mapApi.destination(destinationId).let { it.lat to it.lng } }.getOrNull()

    /** La recomendación de un lugar: journey suelto (trip_id nulo) sobre el
     *  spot. Nace al tocar "Añadir foto", no al elegir el lugar. */
    suspend fun crearRecomendacion(spotId: String, lat: Double, lng: Double): ApiJourney? =
        runCatching { tripRepo.shareLugar(spotId = spotId, lat = lat, lng = lng) }
            .onFailure { android.util.Log.e("TripMap", "crearRecomendacion falló", it) }
            .getOrNull()

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
    /** Lugar ya elegido al entrar: se llega tocando ESE lugar (el carrusel del
     *  Home), no el destino. Se aplica UNA vez — si luego se cierra la ficha,
     *  el mapa se queda en la lista, que es lo que el gesto de cerrar pidió. */
    initialSpotId: String? = null,
    /** Buddy aprobado: puede documentar lugares. Lo sabe quien contiene la
     *  pantalla —ya lo consulta para el CTA de Tu trip— y no se vuelve a pedir. */
    canRecommend: Boolean = false,
    /** El editor de fotos, con el journey recién creado para este lugar. */
    onOpenBook: (ApiJourney, Int, Boolean) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    viewModel: TripMapViewModel = hiltViewModel(),
) {
    val destName = journey.destination?.name ?: journey.title ?: "Trip"
    val destId = journey.destination?.id ?: journey.destinationId

    // Las coordenadas del destino, pedidas solo si no vinieron. Llegando por un
    // NOMBRE (la ciudad de comunidad viva, el destino de una historia) viaja el
    // id y nada más, y sin ellas un destino sin lugares se anunciaba como "este
    // lugar aún no tiene mapa" — de una ciudad que sí existe.
    val coordsPedidas by produceState<Pair<Double, Double>?>(null, destId) {
        val faltan = journey.destination?.lat == null || journey.destination.lng == null
        value = if (faltan && destId != null) {
            withContext(Dispatchers.IO) { viewModel.coordenadas(destId) }
        } else null
    }
    val destLat = journey.destination?.lat ?: coordsPedidas?.first
    val destLng = journey.destination?.lng ?: coordsPedidas?.second

    val spotsDestino by produceState(emptyList<ApiGuideSpot>(), destId) {
        value = if (destId != null) withContext(Dispatchers.IO) { viewModel.spots(destId) } else emptyList()
    }
    // Los que caen en la parte VISIBLE del mapa, de cualquier destino. Los spots
    // cuelgan de UN destino, así que el mapa de Breña salía vacío aunque
    // Cafetería Rosal y El encanto están ahí (pertenecen a "Lima").
    var spotsEnPantalla by remember { mutableStateOf(emptyList<ApiGuideSpot>()) }
    val spots = remember(spotsDestino, spotsEnPantalla) {
        val ids = spotsDestino.mapTo(HashSet()) { it.id }
        spotsDestino + spotsEnPantalla.filter { it.id !in ids }
    }
    val buddyCount by produceState<Int?>(null, destId) {
        value = if (destId != null) withContext(Dispatchers.IO) { viewModel.buddyCount(destId) } else null
    }

    var selectedSpotId by remember { mutableStateOf<String?>(null) }
    var navigationTarget by remember { mutableStateOf<ApiGuideSpot?>(null) }
    var fotoAmpliada by remember { mutableStateOf<FotoDeLugar?>(null) }
    /** Lugar que se está compartiendo. La tarjeta se arma en el momento del
     *  toque y viaja entera: lo que se envía es lo que se ve ahora, no una
     *  referencia que el destinatario tendría que resolver. */
    var compartiendo by remember { mutableStateOf<com.buddy.app.core.data.model.ChatCard.Place?>(null) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    /** Buddy cuyo perfil se está mirando. A pantalla completa y por encima del
     *  mapa: es otra pantalla, no una capa más de esta. */
    var perfilDe by remember { mutableStateOf<ApiPlaceBuddy?>(null) }

    var yaAplicoInicial by remember { mutableStateOf(false) }
    LaunchedEffect(initialSpotId, spots) {
        if (yaAplicoInicial || initialSpotId == null) return@LaunchedEffect
        val elegido = spots.firstOrNull { it.id == initialSpotId } ?: return@LaunchedEffect
        yaAplicoInicial = true
        selectedSpotId = elegido.id
        // El mapa nace a la altura del destino y BAJA hasta el lugar. Aparecer
        // ya encima no contaba nada: quien llega desde el carrusel no sabe
        // dónde cae ese lugar dentro de la ciudad, y el propio acercamiento es
        // lo que se lo dice.
        //
        // Esperando a que la vista exista: el AndroidView se crea al recibir el
        // primer center, que llega con estos mismos datos, así que la primera
        // vuelta del efecto suele encontrarse mapRef todavía en nulo.
        var intentos = 0
        while (mapRef == null && intentos < 20) {
            kotlinx.coroutines.delay(50)
            intentos++
        }
        mapRef?.controller?.animateTo(GeoPoint(elegido.lat, elegido.lng), ZoomLugar, DuracionZoomMs)
    }

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

    val center = remember(destLat, destLng, spots, initialSpotId) {
        val elegido = initialSpotId?.let { id -> spots.firstOrNull { it.id == id } }
        when {
            // Se entró tocando un lugar: el mapa nace centrado ahí. El animateTo
            // de después solo corrige si la vista ya existía; sin esto el mapa
            // aparecía encuadrando la ciudad y luego saltaba.
            elegido != null -> GeoPoint(elegido.lat, elegido.lng)
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

    compartiendo?.let { tarjeta ->
        CompartirLugarSheet(card = tarjeta, onDismiss = { compartiendo = null })
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
                        controller.setZoom(if (spots.isEmpty()) 15.0 else ZoomDestino)
                        controller.setCenter(center)
                        mapRef = this
                        // Al terminar de mover o hacer zoom (400 ms sin gestos):
                        // pedir lo que hay en pantalla. Cancela la petición
                        // anterior para que solo la última posición cuente.
                        var boundsJob: kotlinx.coroutines.Job? = null
                        // Rectángulo ya pedido (el doble de lo visible). Zoom o
                        // arrastre dentro de él no vuelve a la red: antes cada
                        // gesto pedía otra vez el mismo sitio.
                        var pedido: DoubleArray? = null
                        val pedirEnPantalla = {
                            val bb = boundingBox
                            val ya = pedido
                            val dentro = ya != null &&
                                bb.latSouth >= ya[0] && bb.lonWest >= ya[1] &&
                                bb.latNorth <= ya[2] && bb.lonEast <= ya[3]
                            if (!dentro) {
                                boundsJob?.cancel()
                                boundsJob = scope.launch {
                                    val hLat = (bb.latNorth - bb.latSouth)
                                    val hLng = (bb.lonEast - bb.lonWest)
                                    val area = doubleArrayOf(
                                        bb.latSouth - hLat / 2, bb.lonWest - hLng / 2,
                                        bb.latNorth + hLat / 2, bb.lonEast + hLng / 2,
                                    )
                                    val r = withContext(Dispatchers.IO) {
                                        viewModel.spotsInBounds(area[0], area[1], area[2], area[3])
                                    }
                                    if (r != null) {
                                        // Con 150 o más pudo venir recortado: no se reutiliza.
                                        pedido = if (r.size < 150) area else null
                                        // Solo si cambió algo: reasignar la misma lista
                                        // redibujaba todos los marcadores.
                                        if (r.map { it.id } != spotsEnPantalla.map { it.id }) spotsEnPantalla = r
                                    }
                                }
                            }
                        }
                        addMapListener(
                            org.osmdroid.events.DelayedMapListener(
                                object : org.osmdroid.events.MapListener {
                                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean { pedirEnPantalla(); return false }
                                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean { pedirEnPantalla(); return false }
                                },
                                600,
                            ),
                        )
                        // Primera carga: el rectángulo solo existe tras el layout.
                        addOnFirstLayoutListener { _, _, _, _, _ -> pedirEnPantalla() }
                    }
                },
                update = { map ->
                    map.overlays.removeAll { it is Marker || it is org.osmdroid.views.overlay.MapEventsOverlay }
                    // Tocar el mapa vacío cierra la ficha (como iOS): con la
                    // ficha abierta, el mapa es lo que queda "detrás", y tocar
                    // el fondo es el gesto natural para volver.
                    map.overlays.add(
                        org.osmdroid.views.overlay.MapEventsOverlay(
                            object : org.osmdroid.events.MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                    if (selectedSpotId != null) { selectedSpotId = null; return true }
                                    return false
                                }
                                override fun longPressHelper(p: GeoPoint?) = false
                            },
                        ),
                    )
                    val markerSpots = spots.ifEmpty {
                        if (destLat != null && destLng != null) {
                            listOf(ApiGuideSpot(id = "dest", name = destName, lat = destLat, lng = destLng))
                        } else emptyList()
                    }
                    // El elegido se dibuja al FINAL: osmdroid pinta en orden, y
                    // siendo el más grande es el que no puede quedar debajo de
                    // otro.
                    markerSpots.sortedBy { it.id == selectedSpotId }.forEach { spot ->
                        val elegido = spot.id == selectedSpotId
                        map.overlays.add(
                            Marker(map).apply {
                                position = GeoPoint(spot.lat, spot.lng)
                                title = spot.name
                                icon = pinDeLugar(map.context, spot.name, elegido)
                                setAnchor(Marker.ANCHOR_CENTER, anclaVerticalDelPin(map.context, elegido))
                                // Sin el globo de osmdroid: el nombre ya va en
                                // el pin del elegido y la ficha de abajo cuenta
                                // el resto.
                                infoWindow = null
                                setOnMarkerClickListener { _, _ ->
                                    // El marcador abre la MISMA ficha que la
                                    // tarjeta: tocar el pin y tocar la tarjeta
                                    // son la misma pregunta sobre el mismo sitio.
                                    //
                                    // Sin mover la cámara, a diferencia del
                                    // rail: el pin que se acaba de tocar ya está
                                    // a la vista y bajo el dedo, y recentrarlo
                                    // lo movería justo donde el dedo lo suelta.
                                    selectedSpotId = spot.id
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
                        canRecommend = canRecommend,
                        onAddPhoto = {
                            // AQUÍ nace el journey, no al elegir el lugar: hasta
                            // este toque el usuario solo estaba mirando la ficha.
                            scope.launch {
                                val journey = withContext(Dispatchers.IO) {
                                    viewModel.crearRecomendacion(spot.id, spot.lat, spot.lng)
                                }
                                if (journey != null) onOpenBook(journey, -1, true)
                            }
                        },
                        onOpenBuddy = { perfilDe = it },
                        onNavigate = { navigationTarget = spot },
                        onShare = {
                            compartiendo = com.buddy.app.core.data.model.ChatCard.Place(
                                spotId = spot.id,
                                destinationId = destId,
                                name = spot.name,
                                category = spot.categoryName,
                                // La foto que se está viendo: es la que verá el
                                // destinatario en el chat.
                                photoUrl = fotos.firstOrNull()?.url ?: spot.coverUrl,
                                lat = spot.lat,
                                lng = spot.lng,
                                authorName = fotos.firstOrNull()?.autor,
                            )
                        },
                        onOpenPhoto = { fotoAmpliada = it },
                        onClose = { selectedSpotId = null },
                    )
                } else {
                    RailDeLugares(
                        spots = spots,
                        presenceText = presenceText,
                        onSelect = { elegido ->
                            selectedSpotId = elegido.id
                            mapRef?.controller?.animateTo(
                                GeoPoint(elegido.lat, elegido.lng), ZoomLugar, DuracionZoomMs,
                            )
                        },
                    )
                }
            }
        }

        // ── Perfil de un buddy ──────────────────────────────────────────────
        //
        // ENCIMA, no en lugar de.
        //
        // Antes esto salía con un `return` que sacaba el mapa de la
        // composición, y al volver el mapa nacía otra vez: se perdía el zoom,
        // la pestaña abierta y el lugar elegido. El estado de una pantalla no
        // puede depender de si alguien miró un perfil y volvió.
        //
        // Se dibuja como capa opaca a pantalla completa y se come los toques:
        // sin eso, tocar una zona vacía del perfil llegaría al mapa de detrás y
        // movería algo que no se está viendo.
        perfilDe?.let { buddy ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BuddyColor.Canvas)
                    .pointerInput(Unit) {},
            ) {
                // key por persona: con un solo ViewModel compartido, el segundo
                // perfil se encontraba el primero ya cargado y mostraba a la
                // persona equivocada.
                // Mi propio perfil se muestra como la pantalla del tab Yo, que no
            // trae flecha de volver: sin esto, el botón del sistema saldría de
            // la app en vez de cerrar la capa.
            androidx.activity.compose.BackHandler { perfilDe = null }
            com.buddy.app.features.profile.UserProfileScreen(
                    travelerId = buddy.travelerId!!,
                    previewName = buddy.fullName,
                    previewAvatarUrl = buddy.avatarUrl,
                    onBack = { perfilDe = null },
                    vm = androidx.hilt.navigation.compose.hiltViewModel(key = buddy.travelerId),
                )
            }
        }
    }
}

/** Alto fijo del panel — el mismo con rail o con ficha, para que elegir un
 *  lugar no reacomode el mapa bajo el dedo. */
private val PanelHeight = 258.dp

/**
 * ZOOM: LA CIUDAD Y LA CUADRA
 *
 * Dos alturas, y el viaje entre ellas es lo que se ve al elegir un lugar. Los
 * valores salen de los de iOS, que son geográficos (MKCoordinateSpan) y no
 * niveles de tesela: 0.012° para el conjunto y 0.004° —unos 450 m de alto de
 * pantalla— para el lugar elegido. Sobre este viewport eso equivale a ~13.5 y
 * ~18.5.
 *
 * Saltar de golpe deja al usuario preguntándose a dónde fue el mapa: la
 * transición ES la explicación de que se acercó a un punto concreto del mismo
 * sitio, así que va animada y no instantánea.
 */
private const val ZoomDestino = 13.5
private const val ZoomLugar = 18.5
private const val DuracionZoomMs = 600L

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
