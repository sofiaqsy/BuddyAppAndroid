package com.buddy.app.features.trips.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Place
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyTextField
import com.buddy.app.features.home.data.ApiNearbySpot
import com.buddy.app.features.home.data.ApiSpotCategoryRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.CustomZoomButtonsController

/**
 * "Buscar en el mapa" — estilo inDrive: el pin queda FIJO en el centro con una
 * línea fina debajo cuya punta marca el punto; el buddy mueve el mapa por
 * debajo y, al soltar, se carga la dirección de ese punto.
 *
 * Dos caminos sobre el mismo mapa, y los dos terminan en lo que ya existía:
 *  - tocar un lugar que YA está en el mapa → "Elegir este lugar", igual que
 *    elegirlo de la lista de cercanos (lleva a su ficha, no crea nada);
 *  - dejar el pin sobre un punto libre → se piden los mismos datos que en
 *    "Registrar nuevo lugar" (nombre y tipo). El lugar se propone con las
 *    coordenadas del CENTRO, no con las del GPS.
 *
 * Los lugares existentes se piden por rectángulo visible (places/in-bounds),
 * el mismo endpoint del mapa del destino: al mover o hacer zoom aparece lo
 * que hay en pantalla.
 */
@Composable
fun ElegirLugarEnMapa(
    initialLat: Double?,
    initialLng: Double?,
    categories: List<ApiSpotCategoryRef>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onPickSpot: (ApiNearbySpot) -> Unit,
    onProposeAt: (nombre: String, categoriaId: String?, lat: Double, lng: Double) -> Unit,
    viewModel: TripMapViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    // Todos los tiempos de los logs son "ms desde que se abrió el mapa".
    val t0 = remember { android.os.SystemClock.elapsedRealtime() }
    fun ms() = android.os.SystemClock.elapsedRealtime() - t0
    var soltadoEn by remember { mutableStateOf(0L) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    // Búsqueda por texto: mueve el mapa, no elige nada por sí sola.
    var consulta by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<android.location.Address>>(emptyList()) }
    val ctxBusqueda = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(consulta) {
        val q = consulta.trim()
        if (q.length < 2) {
            if (resultados.isNotEmpty()) android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda vacía/corta — limpio resultados")
            resultados = emptyList(); return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        val inicio = android.os.SystemClock.elapsedRealtime()
        val c = mapRef?.mapCenter
        android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda \"$q\" iniciada — cerca de ${c?.let { "(${it.latitude},${it.longitude})" } ?: "ninguna parte"}")
        val r = withContext(Dispatchers.IO) { buscarLugar(ctxBusqueda, q, c?.latitude, c?.longitude) }
        resultados = r
        android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda \"$q\" → ${r.size} resultado(s) en ${android.os.SystemClock.elapsedRealtime() - inicio}ms")
        r.forEachIndexed { i, a ->
            val km = c?.let { org.osmdroid.util.GeoPoint(it.latitude, it.longitude).distanceToAsDouble(GeoPoint(a.latitude, a.longitude)) / 1000 }
            android.util.Log.d(TAG, "  ${i + 1}. \"${a.featureName ?: a.thoroughfare ?: a.locality}\" — ${a.getAddressLine(0)} (${a.latitude},${a.longitude}) a ${km?.let { "%.1f".format(it) } ?: "?"} km del centro")
        }
    }
    var spots by remember { mutableStateOf<List<ApiGuideSpot>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var centro by remember {
        mutableStateOf(if (initialLat != null && initialLng != null) GeoPoint(initialLat, initialLng) else null)
    }
    var moviendo by remember { mutableStateOf(false) }
    var direccion by remember { mutableStateOf<String?>(null) }
    var nombre by remember { mutableStateOf("") }
    var categoriaId by remember { mutableStateOf<String?>(null) }
    var tooWide by remember { mutableStateOf(false) }

    val elegido = spots.firstOrNull { it.id == selectedId }
    val insetInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val insetSuperior = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        android.util.Log.d(TAG, "[t+${ms()}ms] abierto — ubicación=${if (initialLat != null) "($initialLat,$initialLng)" else "AÚN NO"} categorias=${categories.size}")
    }
    // La ubicación pudo llegar DESPUÉS de abrir (la hoja la pide al abrirse):
    // si el mapa nació sin centro, se mueve ahí en cuanto llega.
    androidx.compose.runtime.LaunchedEffect(initialLat, initialLng) {
        if (initialLat == null || initialLng == null || centro != null) return@LaunchedEffect
        android.util.Log.d(TAG, "[t+${ms()}ms] ubicación llegó tarde → centrando en ($initialLat,$initialLng)")
        mapRef?.controller?.apply { setZoom(17.0); setCenter(GeoPoint(initialLat, initialLng)) }
        centro = GeoPoint(initialLat, initialLng)
    }
    DisposableEffect(Unit) { onDispose { android.util.Log.d(TAG, "cerrado"); mapRef?.onDetach() } }

    // Dirección del centro: se pide cuando el mapa se detiene, no en cada frame.
    val contexto = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(centro) {
        val c = centro ?: return@LaunchedEffect
        direccion = null
        val inicio = android.os.SystemClock.elapsedRealtime()
        val texto = withContext(Dispatchers.IO) { direccionDe(contexto, c.latitude, c.longitude) }
        direccion = texto ?: "Sin dirección"
        val ahora = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "[t+${ms()}ms] dirección=${texto ?: "null"} — geocoder ${ahora - inicio}ms" +
            (if (soltadoEn > 0) ", ${ahora - soltadoEn}ms desde que soltaste el mapa" else ""))
    }
    val elevacion by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (moviendo) 10.dp else 0.dp, label = "elevacionPin",
    )

    Box(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().userAgentValue = ctx.packageName
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    if (initialLat != null && initialLng != null) {
                        controller.setZoom(17.0)
                        controller.setCenter(GeoPoint(initialLat, initialLng))
                    } else {
                        controller.setZoom(3.0)
                        controller.setCenter(GeoPoint(20.0, 0.0))
                    }
                    mapRef = this

                    // Mismo patrón que el mapa del destino: tras 600 ms sin
                    // gestos se pide lo que hay en pantalla (con margen del
                    // doble) y solo se vuelve a pedir al salir de ese margen.
                    var boundsJob: Job? = null
                    var pedido: DoubleArray? = null
                    val pedirEnPantalla = {
                        val bb = boundingBox
                        val ya = pedido
                        val dentro = ya != null &&
                            bb.latSouth >= ya[0] && bb.lonWest >= ya[1] &&
                            bb.latNorth <= ya[2] && bb.lonEast <= ya[3]
                        if (!dentro) {
                            boundsJob?.cancel()
                            val pedidoEn = android.os.SystemClock.elapsedRealtime()
                            boundsJob = scope.launch {
                                val hLat = bb.latNorth - bb.latSouth
                                val hLng = bb.lonEast - bb.lonWest
                                val area = doubleArrayOf(
                                    bb.latSouth - hLat / 2, bb.lonWest - hLng / 2,
                                    bb.latNorth + hLat / 2, bb.lonEast + hLng / 2,
                                )
                                val r = withContext(Dispatchers.IO) {
                                    viewModel.spotsInBounds(area[0], area[1], area[2], area[3])
                                }
                                tooWide = r == null
                                android.util.Log.d(TAG, "[t+${ms()}ms] en pantalla: ${r?.size ?: "demasiado lejos/fallo"} lugar(es) — red ${android.os.SystemClock.elapsedRealtime() - pedidoEn}ms")
                                if (r != null) {
                                    pedido = if (r.size < 150) area else null
                                    if (r.map { it.id } != spots.map { it.id }) spots = r
                                }
                            }
                        }
                    }
                    // Mientras se arrastra: el pin se levanta y se suelta la selección.
                    addMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                if (!moviendo) { moviendo = true; selectedId = null }
                                return false
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                if (!moviendo) { moviendo = true; selectedId = null }
                                return false
                            }
                        },
                    )
                    // Al soltar (400 ms sin gestos): el centro pasa a ser el punto.
                    addMapListener(
                        DelayedMapListener(
                            object : MapListener {
                                private fun alSoltar(): Boolean {
                                    moviendo = false
                                    soltadoEn = android.os.SystemClock.elapsedRealtime()
                                    val c = mapCenter
                                    centro = GeoPoint(c.latitude, c.longitude)
                                    android.util.Log.d(TAG, "[t+${ms()}ms] mapa detenido — centro=(${c.latitude},${c.longitude})")
                                    pedirEnPantalla()
                                    return false
                                }
                                override fun onScroll(event: ScrollEvent?) = alSoltar()
                                override fun onZoom(event: ZoomEvent?) = alSoltar()
                            },
                            400,
                        ),
                    )
                    addOnFirstLayoutListener { _, _, _, _, _ ->
                        android.util.Log.d(TAG, "[t+${ms()}ms] primer layout del mapa")
                        pedirEnPantalla()
                    }
                }
            },
            update = { map ->
                map.overlays.removeAll { it is Marker }
                spots.sortedBy { it.id == selectedId }.forEach { spot ->
                    val esElegido = spot.id == selectedId
                    map.overlays.add(
                        Marker(map).apply {
                            position = GeoPoint(spot.lat, spot.lng)
                            title = spot.name
                            icon = pinDeLugar(map.context, spot.name, esElegido, conNombre = true)
                            setAnchor(Marker.ANCHOR_CENTER, anclaVerticalDelPin(map.context, esElegido, conNombre = true))
                            infoWindow = null
                            setOnMarkerClickListener { _, _ ->
                                if (isSubmitting) return@setOnMarkerClickListener true
                                android.util.Log.d(TAG, "pin existente tocado ${spot.id.take(8)} \"${spot.name}\" pendiente=${spot.estaPendiente}")
                                selectedId = spot.id
                                true
                            }
                        },
                    )
                }
                map.invalidate()
            },
        )

        // Pin central FIJO con su línea fina: la PUNTA de la línea es el punto
        // elegido y queda exactamente en el centro del mapa. Los offsets son
        // fijos para que la punta no se mueva cuando cambia la etiqueta.
        // (El mapa ocupa toda la pantalla y el panel flota encima, así que el
        // centro del mapa es el centro de este Box.)
        if (elegido == null) {
            val ancho = 2.dp
            Box(Modifier.align(Alignment.Center).size(6.dp).clip(CircleShape).background(BuddyColor.Ink.copy(alpha = 0.25f)))
            Box(
                Modifier.align(Alignment.Center).offset(y = (-8).dp - elevacion)
                    .size(width = ancho, height = 16.dp).background(BuddyColor.Ink),
            )
            Box(
                Modifier.align(Alignment.Center).offset(y = (-(16 + 18)).dp - elevacion)
                    .size(36.dp).shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)).background(BuddyColor.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = BuddyColor.InkInverse, modifier = Modifier.size(18.dp))
            }
            Column(
                Modifier.align(Alignment.Center).offset(y = (-(16 + 36 + 8 + 20)).dp - elevacion)
                    .widthIn(max = 240.dp)
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Punto elegido", style = BuddyType.Caption2, color = BuddyColor.InkMuted)
                Text(
                    if (moviendo) "…" else (direccion ?: "Buscando dirección…"),
                    style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }

        // Barra superior: volver + instrucción. Flota sobre el mapa.
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = insetSuperior + 8.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .shadow(4.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Surface)
                        .clickable { android.util.Log.d(TAG, "volver"); onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver",
                         tint = BuddyColor.Ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                BuddyTextField(
                    value = consulta,
                    onValueChange = { consulta = it },
                    placeholder = "Buscar un lugar o dirección",
                    modifier = Modifier.weight(1f),
                )
            }
            if (resultados.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(Radius.md))
                        .clip(RoundedCornerShape(Radius.md))
                        .background(BuddyColor.Surface),
                ) {
                    resultados.forEachIndexed { i, a ->
                        val titulo = a.featureName ?: a.thoroughfare ?: a.locality ?: "Sin nombre"
                        val sub = (0..a.maxAddressLineIndex).joinToString(", ") { a.getAddressLine(it) }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda → \"$titulo\" (${a.latitude},${a.longitude})")
                                    mapRef?.controller?.apply { setZoom(17.0); animateTo(GeoPoint(a.latitude, a.longitude)) }
                                    selectedId = null
                                    consulta = ""
                                    resultados = emptyList()
                                }
                                .padding(horizontal = Spacing.md, vertical = 10.dp),
                        ) {
                            Text(titulo, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(sub, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1,
                                 overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        if (i < resultados.size - 1) androidx.compose.material3.HorizontalDivider(color = BuddyColor.Border)
                    }
                }
            } else if (tooWide) {
                Text(
                    "Acércate para ver los lugares",
                    style = BuddyType.Footnote, color = BuddyColor.Ink,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .shadow(4.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Surface)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Panel inferior: lo elegido (lugar existente o punto nuevo).
        val mostrarPanel = true
        if (mostrarPanel) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                    .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                    .background(BuddyColor.Surface)
                    .padding(horizontal = Spacing.edge)
                    .padding(top = Spacing.md, bottom = Spacing.md + insetInferior),
            ) {
                if (elegido != null) {
                    Text(
                        if (elegido.estaPendiente) "LUGAR POR REVISAR" else "LUGAR EN EL MAPA",
                        style = BuddyType.Eyebrow, color = BuddyColor.InkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(elegido.name, style = BuddyType.Title3, color = BuddyColor.Ink, maxLines = 2)
                    elegido.categoryName?.let {
                        Text(it, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                    }
                    Spacer(Modifier.height(Spacing.md))
                    BotonPrincipal(
                        texto = "Elegir este lugar",
                        habilitado = !isSubmitting,
                        cargando = false,
                        onClick = {
                            android.util.Log.d(TAG, "elegir lugar existente ${elegido.id.take(8)} destino=${elegido.destinationId?.take(8)}")
                            onPickSpot(
                                ApiNearbySpot(
                                    id = elegido.id, name = elegido.name,
                                    lat = elegido.lat, lng = elegido.lng,
                                    coverUrl = elegido.coverUrl,
                                    destinationId = elegido.destinationId,
                                    status = elegido.status,
                                ),
                            )
                        },
                    )
                } else {
                    Text("NUEVO LUGAR", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                    Spacer(Modifier.height(Spacing.sm))
                    BuddyTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        placeholder = "¿Cómo se llama? Ej. Cafetería Rosal",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (categories.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            categories.forEach { categoria ->
                                val activa = categoriaId == categoria.id
                                Text(
                                    categoria.name,
                                    style = BuddyType.Footnote,
                                    color = if (activa) BuddyColor.InkInverse else BuddyColor.Ink,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (activa) BuddyColor.Ink else BuddyColor.Surface)
                                        .border(
                                            1.dp,
                                            if (activa) Color.Transparent else BuddyColor.Border,
                                            RoundedCornerShape(50),
                                        )
                                        .clickable { categoriaId = if (activa) null else categoria.id }
                                        .padding(horizontal = Spacing.md, vertical = 9.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    BotonPrincipal(
                        texto = "Compartir aquí",
                        habilitado = nombre.trim().isNotEmpty() && !isSubmitting && centro != null && !moviendo,
                        cargando = isSubmitting,
                        onClick = {
                            val punto = centro ?: return@BotonPrincipal
                            android.util.Log.d(TAG, "proponer \"${nombre.trim()}\" cat=${categoriaId?.take(8)} en (${punto.latitude},${punto.longitude}) dir=$direccion")
                            onProposeAt(nombre, categoriaId, punto.latitude, punto.longitude)
                        },
                    )
                }
                errorMessage?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = BuddyType.Caption1, color = BuddyColor.ErrorRed, textAlign = TextAlign.Center,
                         modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Text(
            "© OpenStreetMap",
            fontSize = 10.sp, color = BuddyColor.InkMuted,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = insetInferior + if (mostrarPanel) 0.dp else 4.dp)
                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun BotonPrincipal(texto: String, habilitado: Boolean, cargando: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(if (habilitado) BuddyColor.Ink else BuddyColor.InkMuted)
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cargando) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.InkInverse)
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(texto, style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
    }
}

private const val TAG = "ElegirLugarMapa"

/** "Av. de Cervantes 3" — calle y número; sin ellos, el nombre del sitio o la localidad. */
@Suppress("DEPRECATION")
private fun direccionDe(contexto: android.content.Context, lat: Double, lng: Double): String? =
    runCatching {
        if (!android.location.Geocoder.isPresent()) return null
        val a = android.location.Geocoder(contexto, java.util.Locale.getDefault())
            .getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        val calle = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ")
        calle.ifEmpty { a.featureName ?: a.locality }
    }.getOrNull()

/** Busca por texto cerca de lo que el mapa muestra: "café" debe encontrar los de aquí. */
@Suppress("DEPRECATION")
private fun buscarLugar(contexto: android.content.Context, texto: String, lat: Double?, lng: Double?): List<android.location.Address> =
    runCatching {
        if (!android.location.Geocoder.isPresent()) {
            android.util.Log.w(TAG, "búsqueda: Geocoder NO disponible en este dispositivo")
            return emptyList()
        }
        val g = android.location.Geocoder(contexto, java.util.Locale.getDefault())
        if (lat != null && lng != null) g.getFromLocationName(texto, 5, lat - 0.5, lng - 0.5, lat + 0.5, lng + 0.5)
        else g.getFromLocationName(texto, 5)
    }.onFailure { android.util.Log.e(TAG, "búsqueda \"$texto\" FALLÓ", it) }.getOrNull().orEmpty()
