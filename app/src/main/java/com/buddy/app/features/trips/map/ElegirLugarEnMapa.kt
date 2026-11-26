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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    /** Título de la barra (iOS: "Compartir un lugar" / "Agregar ubicación"). */
    title: String = "Compartir un lugar",
    /** Ubicación del negocio: el nombre es el del negocio y el botón dice "Guardar ubicación". */
    fixedName: String? = null,
    /** Texto del botón para un punto nuevo (Agregar ubicación en 2 pasos: "Siguiente"). */
    proposeTitle: String? = null,
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
    // Igual que iOS (Keyla, 25 Nov 2026): ruedita mientras busca, el buscador muestra
    // el punto elegido sin buscar, y al mover el mapa se repite la búsqueda escrita.
    var buscando by remember { mutableStateOf(false) }
    var autoRelleno by remember { mutableStateOf(false) }
    var buscadorActivo by remember { mutableStateOf(false) }
    var rebuscar by remember { mutableStateOf(0) }
    val ctxBusqueda = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(consulta, rebuscar) {
        if (autoRelleno) { autoRelleno = false; return@LaunchedEffect }
        val q = consulta.trim()
        if (q.length < 2) {
            if (resultados.isNotEmpty()) android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda vacía/corta — limpio resultados")
            resultados = emptyList(); buscando = false; return@LaunchedEffect
        }
        buscando = true
        kotlinx.coroutines.delay(400)
        val inicio = android.os.SystemClock.elapsedRealtime()
        val c = mapRef?.mapCenter
        val span = mapRef?.boundingBox?.let { maxOf(it.latNorth - it.latSouth, it.lonEast - it.lonWest) } ?: 0.0
        android.util.Log.d(TAG, "[t+${ms()}ms] búsqueda \"$q\" iniciada — cerca de ${c?.let { "(${it.latitude},${it.longitude})" } ?: "ninguna parte"}")
        val r = try {
            withContext(Dispatchers.IO) { buscarLugar(ctxBusqueda, q, c?.latitude, c?.longitude, span) }
        } finally { buscando = false }
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
    var nombre by remember { mutableStateOf(fixedName ?: "") }
    var altoPanel by remember { mutableStateOf(0) }
    var altoBarra by remember { mutableStateOf(0) }
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val teclado = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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
    // Dentro de un Dialog la ventana se desplazaba con el teclado y escondía la barra:
    // el panel ya sube solo (imePadding), así que la ventana no se mueve.
    val vista = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (vista.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    // Dirección del centro: se pide cuando el mapa se detiene, no en cada frame.
    val contexto = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(centro) {
        val c = centro ?: return@LaunchedEffect
        direccion = null
        val inicio = android.os.SystemClock.elapsedRealtime()
        val texto = withContext(Dispatchers.IO) { direccionDe(contexto, c.latitude, c.longitude) }
        direccion = texto ?: "Sin dirección"
        // Al soltar el mapa el buscador muestra el punto elegido (sin buscar).
        if (!buscadorActivo && texto != null && texto != consulta) {
            autoRelleno = true; consulta = texto; resultados = emptyList()
        }
        val ahora = android.os.SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "[t+${ms()}ms] dirección=${texto ?: "null"} — geocoder ${ahora - inicio}ms" +
            (if (soltadoEn > 0) ", ${ahora - soltadoEn}ms desde que soltaste el mapa" else ""))
    }
    val elevacion by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (moviendo) 10.dp else 0.dp, label = "elevacionPin",
    )

    Box(Modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = with(densidad) { altoBarra.toDp() }),
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
                    // Tocar el mapa cierra el teclado (Keyla, 25 Nov 2026); el mapa sigue moviéndose.
                    setOnTouchListener { _, ev ->
                        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                            focusManager.clearFocus(); teclado?.hide()
                        }
                        false
                    }

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
                                    // Con texto escrito, se busca otra vez en la nueva zona.
                                    if (buscadorActivo && consulta.trim().length >= 2) rebuscar++
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
                            this.title = spot.name
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
            Box(Modifier.align(Alignment.Center).offset(y = with(densidad) { (altoBarra / 2).toDp() }).size(6.dp).clip(CircleShape).background(BuddyColor.Ink.copy(alpha = 0.25f)))
            Box(
                Modifier.align(Alignment.Center).offset(y = with(densidad) { (altoBarra / 2).toDp() } - 8.dp - elevacion)
                    .size(width = ancho, height = 16.dp).background(BuddyColor.Ink),
            )
            Box(
                Modifier.align(Alignment.Center).offset(y = with(densidad) { (altoBarra / 2).toDp() } - (16 + 18).dp - elevacion)
                    .size(36.dp).shadow(4.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)).background(BuddyColor.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = BuddyColor.InkInverse, modifier = Modifier.size(18.dp))
            }
            Column(
                Modifier.align(Alignment.Center).offset(y = with(densidad) { (altoBarra / 2).toDp() } - (16 + 36 + 8 + 20).dp - elevacion)
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

        // Barra superior sólida (iOS, Keyla 25 Nov 2026): atrás + título; el mapa va debajo.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(BuddyColor.Surface)
                .padding(top = insetSuperior)
                .height(56.dp)
                .onGloballyPositioned { altoBarra = it.size.height },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(BuddyColor.Surface)
                    .clickable { android.util.Log.d(TAG, "volver"); onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver",
                     tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
            }
            Text(title, style = BuddyType.Headline, color = BuddyColor.Ink, modifier = Modifier.align(Alignment.Center))
        }

        // Acercar / alejar / mi ubicación, encima del panel (iOS).
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.edge, bottom = with(densidad) { altoPanel.toDp() } + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.buddy.app.core.designsystem.components.MapControlButton(Icons.Filled.Add, "Acercar") { mapRef?.controller?.zoomIn() }
            com.buddy.app.core.designsystem.components.MapControlButton(Icons.Filled.Remove, "Alejar") { mapRef?.controller?.zoomOut() }
            if (initialLat != null && initialLng != null) {
                com.buddy.app.core.designsystem.components.MapControlButton(
                    androidx.compose.material.icons.Icons.Filled.Navigation, "Mi ubicación", iconRotation = 45f,
                ) {
                    mapRef?.controller?.animateTo(GeoPoint(initialLat, initialLng), 17.0, 400L)
                }
            }
        }

        // Panel inferior: lo elegido (lugar existente o punto nuevo).
        val mostrarPanel = true
        if (mostrarPanel) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    // Con el teclado abierto el panel sube encima de él (resultados visibles).
                    .imePadding()
                    // El teclado tapaba la mitad del botón: un poco más de aire cuando está abierto.
                    .padding(bottom = if (androidx.compose.foundation.layout.WindowInsets.isImeVisible) 36.dp else 0.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { altoPanel = it.size.height }
                    .shadow(12.dp, RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                    .clip(RoundedCornerShape(topStart = Radius.lg, topEnd = Radius.lg))
                    .background(BuddyColor.Surface)
                    .padding(horizontal = Spacing.edge)
                    // En el Dialog a pantalla completa el inset puede llegar en 0: mínimo 28 dp para la barra de gestos.
                    .padding(top = Spacing.md, bottom = Spacing.md + maxOf(insetInferior, 28.dp)),
            ) {
                if (elegido == null) {
                    // Buscador dentro del panel, encima de "Nuevo lugar" (iOS).
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(BuddyColor.SageTint.copy(alpha = 0.5f))
                            .padding(horizontal = Spacing.md, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = BuddyColor.InkMuted, modifier = Modifier.size(18.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = consulta,
                            onValueChange = { consulta = it },
                            singleLine = true,
                            textStyle = BuddyType.Callout.copy(color = BuddyColor.Ink),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(BuddyColor.Brand),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .onFocusChanged { buscadorActivo = it.isFocused },
                            decorationBox = { inner ->
                                if (consulta.isEmpty()) Text("Buscar un lugar o dirección", style = BuddyType.Callout, color = BuddyColor.InkFaint)
                                inner()
                            },
                        )
                        if (buscando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.Brand)
                        if (consulta.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.Cancel, contentDescription = "Borrar búsqueda", tint = BuddyColor.InkFaint,
                                 modifier = Modifier.size(20.dp).clip(CircleShape).clickable { consulta = ""; resultados = emptyList() })
                        }
                    }
                    if (resultados.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.md))
                                .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md))
                                .background(BuddyColor.Surface),
                        ) {
                            resultados.take(4).forEachIndexed { i, a ->
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
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = Spacing.md, vertical = 10.dp),
                                ) {
                                    Text(titulo, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1,
                                         overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(sub, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1,
                                         overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                if (i < minOf(resultados.size, 4) - 1) androidx.compose.material3.HorizontalDivider(color = BuddyColor.Border)
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
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
                        texto = if (fixedName != null) "Es mi negocio" else "Elegir este lugar",
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
                    if (fixedName == null) {
                        Text("NUEVO LUGAR", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted)
                        Spacer(Modifier.height(Spacing.sm))
                    }
                    BuddyTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        placeholder = if (fixedName != null) "Nombre del lugar" else "¿Cómo se llama? Ej. Cafetería Rosal",
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
                        texto = proposeTitle ?: if (fixedName != null) "Guardar ubicación" else "Compartir aquí",
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

/**
 * Busca por texto cerca de lo que el mapa muestra: "café" debe encontrar los de aquí.
 * Si nada cercano coincide, también busca en todo el Perú (del más cercano al más
 * lejano) y prueba sin el número: "Francisco Arana 218" → "Francisco Arana" (iOS).
 */
@Suppress("DEPRECATION")
private fun buscarLugar(contexto: android.content.Context, texto: String, lat: Double?, lng: Double?, span: Double = 0.0): List<android.location.Address> =
    runCatching {
        if (!android.location.Geocoder.isPresent()) {
            android.util.Log.w(TAG, "búsqueda: Geocoder NO disponible en este dispositivo")
            return emptyList()
        }
        val g = android.location.Geocoder(contexto, java.util.Locale("es", "PE"))
        val d = maxOf(0.5, span * 0.75)
        val cerca = if (lat != null && lng != null) runCatching { g.getFromLocationName(texto, 5, lat - d, lng - d, lat + d, lng + d) }.getOrNull().orEmpty() else emptyList()
        val palabras = texto.lowercase().split(" ").filter { w -> w.isNotBlank() && w.none { it.isDigit() } }
        fun coincide(a: android.location.Address): Boolean {
            val n = listOfNotNull(a.featureName, a.thoroughfare, a.getAddressLine(0)).joinToString(" ").lowercase()
            return palabras.all { n.contains(it) }
        }
        if (cerca.any(::coincide)) return cerca
        // Todo el Perú, y sin números.
        val peru = runCatching { g.getFromLocationName(texto, 5, -18.4, -81.4, 0.0, -68.6) }.getOrNull().orEmpty()
        val corto = palabras.joinToString(" ")
        val sinNumero = if (corto.length >= 2 && corto != texto.lowercase())
            runCatching { g.getFromLocationName(corto, 5, -18.4, -81.4, 0.0, -68.6) }.getOrNull().orEmpty() else emptyList()
        fun dist(a: android.location.Address) = if (lat == null || lng == null) 0.0 else
            GeoPoint(lat, lng).distanceToAsDouble(GeoPoint(a.latitude, a.longitude))
        val todos = (cerca + peru + sinNumero).distinctBy { "${it.latitude}|${it.longitude}" }
        todos.filter(::coincide).sortedBy(::dist) + todos.filterNot(::coincide).sortedBy(::dist)
    }.onFailure { android.util.Log.e(TAG, "búsqueda \"$texto\" FALLÓ", it) }.getOrNull().orEmpty()
