package com.buddy.app.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Un solo visor de fotos en toda la app, igual que iOS (good-design-rules
 * regla 26, Keyla 21 nov 2026):
 *  - de lado = foto siguiente / anterior; tocar la mitad derecha / izquierda también;
 *  - pellizcar o doble toque = zoom (hasta 4x); con zoom se arrastra para mirar,
 *    las barras se ocultan y nada más reacciona;
 *  - hacia abajo = cerrar (sin zoom); la X de arriba también cierra;
 *  - con publicaciones (grupos): al terminar las fotos sigue con la siguiente;
 *    hacia arriba salta al siguiente trip; hacia abajo siempre cierra.
 */
data class ViewerPage(val group: Int, val index: Int, val url: String?)

/** Lo que muestra la barra de arriba para cada grupo (autor y lugar). */
data class ViewerHeader(
    val title: String? = null,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    /** Productos (iOS, 23 Nov 2026): título y "S/ 20 · 250 g · 2 de 13" sin
     *  avatar ni barras; el número y las miniaturas cuentan TODAS las fotos. */
    val plain: Boolean = false,
)

object ViewerPages {
    /** Una página por foto, en orden; un grupo sin fotos ocupa una página (su portada). */
    fun make(groups: List<List<String>>, fallback: (Int) -> String? = { null }): List<ViewerPage> =
        groups.flatMapIndexed { g, urls ->
            if (urls.isEmpty()) listOf(ViewerPage(g, 0, fallback(g)))
            else urls.mapIndexed { i, u -> ViewerPage(g, i, u) }
        }

    fun firstOf(pages: List<ViewerPage>, group: Int) = pages.indexOfFirst { it.group == group }
}

@Composable
fun PhotoViewerDialog(
    pages: List<ViewerPage>,
    startPage: Int,
    onDismiss: () -> Unit,
    headerFor: (group: Int) -> ViewerHeader = { ViewerHeader() },
    /** Clave de la miniatura de cada página (PhotoSources), para abrir y cerrar desde ella. */
    sourceKey: (ViewerPage) -> String? = { null },
    /** Avisa qué página se ve (el perfil se desplaza para tener su miniatura en pantalla). */
    onShowing: (ViewerPage) -> Unit = {},
) {
    // Capa dentro de la MISMA ventana de la app (PhotoOverlayHost), no un Dialog:
    // un Dialog es otra ventana que nace bajo la barra de estado, cambia de
    // tamaño un instante después y reiniciaba la animación (Keyla, 23 Nov 2026).
    val latest = androidx.compose.runtime.rememberUpdatedState(
        PhotoViewerArgs(pages, startPage, onDismiss, headerFor, sourceKey, onShowing))
    androidx.compose.runtime.DisposableEffect(Unit) {
        val content: @Composable () -> Unit = {
            val a = latest.value
            PhotoViewer(a.pages, a.startPage, a.onDismiss, a.headerFor, a.sourceKey, a.onShowing)
        }
        PhotoOverlay.content.value = content
        onDispose { if (PhotoOverlay.content.value === content) PhotoOverlay.content.value = null }
    }
}

private class PhotoViewerArgs(
    val pages: List<ViewerPage>, val startPage: Int, val onDismiss: () -> Unit,
    val headerFor: (Int) -> ViewerHeader, val sourceKey: (ViewerPage) -> String?, val onShowing: (ViewerPage) -> Unit,
)

/** El visor que está abierto, si hay uno. Lo dibuja [PhotoOverlayHost]. */
object PhotoOverlay {
    val content = androidx.compose.runtime.mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** Va al final de la raíz de la app: el visor se dibuja encima de todo, en la
 *  misma ventana y con las mismas coordenadas que las miniaturas. */
@Composable
fun PhotoOverlayHost() {
    PhotoOverlay.content.value?.let { c -> androidx.compose.runtime.key(c) { c() } }
}

@Composable
fun PhotoViewer(
    pages: List<ViewerPage>,
    startPage: Int,
    onDismiss: () -> Unit,
    headerFor: (group: Int) -> ViewerHeader = { ViewerHeader() },
    sourceKey: (ViewerPage) -> String? = { null },
    onShowing: (ViewerPage) -> Unit = {},
) {
    if (pages.isEmpty()) { LaunchedEffect(Unit) { onDismiss() }; return }
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var zoomed by remember { mutableStateOf(false) }
    // Arrastre (px) y apertura: 0 = en la miniatura, 1 = a pantalla completa.
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var viewerOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewerSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val page = pages[pager.currentPage.coerceIn(0, pages.lastIndex)]
    val groupPages = pages.filter { it.group == page.group }
    val groups = pages.maxOf { it.group } + 1
    val dyDp = with(density) { dragY.value.toDp().value }

    LaunchedEffect(page) { onShowing(page); PhotoSources.showing.value = sourceKey(page) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PhotoSources.showing.value = null } }

    // ── Transición compartida (Keyla, 23 Nov 2026) ─────────────────────────
    // Una sola imagen de transición, en su propia capa, va del rectángulo de la
    // tarjeta al de la foto encajada en pantalla: posición, tamaño y radio a la
    // vez, siempre con recorte (Crop), así la foto nunca se deforma. El carrusel
    // se muestra solo cuando la foto ya llegó; al cerrar se oculta y la imagen
    // vuelve a la tarjeta del producto que se está viendo en ese momento.
    val easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    var phase by remember { mutableStateOf(ViewerPhase.Opening) }
    val closeT = remember { Animatable(0f) }
    var closeFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    fun cardRect(p: ViewerPage): androidx.compose.ui.geometry.Rect? {
        val r = sourceKey(p)?.let { PhotoSources.rects[it] } ?: return null
        val local = r.translate(-viewerOrigin)
        if (viewerSize == androidx.compose.ui.geometry.Size.Zero || local.bottom < 0 || local.top > viewerSize.height) return null
        return local
    }
    fun fitRect(url: String?): androidx.compose.ui.geometry.Rect {
        val w = viewerSize.width; val h = viewerSize.height
        val a = url?.let { PhotoSources.aspects[it] } ?: (w / h)
        val (fw, fh) = if (a > w / h) w to w / a else h * a to h
        return androidx.compose.ui.geometry.Rect(Offset((w - fw) / 2f, (h - fh) / 2f), androidx.compose.ui.geometry.Size(fw, fh))
    }

    // La tarjeta de la foto que se ve queda oculta mientras el visor está
    // abierto: nunca se ven dos copias de la misma foto.
    LaunchedEffect(page, phase) { PhotoSources.hidden.value = sourceKey(page) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { PhotoSources.hidden.value = null } }

    // Abre UNA vez: cuando el visor ya está medido y se conoce la forma de la
    // foto (o a los 250 ms, con la forma de la pantalla).
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { viewerSize }.first { it.width > 0f && it.height > 0f }
        kotlinx.coroutines.withTimeoutOrNull(250) {
            androidx.compose.runtime.snapshotFlow { page.url?.let { PhotoSources.aspects[it] } }.first { it != null }
        }
        open.animateTo(1f, androidx.compose.animation.core.tween(400, easing = easing))
        if (phase == ViewerPhase.Opening) phase = ViewerPhase.Open
    }
    fun close() {
        if (closing) return
        closing = true
        // Sale desde donde está la foto ahora (también si se estaba arrastrando).
        val f = fitRect(page.url)
        val s = PhotoDismissPhysics.scale(with(density) { dragY.value.toDp().value })
        val from = if (phase == ViewerPhase.Opening) null else androidx.compose.ui.geometry.Rect(
            Offset(f.center.x - f.width * s / 2f + dragX.value, f.center.y - f.height * s / 2f + dragY.value),
            androidx.compose.ui.geometry.Size(f.width * s, f.height * s))
        closeFrom = from
        phase = ViewerPhase.Closing
        scope.launch {
            if (from == null) {
                open.animateTo(0f, androidx.compose.animation.core.tween(340, easing = easing))
            } else {
                closeT.animateTo(1f, androidx.compose.animation.core.tween(340, easing = easing))
            }
            onDismiss()
        }
    }
    BackHandler(onBack = ::close)

    fun goTo(i: Int) { scope.launch { pager.animateScrollToPage(i) } }
    fun step(d: Int) {
        val n = pager.currentPage + d
        if (n !in pages.indices) { if (d > 0) close(); return }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); goTo(n)
    }
    fun jumpGroup(d: Int) {
        val n = ViewerPages.firstOf(pages, page.group + d)
        if (n < 0) { if (d > 0) close(); return }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress); goTo(n)
    }

    // Rectángulo de la imagen de transición en este cuadro.
    val t = open.value
    val transitionRect: androidx.compose.ui.geometry.Rect? = when {
        viewerSize == androidx.compose.ui.geometry.Size.Zero -> null
        phase == ViewerPhase.Opening || (phase == ViewerPhase.Closing && closeFrom == null) -> {
            val to = fitRect(page.url)
            val from = cardRect(page) ?: to.let { r ->
                androidx.compose.ui.geometry.Rect(r.center.x - r.width * 0.46f, r.center.y - r.height * 0.46f,
                    r.center.x + r.width * 0.46f, r.center.y + r.height * 0.46f) }
            androidx.compose.ui.geometry.lerp(from, to, t)
        }
        phase == ViewerPhase.Closing -> {
            val from = closeFrom!!
            // El destino se calcula en cada cuadro: la fila o la cuadrícula ya se
            // desplazó a la tarjeta del producto que se veía.
            val to = cardRect(page) ?: androidx.compose.ui.geometry.Rect(
                from.center.x - from.width * 0.42f, from.center.y - from.height * 0.42f,
                from.center.x + from.width * 0.42f, from.center.y + from.height * 0.42f)
            androidx.compose.ui.geometry.lerp(from, to, closeT.value)
        }
        else -> null
    }
    val hasCard = cardRect(page) != null
    // Radio: el de la tarjeta (10 dp) en la tarjeta, 0 a pantalla completa.
    val progressToCard = when (phase) {
        ViewerPhase.Opening -> 1f - t
        ViewerPhase.Closing -> if (closeFrom == null) 1f - t else closeT.value
        else -> 0f
    }
    val cornerDp = if (hasCard) 10f * progressToCard else 0f
    val layerAlpha = if (!hasCard && phase == ViewerPhase.Closing) 1f - progressToCard else 1f
    val dragScale = PhotoDismissPhysics.scale(dyDp)
    val backdrop = when (phase) {
        ViewerPhase.Opening -> t
        ViewerPhase.Closing -> 1f - progressToCard
        else -> PhotoDismissPhysics.backdrop(dyDp)
    }
    val chrome = if (zoomed || phase != ViewerPhase.Open) 0f else maxOf(0f, 1f - maxOf(dyDp, 0f) / 150f)

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { viewerOrigin = it.positionOnScreen(); viewerSize = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()) }
            .background(Color.Black.copy(alpha = backdrop))
            .testTag("photoViewer")
            .pointerInput(zoomed, page.group) {
                if (zoomed) return@pointerInput
                val tracker = VelocityTracker()
                var vertical: Boolean? = null
                var tx = 0f; var ty = 0f
                // La dirección se decide con 12 dp de recorrido y el eje vertical
                // tiene que ganar claro (1.5x): antes bastaban 2 px, y un deslizar
                // a la izquierda que empezaba un poco hacia abajo se tomaba como
                // "cerrar", frenaba el carrusel y la foto volvía a su sitio
                // (Keyla, 23 Nov 2026: "se siente duro").
                val decidePx = 12.dp.toPx()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking(); vertical = null; tx = 0f; ty = 0f },
                    onDrag = { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        tx += amount.x; ty += amount.y
                        if (vertical == null && (abs(tx) > decidePx || abs(ty) > decidePx)) vertical = abs(ty) > abs(tx) * 1.5f
                        if (vertical == true) {
                            // Hacia abajo la foto sigue al dedo (también de lado).
                            val ny = dragY.value + amount.y
                            if (ny > 0f || dragY.value > 0f) {
                                scope.launch { dragY.snapTo(maxOf(0f, ny)); dragX.snapTo(dragX.value + amount.x) }
                                change.consume()
                            }
                        }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity()
                        val velDp = with(density) { v.y.toDp().value }
                        val dy = with(density) { dragY.value.toDp().value }
                        when {
                            dragY.value <= 0f && vertical == true && velDp < -600f && groups > 1 -> jumpGroup(1)
                            PhotoDismissPhysics.shouldDismiss(dy, velDp) -> close()
                            else -> scope.launch {
                                launch { dragX.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f)) }
                                dragY.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.85f))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f); dragY.animateTo(0f) } },
                )
            },
    ) {
        HorizontalPager(
            state = pager,
            // Un deslizar corto basta para pasar de foto (antes pedía media pantalla).
            flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                state = pager,
                snapPositionalThreshold = 0.2f,
            ),
            userScrollEnabled = !zoomed && dragY.value == 0f && phase == ViewerPhase.Open,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                alpha = if (phase == ViewerPhase.Open) 1f else 0f
                scaleX = dragScale; scaleY = dragScale
                translationX = dragX.value; translationY = dragY.value
            },
        ) { i ->
            ZoomableImage(
                url = pages[i].url,
                active = i == pager.currentPage,
                onZoomChange = { zoomed = it },
                onTap = { x -> step(if (x < 0.5f) -1 else 1) },
            )
        }

        transitionRect?.let { r ->
            TransitionImage(page.url, r, cornerDp, layerAlpha)
        }

        // Barras: se ocultan con zoom, al arrastrar y al cerrar.
        Column(Modifier.fillMaxSize().alpha(chrome)) {
            val header = headerFor(page.group)
            TopBar(
                header = header,
                count = if (header.plain) pages.size else groupPages.size,
                position = if (header.plain) pager.currentPage else page.index,
                onClose = ::close,
            )
            Spacer(Modifier.weight(1f))
            if (header.plain && pages.size > 1 && !zoomed) {
                Thumbs(pages, pager.currentPage) { n -> goTo(n) }
            } else if (!header.plain && groupPages.size > 1 && !zoomed) {
                Thumbs(groupPages, page.index) { idx ->
                    val n = pages.indexOfFirst { it.group == page.group && it.index == idx }
                    if (n >= 0) goTo(n)
                }
            }
        }
    }
}

@Composable
private fun TopBar(header: ViewerHeader, count: Int, position: Int, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (count > 1 && header.title != null && !header.plain) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape)
                        .background(if (i <= position) Color.White else Color.White.copy(alpha = 0.3f)))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (header.title != null && header.plain) {
                Column(Modifier.weight(1f)) {
                    Text(header.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2,
                         overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(header.subtitle, if (count > 1) "${position + 1} de $count" else null).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
            } else if (header.title != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE3EADD)), contentAlignment = Alignment.Center) {
                    Text(header.title.take(1), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2F4A3E))
                    if (header.avatarUrl != null) SubcomposeAsyncImage(model = header.avatarUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f)) {
                    Text(header.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                         overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(header.subtitle, if (count > 1) "${position + 1} de $count" else null).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            } else {
                Text(if (count > 1) "${position + 1} de $count" else "", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                     color = Color.White, modifier = Modifier.weight(1f))
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onClose).semantics { contentDescription = "Cerrar" },
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Thumbs(groupPages: List<ViewerPage>, position: Int, onPick: (Int) -> Unit) {
    val list = rememberLazyListState()
    LaunchedEffect(position) { list.animateScrollToItem((position - 2).coerceAtLeast(0)) }
    LazyRow(
        state = list,
        modifier = Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(groupPages) { i, p ->
            val shape = RoundedCornerShape(10.dp)
            SubcomposeAsyncImage(
                model = p.url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(shape).background(Color.White.copy(alpha = 0.1f))
                    .then(if (i == position) Modifier.border(2.dp, Color.White, shape) else Modifier)
                    .alpha(if (i == position) 1f else 0.55f)
                    .clickable { onPick(i) },
            )
        }
    }
}

/**
 * Foto con zoom: dos dedos acercan (1x a 4x), doble toque acerca 2.5x o vuelve,
 * con zoom un dedo arrastra. Sin zoom, un dedo no se toca: lo usa el carrusel
 * para pasar de foto (como la app de fotos).
 */
@Composable
fun ZoomableImage(url: String?, active: Boolean, onZoomChange: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Al dejar la página, vuelve al tamaño normal.
    LaunchedEffect(active) { if (!active && scale != 1f) { scale = 1f; offset = Offset.Zero; onZoomChange(false) } }
    fun setScale(s: Float) {
        val was = scale > 1.01f
        scale = s.coerceIn(1f, 4f)
        if (scale <= 1.01f) offset = Offset.Zero
        val now = scale > 1.01f
        if (now != was) onZoomChange(now)
    }
    Box(
        Modifier.fillMaxSize()
            .semantics { contentDescription = "Foto. Pellizca o toca dos veces para acercar" }
            .testTag("zoomablePhoto")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        if (scale > 1.01f) setScale(1f)
                        else {
                            setScale(2.5f)
                            offset = Offset((size.width / 2f - p.x) * 1.5f, (size.height / 2f - p.y) * 1.5f)
                        }
                    },
                    onTap = { p -> if (scale <= 1.01f) onTap(p.x / size.width) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // Un dedo sin zoom: se deja pasar al carrusel.
                        if (fingers >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f) setScale(scale * zoom)
                            if (scale > 1.01f) {
                                val maxX = (size.width * (scale - 1)) / 2f
                                val maxY = (size.height * (scale - 1)) / 2f
                                offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Fit,
            onSuccess = { PhotoSources.noteAspect(url, it.painter.intrinsicSize) },
            loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp)) } },
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        )
    }
}


/**
 * Dónde está cada miniatura en la pantalla (coordenadas de pantalla), para que
 * la foto crezca desde ella y vuelva a ella al cerrar (como iOS, Keyla 21 nov 2026).
 */
object PhotoSources {
    val rects = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    /** Clave de la miniatura de la foto que muestra el visor (null si está cerrado):
     *  la fila o grilla que la tiene se desplaza para dejarla en pantalla. */
    val showing = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** Ancho / alto de cada foto ya cargada: la transición sabe su forma final. */
    val aspects = androidx.compose.runtime.mutableStateMapOf<String, Float>()
    /** Miniatura oculta mientras su foto está en el visor (sin copias dobles). */
    val hidden = androidx.compose.runtime.mutableStateOf<String?>(null)

    fun noteAspect(url: String?, size: androidx.compose.ui.geometry.Size) {
        if (url != null && size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite())
            aspects[url] = size.width / size.height
    }
}

private enum class ViewerPhase { Opening, Open, Closing }

/** La imagen que viaja entre la tarjeta y la pantalla completa. */
@Composable
private fun TransitionImage(url: String?, r: androidx.compose.ui.geometry.Rect, cornerDp: Float, alpha: Float) {
    Box(
        Modifier
            .layout { m, _ ->
                val w = r.width.toInt().coerceAtLeast(1); val h = r.height.toInt().coerceAtLeast(1)
                val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                layout(w, h) { p.place(0, 0) }
            }
            .offset { androidx.compose.ui.unit.IntOffset(r.left.toInt(), r.top.toInt()) }
            .graphicsLayer {
                this.alpha = alpha
                shape = RoundedCornerShape(cornerDp.dp)
                clip = true
            },
    ) {
        SubcomposeAsyncImage(
            model = url, contentDescription = null, contentScale = ContentScale.Crop,
            onSuccess = { PhotoSources.noteAspect(url, it.painter.intrinsicSize) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * En una LazyRow/LazyColumn con miniaturas: mientras el visor muestra una de sus
 * fotos, la lista se desplaza (sin animar, el visor la tapa) para dejarla en
 * pantalla, así al cerrar la foto vuelve a ella (como iOS, Keyla 21 nov 2026).
 */
@Composable
fun FollowViewer(state: androidx.compose.foundation.lazy.LazyListState, indexOfKey: (String) -> Int) {
    val key = PhotoSources.showing.value
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        val i = indexOfKey(k)
        if (i < 0) return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo
        val fully = visible.any { it.index == i && it.offset >= 0 && it.offset + it.size <= state.layoutInfo.viewportEndOffset }
        if (!fully) state.scrollToItem(i)
    }
}

/** Lo mismo que [FollowViewer] para una cuadrícula (productos abiertos). */
@Composable
fun FollowViewerGrid(state: androidx.compose.foundation.lazy.grid.LazyGridState, indexOfKey: (String) -> Int) {
    val key = PhotoSources.showing.value
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        val i = indexOfKey(k)
        if (i < 0) return@LaunchedEffect
        val visible = state.layoutInfo.visibleItemsInfo
        val fully = visible.any { it.index == i && it.offset.y >= 0 && it.offset.y + it.size.height <= state.layoutInfo.viewportEndOffset }
        if (!fully) state.scrollToItem(i)
    }
}

/** En la miniatura que abre el visor. */
fun Modifier.photoSource(key: String): Modifier = this.then(
    Modifier.graphicsLayer { alpha = if (PhotoSources.hidden.value == key) 0f else 1f }
).then(
    Modifier.onGloballyPositioned { c ->
        val p = c.positionOnScreen()
        PhotoSources.rects[key] = androidx.compose.ui.geometry.Rect(p, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
    }
)

/** Física del arrastre para cerrar (mismos valores que iOS; PhotoDismissPhysicsTest). */
object PhotoDismissPhysics {
    const val DISTANCE_DP = 130f
    const val VELOCITY_DP = 1000f
    const val MIN_SCALE = 0.75f
    fun scale(dyDp: Float): Float = maxOf(MIN_SCALE, 1f - maxOf(dyDp, 0f) / 1200f)
    fun backdrop(dyDp: Float): Float = maxOf(0f, 1f - kotlin.math.abs(dyDp) / 450f)
    fun shouldDismiss(dyDp: Float, velocityDp: Float): Boolean = dyDp > DISTANCE_DP || (dyDp > 30f && velocityDp > VELOCITY_DP)
}

/** Igual que Modifier.photoSource, para usar sin importar la extensión. */
fun photoSourceModifier(key: String): Modifier = Modifier.photoSource(key)

