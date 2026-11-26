package com.buddy.app.features.trips.map

import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    /** Fotos del lugar: primero las del dueño (portada primero, como iOS Fotos),
     *  después las de los visitantes, sin repetir. */
    suspend fun fotos(spotId: String): List<FotoDeLugar> {
        val owner = runCatching { mapApi.ownerPhotos(spotId.lowercase()).photos }.getOrDefault(emptyList())
            .sortedBy { it.position }.map { FotoDeLugar(it.url, null) }
        val gallery = runCatching { mapApi.spotGallery(spotId).fotos() }.getOrDefault(emptyList())
        return (owner + gallery).distinctBy { it.url }
    }

    suspend fun coordenadas(destinationId: String): Pair<Double, Double>? =
        runCatching { mapApi.destination(destinationId).let { it.lat to it.lng } }.getOrNull()

    /** Sube UNA foto de la recomendación (hasta 3 intentos); el servidor guarda por client_page_id. */
    suspend fun subirFotoRecomendacion(journeyId: String, jpeg: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        val parts = listOf(
            okhttp3.MultipartBody.Part.createFormData("page_0", "page_0.jpg",
                okhttp3.RequestBody.create("image/jpeg".toMediaType(), jpeg)),
            okhttp3.MultipartBody.Part.createFormData("client_page_id_0", id),
        )
        repeat(3) { intento ->
            if (runCatching { homeApi.uploadJourneyPages(journeyId, parts) }.isSuccess) return@withContext true
            kotlinx.coroutines.delay(2000L * (intento + 1))
        }
        false
    }

    suspend fun publicarRecomendacion(journeyId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { homeApi.publishJourney(journeyId, com.buddy.app.features.home.data.PublishBody(status = "completed", isPublic = true)) }.isSuccess
    }

    /** La recomendación de un lugar: journey suelto (trip_id nulo) sobre el
     *  spot. Nace al tocar "Añadir foto", no al elegir el lugar. */
    suspend fun crearRecomendacion(spotId: String, lat: Double, lng: Double): ApiJourney? =
        runCatching { tripRepo.shareLugar(spotId = spotId, lat = lat, lng = lng) }
            .onFailure { android.util.Log.e("TripMap", "crearRecomendacion falló", it) }
            .getOrNull()

    /** Productos del lugar (vacío si no tiene negocio o si falla). */
    suspend fun products(spotId: String): List<com.buddy.app.core.data.model.FincasProduct> =
        runCatching { homeApi.spotProducts(spotId.lowercase()).items }.getOrDefault(emptyList())

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
    /** Abierto desde "Recomendar un lugar": Fotos primero y "Añadir foto" va directo al selector. */
    recommendMode: Boolean = false,
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
    // Punto azul de "tu ubicación" (iOS, 24 Nov 2026).
    var myLoc by remember { mutableStateOf<org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay?>(null) }
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
    /** Sube al cambiar: el dueño añadió una foto y la galería se vuelve a pedir. */
    var fotosToken by remember { mutableStateOf(0) }
    var isLoadingFotos by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSpotId, fotosToken) {
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

    var products by remember { mutableStateOf<List<com.buddy.app.core.data.model.FincasProduct>>(emptyList()) }
    var isLoadingProducts by remember { mutableStateOf(false) }
    LaunchedEffect(selectedSpotId) {
        val id = selectedSpotId ?: return@LaunchedEffect
        products = emptyList()
        isLoadingProducts = true
        val cargados = withContext(Dispatchers.IO) { viewModel.products(id) }
        if (selectedSpotId == id) { products = cargados; isLoadingProducts = false }
    }
    // "Preguntar al negocio": chat a pantalla completa encima del mapa.
    var chatTarget by remember { mutableStateOf<com.buddy.app.features.businesschat.BusinessChatTarget?>(null) }
    // Foto de producto abierta en el visor (todas las del lugar, en orden).
    var productoAmpliado by remember { mutableStateOf<String?>(null) }
    val ownerSpots by com.buddy.app.features.business.OwnerSpots.map.collectAsState(initial = emptyMap())
    var editingProduct by remember { mutableStateOf<Pair<String, com.buddy.app.core.data.model.FincasProduct?>?>(null) }
    val ctxOwner = androidx.compose.ui.platform.LocalContext.current
    val businessApi = remember { dagger.hilt.android.EntryPointAccessors.fromApplication(ctxOwner.applicationContext,
        com.buddy.app.features.business.BusinessEntryPoint::class.java).businessApi() }
    var ownerUpload by remember { mutableStateOf<Pair<String, String>?>(null) }   // spotId, farmId
    /** Fotos del dueño del lugar abierto (id, url, posición): para "Quitar foto" y "Usar como portada". */
    var ownerPhotos by remember { mutableStateOf<List<com.buddy.app.features.business.OwnerPhoto>>(emptyList()) }
    LaunchedEffect(selectedSpotId, fotosToken, ownerSpots) {
        val id = selectedSpotId
        ownerPhotos = if (id != null && ownerSpots.containsKey(id.lowercase()))
            runCatching { businessApi.ownerPhotos(id).photos.sortedBy { it.position } }.getOrDefault(emptyList()) else emptyList()
    }
    // Recomendar sin editor (Keyla, 25 Nov 2026): elegir fotos crea la
    // recomendación y las sube una por una, con reintentos, mostrando el avance.
    var subida by remember { mutableStateOf<String?>(null) }
    var subidaSpot by remember { mutableStateOf<ApiGuideSpot?>(null) }
    val recommendPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        val spot = subidaSpot
        if (uris.isNotEmpty() && spot != null) scope.launch {
            subida = "Subiendo tu foto…"
            val journey = withContext(Dispatchers.IO) { viewModel.crearRecomendacion(spot.id, spot.lat, spot.lng) }
            if (journey == null) { subida = "No se pudo subir. Revisa tu señal."; return@launch }
            var ok = 0
            uris.forEachIndexed { i, uri ->
                subida = if (uris.size > 1) "Subiendo foto ${i + 1} de ${uris.size}…" else "Subiendo tu foto…"
                val jpeg = runCatching { withContext(Dispatchers.IO) { com.buddy.app.features.business.Jpeg.from(ctxOwner, uri) } }.getOrNull()
                if (jpeg != null && viewModel.subirFotoRecomendacion(journey.id, jpeg)) ok++
            }
            if (ok > 0 && viewModel.publicarRecomendacion(journey.id)) {
                fotosToken++
                subida = "Listo, tu publicación ya está arriba"
                kotlinx.coroutines.delay(3000); subida = null
            } else subida = "No se pudo subir la foto. Revisa tu señal."
        }
    }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = ownerUpload
        if (uri != null && target != null) scope.launch {
            runCatching {
                val jpeg = withContext(Dispatchers.IO) { com.buddy.app.features.business.Jpeg.from(ctxOwner, uri) }
                val body = okhttp3.RequestBody.create("image/jpeg".toMediaType(), jpeg)
                businessApi.uploadOwnerPhoto(target.first,
                    okhttp3.MultipartBody.Part.createFormData("photo", "foto.jpg", body),
                    okhttp3.RequestBody.create(null, target.second))
            }.onSuccess { fotosToken++ }
             .onFailure { android.widget.Toast.makeText(ctxOwner, "No pudimos subir la foto. Intenta de nuevo.", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    /** Producto que la ficha muestra primero: el visor sigue el mismo orden. */
    var productoPrimero by remember { mutableStateOf<String?>(null) }

    var buddies by remember { mutableStateOf<List<ApiPlaceBuddy>>(emptyList()) }
    var isLoadingBuddies by remember { mutableStateOf(true) }
    LaunchedEffect(destId) {
        if (destId == null) { isLoadingBuddies = false; return@LaunchedEffect }
        buddies = withContext(Dispatchers.IO) { viewModel.buddies(destId) }
        isLoadingBuddies = false
    }

    // Presencia humana — lo único que el mapa no puede mostrar solo (iOS)
    val presenceText = when {
        !com.buddy.app.core.Features.SHOWS_BUDDY_TOOLS -> null
        buddyCount == null -> null
        buddyCount!! <= 0 -> "Un buddy puede ayudarte si tienes una duda"
        buddyCount == 1 -> "1 buddy aquí, listo si tienes una duda"
        else -> "$buddyCount buddies aquí, listos si tienes una duda"
    }

    // El panel reserva además la barra de gestos: sin eso la fila de fotos
    // —lo último que se dibuja— quedaba cortada por el borde de la pantalla.
    val insetInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Panel que se jala (iOS, 23 Nov 2026). Cerrado: la ficha mide PanelHeight y
    // la lista lo justo para dos tarjetas; abierto: casi toda la pantalla.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val sheet = remember { PanelSheet(scope, with(density) { 30.dp.toPx() }) }
    val collapsedDp = (if (selectedSpot != null) PanelHeight else ListPanelHeight) + insetInferior
    val expandedDp = screenH + insetInferior - topInset - 24.dp
    LaunchedEffect(collapsedDp, expandedDp) {
        with(density) { sheet.setBounds(collapsedDp.toPx(), expandedDp.toPx(), (PeekHeight + insetInferior).toPx()) }
    }
    // Cambiar de lugar (o volver a la lista) cierra el panel abierto.
    LaunchedEffect(selectedSpotId) { if (sheet.expanded) sheet.expand(false) }
    val panelGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val placesGrid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    androidx.activity.compose.BackHandler(enabled = sheet.expanded) { sheet.expand(false) }

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

    // El mismo visor de toda la app (regla 26): de lado, zoom, abajo cierra.
    fotoAmpliada?.let { foto ->
        val urls = fotos.map { it.url }.ifEmpty { listOf(foto.url) }
        com.buddy.app.core.designsystem.components.PhotoViewerDialog(
            pages = com.buddy.app.core.designsystem.components.ViewerPages.make(listOf(urls)),
            startPage = urls.indexOf(foto.url).coerceAtLeast(0),
            onDismiss = { fotoAmpliada = null },
            sourceKey = { it.url },
        )
    }

    // Como iOS: cada presentación con TODAS sus fotos, una tras otra; arriba el
    // nombre y "S/ 20 · 250 g · n de m". Abre en la foto que se tocó.
    productoAmpliado?.let { url ->
        val entries = products.flatMap { p -> p.entries().map { p to it } }.let { all ->
            // Mismo orden que el panel: el producto tocado en Inicio va primero.
            val i = all.indexOfFirst { (p, pr) -> "${p.id}-${pr?.id ?: ""}" == productoPrimero }
            if (i <= 0) all else listOf(all[i]) + all.filterIndexed { j, _ -> j != i }
        }
        // Una foto por tarjeta, la misma del panel (Keyla, 24 Nov 2026).
        val groups = entries.map { (p, pr) -> listOfNotNull(p.coverFor(pr)) }
        val pages = com.buddy.app.core.designsystem.components.ViewerPages.make(groups).filter { it.url != null }
        com.buddy.app.core.designsystem.components.PhotoViewerDialog(
            pages = pages,
            startPage = pages.indexOfFirst { it.url == url }.coerceAtLeast(0),
            onDismiss = { productoAmpliado = null },
            headerFor = { g ->
                val (p, pr) = entries[g]
                val price = pr?.price?.let { com.buddy.app.core.data.model.soles(it) }
                com.buddy.app.core.designsystem.components.ViewerHeader(
                    title = p.name,
                    subtitle = listOfNotNull(price, pr?.label?.takeIf { it.isNotEmpty() }).joinToString(" · ").ifEmpty { null },
                    plain = true,
                )
            },
            // La tarjeta del producto, no la URL: al cerrar en cualquiera de sus
            // fotos, vuelve a su tarjeta en la fila (como iOS).
            sourceKey = { pg -> entries.getOrNull(pg.group)?.let { (p, pr) -> productSourceKey(p, pr) } },
        )
    }

    compartiendo?.let { tarjeta ->
        CompartirLugarSheet(card = tarjeta, onDismiss = { compartiendo = null })
    }

    DisposableEffect(Unit) { onDispose { myLoc?.disableMyLocation(); mapRef?.onDetach() } }

    Box(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        if (center != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    Configuration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        // Tonos suaves y cálidos, más cerca del mapa de iOS (Keyla, 24 Nov
                        // 2026): baja la saturación del mapa estándar y lo aclara un poco.
                        overlayManager.tilesOverlay.setColorFilter(android.graphics.ColorMatrixColorFilter(
                            android.graphics.ColorMatrix().apply {
                                setSaturation(0.45f)
                                postConcat(android.graphics.ColorMatrix(floatArrayOf(
                                    0.92f, 0f, 0f, 0f, 22f,
                                    0f, 0.92f, 0f, 0f, 20f,
                                    0f, 0f, 0.90f, 0f, 12f,
                                    0f, 0f, 0f, 1f, 0f)))
                            }))
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        controller.setZoom(if (spots.isEmpty()) 15.0 else ZoomDestino)
                        controller.setCenter(center)
                        mapRef = this
                        // Punto azul con aro blanco y halo, como el de iPhone.
                        val provider = org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(ctx).apply {
                            addLocationSource(android.location.LocationManager.NETWORK_PROVIDER)
                        }
                        val dot = puntoAzul(ctx.resources.displayMetrics.density)
                        val overlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(provider, this).apply {
                            setPersonIcon(dot); setDirectionIcon(dot)
                            setPersonAnchor(0.5f, 0.5f); setDirectionAnchor(0.5f, 0.5f)
                            isDrawAccuracyEnabled = false
                            enableMyLocation()
                        }
                        overlays.add(overlay)
                        myLoc = overlay
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
                                icon = pinDeLugar(map.context, spot.name, elegido, categoria = spot.categoryName)
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
                    // El punto azul va encima de los pines (Keyla, 24 Nov 2026).
                    myLoc?.let { map.overlays.remove(it); map.overlays.add(it) }
                    map.invalidate()
                },
            )
            // Botón "mi ubicación", siempre por encima del panel (iOS).
            if (!sheet.expanded) com.buddy.app.core.designsystem.components.MapControlButton(
                icon = androidx.compose.material.icons.Icons.Filled.Navigation,
                label = "Ir a mi ubicación",
                iconRotation = 45f,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = with(density) { sheet.height.value.toDp() } + 28.dp),
            ) { myLoc?.myLocation?.let { mapRef?.controller?.animateTo(it, ZoomLugar, DuracionZoomMs) } }
            // Aviso de subida de fotos (iOS UploadStatusBanner).
            subida?.let { msg ->
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                        .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(BuddyColor.Surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (msg.startsWith("Subiendo")) androidx.compose.material3.CircularProgressIndicator(
                        Modifier.size(18.dp), strokeWidth = 2.dp, color = BuddyColor.Brand)
                    Column(Modifier.weight(1f)) {
                        Text(msg, style = BuddyType.Headline, color = BuddyColor.Ink)
                        if (msg.startsWith("Subiendo")) Text("Con señal lenta puede tardar un poco.",
                            style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                    }
                    if (msg.startsWith("No se pudo")) Text("Cerrar", style = BuddyType.FootnoteBold, color = BuddyColor.Brand,
                        modifier = Modifier.clickable { subida = null })
                }
            }
            // Con el panel abierto no hay mapa a la vista: el crédito se oculta.
            if (!sheet.expanded) Text(
                "© OpenStreetMap",
                fontSize = 10.sp, color = BuddyColor.InkMuted,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = with(density) { sheet.height.value.toDp() } + 4.dp)
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
            // Sin el nombre del destino arriba (Keyla, 23 Nov 2026: innecesario),
            // igual que iOS: solo el botón de volver.
            Spacer(Modifier.weight(1f))
            // Compartir vive en el menú ⋯ de arriba, como iOS, no en la ficha.
            selectedSpot?.let { spot ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Box(
                        Modifier.size(40.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White)
                            .clickable { menu = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.MoreHoriz, contentDescription = "Más opciones", tint = BuddyColor.Ink) }
                    androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Compartir lugar") },
                            leadingIcon = { Icon(Icons.Filled.Share, null) },
                            onClick = {
                                menu = false
                                compartiendo = com.buddy.app.core.data.model.ChatCard.Place(
                                    spotId = spot.id,
                                    destinationId = destId,
                                    name = spot.name,
                                    category = spot.categoryName,
                                    photoUrl = fotos.firstOrNull()?.url ?: spot.coverUrl,
                                    lat = spot.lat,
                                    lng = spot.lng,
                                    authorName = fotos.firstOrNull()?.autor,
                                )
                            },
                        )
                    }
                }
            }
        }

        // ── Panel inferior: rail de lugares o ficha del elegido ──────────────
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { sheet.height.value.toDp() })
                .shadow(14.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .padding(bottom = insetInferior)
                // Todo el panel se jala hacia arriba o abajo; las listas de
                // adentro le pasan el gesto cuando ya están arriba del todo.
                .nestedScroll(sheet.nestedScroll)
                .draggable(
                    state = androidx.compose.foundation.gestures.rememberDraggableState { d -> sheet.dragBy(d) },
                    orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                    onDragStarted = { sheet.start() },
                    onDragStopped = { v -> sheet.settle(v) },
                )
                // Deslizar a la derecha sobre el panel vuelve atrás, como iOS
                // (Keyla, 21 nov 2026). Las filas de fotos que se desplazan de
                // lado consumen su propio arrastre, así que no vuelven.
                .pointerInput(Unit) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onHorizontalDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, a: Float -> dx += a },
                        onDragEnd = { if (dx > 90.dp.toPx()) onBack() },
                    )
                },
        ) {
            Column(Modifier.fillMaxSize()) {
            // Barrita: arriba abre todo, abajo vuelve; tocarla alterna.
            Box(
                Modifier.fillMaxWidth().height(24.dp).clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                ) { sheet.toggle() }.semantics { contentDescription = if (sheet.expanded) "Mostrar el mapa" else "Ver todo" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(BuddyColor.Border)) }
            AnimatedContent(
                targetState = selectedSpot,
                // Mismo lugar = misma ficha, aunque llegue una copia nueva del lugar.
                contentKey = { it?.id },
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "panel",
            ) { spot ->
                if (spot != null) {
                    // ¿Es un lugar del negocio activo? Entonces los productos llevan lápiz.
                    val ownerFarm = ownerSpots[spot.id.lowercase()]
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalProductEdit provides ownerFarm?.let { farmId -> { prod: com.buddy.app.core.data.model.FincasProduct -> editingProduct = Pair(farmId, prod) } },
                        LocalOwnerActions provides ownerFarm?.let { farmId ->
                            com.buddy.app.features.business.OwnerActions(
                                onAddProduct = { editingProduct = Pair(farmId, null) },
                                ownerPhotoIds = ownerPhotos.associate { it.url to it.id },
                                coverId = ownerPhotos.firstOrNull()?.id,
                                onRemovePhoto = { photoId ->
                                    scope.launch {
                                        runCatching { businessApi.deleteOwnerPhoto(spot.id, photoId, farmId) }
                                            .onSuccess { fotosToken++ }
                                            .onFailure { android.widget.Toast.makeText(ctxOwner, "No pudimos quitar la foto.", android.widget.Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                onMakeCover = { photoId ->
                                    scope.launch {
                                        runCatching { businessApi.setOwnerCover(spot.id, photoId, com.buddy.app.features.business.ClaimBody(farmId)) }
                                            .onSuccess { fotosToken++ }
                                            .onFailure { android.widget.Toast.makeText(ctxOwner, "No pudimos cambiar la portada.", android.widget.Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                onAddPhoto = {
                                    ownerUpload = spot.id to farmId
                                    photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                        },
                    ) {
                    PlaceGuideDetail(
                        onExpandPanel = { if (!sheet.expanded) sheet.expand(true) },
                        spot = spot,
                        presenceText = presenceText,
                        fotos = fotos,
                        isLoadingFotos = isLoadingFotos,
                        canRecommend = canRecommend,
                        products = products,
                        isLoadingProducts = isLoadingProducts,
                        expanded = sheet.expanded,
                        onOpenProductPhoto = { productoAmpliado = it },
                        onFocus = { productoPrimero = it },
                        onAsk = { p, pr ->
                            val card = if (p == null) com.buddy.app.features.businesschat.BizCard(
                                kind = "place", title = spot.name, subtitle = spot.categoryName, spotId = spot.id.lowercase())
                            else com.buddy.app.features.businesschat.BizCard(
                                kind = "product", title = p.name, subtitle = pr?.label?.takeIf { it.isNotEmpty() },
                                price = pr?.price?.let { String.format(java.util.Locale.US, "S/ %.2f", it) },
                                imageUrl = p.coverFor(pr), productId = p.id)
                            chatTarget = com.buddy.app.features.businesschat.BusinessChatTarget(
                                title = spot.name, spotId = spot.id.lowercase(), card = card)
                        },
                        gridState = panelGrid,
                        onAddPhoto = {
                            // Directo al selector de fotos, sin editor (iOS, 25 Nov 2026).
                            // El journey nace al elegir las fotos, no al abrir la ficha.
                            subidaSpot = spot
                            recommendPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        startOnPhotos = recommendMode,
                        onNavigate = { navigationTarget = spot },
                        onOpenPhoto = { fotoAmpliada = it },
                    )
                    }
                } else {
                    RailDeLugares(
                        spots = spots,
                        presenceText = presenceText,
                        expanded = sheet.expanded,
                        gridState = placesGrid,
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
        }

        editingProduct?.let { (farm, prod) ->
            com.buddy.app.features.business.ProductEditorScreen(farm, prod, Modifier.navigationBarsPadding()) { changed ->
                editingProduct = null
                if (changed) selectedSpot?.let { sp ->
                    scope.launch { products = withContext(Dispatchers.IO) { viewModel.products(sp.id) } }
                }
            }
        }
        chatTarget?.let { t ->
            com.buddy.app.features.businesschat.BusinessChatScreen(t, onClose = { chatTarget = null },
                vm = androidx.hilt.navigation.compose.hiltViewModel(key = "biz-${t.spotId}-${t.card?.productId}"))
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
private val PanelHeight = 300.dp

/** Cerrada, la lista mide lo justo para su título y dos tarjetas. */
private val ListPanelHeight = 300.dp
/** Alto mínimo (iOS, 24 Nov 2026): la barrita y el título con "Cómo llegar". */
private val PeekHeight = 84.dp

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

/**
 * "Lugares en esta zona" (iOS, 23 Nov 2026): cerrada, solo DOS tarjetas fijas
 * que llenan el ancho (sin fila que se mueva de lado); abierta, todos los
 * lugares en una cuadrícula de 2 con la misma tarjeta.
 */
@Composable
private fun RailDeLugares(
    spots: List<ApiGuideSpot>,
    presenceText: String?,
    expanded: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onSelect: (ApiGuideSpot) -> Unit,
) {
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    val cardW = (screenW - 40.dp - 10.dp) / 2
    Column(Modifier.fillMaxSize()) {
        if (presenceText != null) {
            Row(
                Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(BuddyColor.Accent))
                Text(presenceText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
        }
        if (spots.isEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Sin explorar aún", style = BuddyType.Callout, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink)
                Text("Nadie ha agregado recomendaciones aquí todavía. La guía de este lugar la construye su comunidad.",
                     style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
            return@Column
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)) {
            Text("Lugares en esta zona", style = BuddyType.Title3, color = BuddyColor.Ink)
            Text("Toca uno para ver fotos e información", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        }
        if (expanded) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(spots.size, key = { spots[it].id }) { i ->
                    PlacePhotoCard(spot = spots[i], index = i, isSelected = false, onClick = { onSelect(spots[i]) }, width = cardW)
                }
            }
        } else {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                spots.take(2).forEachIndexed { i, spot ->
                    PlacePhotoCard(spot = spot, index = i, isSelected = false, onClick = { onSelect(spot) }, width = cardW)
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

/** Punto de "tu ubicación": halo azul claro, aro blanco y centro azul (iOS). */
private fun puntoAzul(d: Float): android.graphics.Bitmap {
    val size = (44 * d).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = 0x2E007AFF; c.drawCircle(r, r, r, p)
    p.color = 0x40000000; c.drawCircle(r, r + d, 11 * d, p)
    p.color = android.graphics.Color.WHITE; c.drawCircle(r, r, 11 * d, p)
    p.color = 0xFF007AFF.toInt(); c.drawCircle(r, r, 8 * d, p)
    return bmp
}
