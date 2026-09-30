package com.buddy.app.features.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import com.buddy.app.core.location.DistanceResolver
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.designsystem.BuddyColor
import kotlin.math.abs

// Pager de ancho completo: la foto ES la tarjeta, el texto va ENCIMA sobre un
// degradado. El alto no se calcula acá — lo decide el llamador (weight(1f)
// en el Home, para ocupar el espacio libre de la pantalla, espejo de
// .frame(maxHeight: .infinity) en iOS), y cada card lo llena con
// fillParentMaxHeight().

/** true mientras se hace pinch sobre una foto: el pager deja de desplazarse
 *  mientras dura, como en iOS (CarouselZoomState). */
object CarouselZoom {
    var isZooming by mutableStateOf(false)
}

/**
 * Explora {ciudad} — el carrusel de lugares que recomiendan los buddies.
 *
 * Espejo de exploreCarousel (iOS). Ocupa el hueco donde antes iba la grilla de
 * 6 categorías, pero NO es un selector: las fotos inspiran, no se eligen. La
 * intención se sigue pidiendo, solo que después — nace de que un lugar llamó
 * la atención, no de una lista de temas.
 *
 * Una card por FOTO, no por lugar: si "El Encanto" tiene 3 fotos, se ven 3
 * tarjetas. Es lo que hace iOS y lo que el backend ordena en cover_urls.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExploreCarousel(
    cards: List<ApiPlaceCard>,
    /** Tocar la card abre ese lugar. */
    onOpenPlace: (ApiPlaceCard) -> Unit,
    modifier: Modifier = Modifier,
    /** Dibuja el carrusel REAL con tarjetas de relleno, no una silueta parecida:
     *  cualquier réplica hecha a mano se desalinea y el layout salta al llegar
     *  los datos. Con esto además se desactiva el scroll — arrastrar un
     *  esqueleto sugiere que hay contenido que explorar y no lo hay. */
    isSkeleton: Boolean = false,
    /** Última ubicación filtrada (LocationFilter). Nula sin GPS. */
    userLat: Double? = null,
    userLng: Double? = null,
    /** Id del lugar más cercano con margen: solo esa card dice "Estás aquí". */
    nearestId: String? = null,
) {
    val fuente = remember(cards, isSkeleton) {
        if (isSkeleton && cards.isEmpty()) ApiPlaceCard.placeholders() else cards
    }
    val photos = remember(fuente) {
        fuente.flatMap { place ->
            // coverPhotos manda cuando viene: es la única fuente que sabe de
            // quién es CADA foto. coverUrls queda de respaldo para endpoints que
            // no lo mandan y para respuestas viejas en caché.
            place.coverPhotos?.takeIf { it.isNotEmpty() }?.let { fotos ->
                return@flatMap fotos.mapIndexed { i, f ->
                    ExplorePhoto("${place.id}-$i", f.url, place, f.authorName, f.authorAvatarUrl)
                }
            }
            val urls = place.coverUrls?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(place.coverUrl)
            // Sin fotos la card igual existe (el esqueleto no tiene ninguna):
            // la lista vacía la borraría del carrusel y dejaría huecos.
            if (urls.isEmpty())
                listOf(ExplorePhoto("${place.id}-0", null, place,
                                    place.coverAuthorName, place.coverAuthorAvatarUrl))
            else urls.mapIndexed { i, url ->
                ExplorePhoto("${place.id}-$i", url, place,
                             place.coverAuthorName, place.coverAuthorAvatarUrl)
            }
        }
    }
    if (photos.isEmpty()) return

    // Scroll infinito: un índice VIRTUAL enorme, mapeado al real con módulo.
    // El pager arranca a la mitad de ese rango — de ahí se puede seguir para
    // cualquiera de los dos lados sin llegar nunca al borde real. No hay
    // "vuelta al principio" que notar: la lista simplemente no se acaba.
    val realCount = photos.size
    // remember(realCount) y no rememberLazyListState(initialFirstVisibleItemIndex=):
    // ese initial solo se respeta en la PRIMERA composición. La pantalla pasa
    // por un esqueleto antes de los datos reales — con menos cards que el
    // set real — así que si el índice de arranque quedaba alineado al
    // esqueleto, al llegar los datos reales el mismo índice virtual caía en
    // OTRA foto por el cambio de módulo: la card saltaba a algo random justo
    // cuando debía mostrar la primera de verdad. remember(realCount) fuerza
    // un LazyListState nuevo, alineado al conteo correcto, cada vez que el
    // conteo cambia.
    val listState = remember(realCount) {
        val startIndex = (Int.MAX_VALUE / 2) - (Int.MAX_VALUE / 2) % realCount
        androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = startIndex)
    }


    // Pager vertical simple: una card por página, ancho completo, swipe hacia
    // abajo para la siguiente. Antes era un Coverflow horizontal (peek +
    // escala por distancia al centro) — se cambió el eje pero no el contenido
    // de cada card, que sigue siendo ExploreCarouselCard tal cual.
    LazyColumn(
        state = listState,
        // El snap deja siempre una card completa a la vista — sin él el pager
        // para a mitad de camino entre dos fotos.
        //
        // El spring por default (StiffnessMediumLow) es el sospechoso más
        // probable de la sensación de "lento": es un resorte pensado para
        // listas genéricas, no para un asentado corto y decidido. Se sube
        // la rigidez y se quita el rebote (DampingRatioNoBouncy) — decidido
        // sin sentirse brusco. Falta medir en dispositivo real si el resto
        // (jank de decode de imagen, etc.) también pesa — no se tocó nada
        // de Coil todavía porque AsyncImage ya pide el tamaño medido del
        // composable, no el original: ese sospechoso de la revisión no
        // aplica acá sin medir primero.
        flingBehavior = run {
            val baseProvider = remember(listState) {
                androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(listState)
            }
            // calculateApproachOffset en 0: sin esto, un swipe brusco usaba
            // el decay para "acercarse" antes de buscar dónde encajar, y ese
            // acercamiento podía cruzar varias cards de un tirón — el bug
            // reportado ("de un swipe rápido salta muchas recomendaciones").
            // En 0 el fling SIEMPRE resuelve por snap directo al vecino más
            // cercano en la dirección del gesto: como mucho, una card.
            val singleStepProvider = remember(baseProvider) {
                object : androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider by baseProvider {
                    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
                }
            }
            val decay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
            remember(singleStepProvider, decay) {
                androidx.compose.foundation.gestures.snapping.SnapFlingBehavior(
                    snapLayoutInfoProvider = singleStepProvider,
                    decayAnimationSpec = decay,
                    snapAnimationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 800f,
                    ),
                )
            }
        },
        userScrollEnabled = !isSkeleton && !CarouselZoom.isZooming,
        // Sin height fijo: el llamador decide el alto (weight(1f) en el Home
        // para ocupar el espacio libre, o un tamaño fijo en otros usos).
        modifier = modifier.fillMaxWidth(),
    ) {
        // count = Int.MAX_VALUE y no photos.size: es lo que hace el loop
        // posible. La key combina índice virtual + id real — así cada vuelta
        // del ciclo es una key distinta (Compose la pide única) pero el
        // CONTENIDO en cada posición es siempre el que toca según el módulo.
        items(
            count = Int.MAX_VALUE,
            key = { virtualIndex -> "$virtualIndex-${photos[virtualIndex % realCount].id}" },
        ) { virtualIndex ->
            val photo = photos[virtualIndex % realCount]
            ExploreCarouselCard(
                photo = photo,
                isSkeleton = isSkeleton,
                userLat = userLat,
                userLng = userLng,
                isNearest = photo.place.id == nearestId,
                modifier = Modifier
                    .fillParentMaxWidth()
                    .fillParentMaxHeight()
                    .clickable(
                        enabled = !isSkeleton,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpenPlace(photo.place) },
            )
        }
    }
}

/**
 * Deja que el carrusel sangre hasta el borde de la pantalla aunque su
 * contenedor tenga padding lateral (el composer entero lo tiene, para la
 * cabecera y el subtítulo). Sin esto la foto queda inset — un borde en
 * blanco a los lados que iOS no tiene, porque ahí el carrusel sale del
 * padding con GeometryReader.
 *
 * Un layout modifier y no padding negativo: Compose rechaza un padding
 * menor que cero.
 */
fun Modifier.sangraLateral(cantidad: androidx.compose.ui.unit.Dp) =
    layout { measurable, constraints ->
        val extra = cantidad.roundToPx() * 2
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
                maxWidth = if (constraints.maxWidth == androidx.compose.ui.unit.Constraints.Infinity)
                    constraints.maxWidth else constraints.maxWidth + extra,
            )
        )
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-cantidad.roundToPx(), 0)
        }
    }

/** Una foto concreta del carrusel, con el lugar al que pertenece. */
private data class ExplorePhoto(
    val id: String,
    val url: String?,
    val place: ApiPlaceCard,
    /** Quién aportó ESTA foto — no el autor de la tarjeta del lugar. */
    val authorName: String?,
    val authorAvatarUrl: String?,
)

/**
 * La foto ES la tarjeta — espejo exacto de ExploreCarouselCard (iOS): nombre,
 * categoría y quién recomienda van ENCIMA de la imagen, sobre un degradado,
 * en vez de robarle una banda propia. Sin esquinas redondeadas ni borde: la
 * foto llega a los bordes y es ella la que delimita la tarjeta.
 */
@Composable
private fun ExploreCarouselCard(
    photo: ExplorePhoto,
    isSkeleton: Boolean,
    userLat: Double?,
    userLng: Double?,
    isNearest: Boolean,
    modifier: Modifier = Modifier,
) {
    val place = photo.place

    // Distancia MOSTRADA y "Estás aquí" con histéresis. La lógica vive en
    // DistanceResolver; la card solo guarda lo que está mostrando, así el ruido
    // del GPS no hace saltar el número. Espejo de recompute() (iOS).
    var shownDistance by remember(place.id) { mutableStateOf<Double?>(null) }
    var isHere by remember(place.id) { mutableStateOf(false) }
    LaunchedEffect(place.id, userLat, userLng, isNearest) {
        val nueva = DistanceResolver.distance(userLat, userLng, place) ?: place.distanceMeters?.toDouble()
        if (nueva != null && DistanceResolver.shouldUpdate(shownDistance, nueva)) shownDistance = nueva
        isHere = DistanceResolver.isHere(isHere, shownDistance, isNearest)
    }
    // El prefijo "Distancia" solo tiene sentido cuando lo que se muestra ES
    // una distancia (no "Estás aquí"), y solo pasados 2 km — de cerca la cifra
    // ya se explica sola. Espejo de etiquetaDistancia (iOS).
    val etiqueta = shownDistance?.let {
        if (isHere) "Estás aquí"
        else {
            val base = DistanceResolver.label(it)
            if (it < 2000) base else "Distancia $base"
        }
    }

    // Zoom leve al cambiar el valor, solo en cambios reales.
    var pulsando by remember { mutableStateOf(false) }
    var previa by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(etiqueta) {
        val antes = previa
        previa = etiqueta
        if (antes != null && etiqueta != null && antes != etiqueta) {
            android.util.Log.d("ExploreCarousel", "📏 [distancia] ${place.name}: $antes → $etiqueta")
            pulsando = true
            delay(200)
            pulsando = false
        }
    }
    val escala by animateFloatAsState(
        targetValue = if (pulsando) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pulsoDistancia",
    )

    Box(modifier.clipToBounds()) {
        if (photo.url != null) {
            // Pinch para acercar la foto (el gesto instintivo al mirar una
            // foto). Solo con dos dedos: con uno el arrastre sigue siendo del
            // pager. Al soltar vuelve a su tamaño. Mientras dura, ni el pager
            // ni la pantalla se mueven.
            var zoom by remember { mutableStateOf(1f) }
            var ancla by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Unspecified) }
            val zoomAnimado by animateFloatAsState(
                targetValue = zoom,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
                label = "zoomFoto",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    CarouselZoom.isZooming = true
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                    ancla = event.calculateCentroid(useCurrent = true)
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            CarouselZoom.isZooming = false
                            zoom = 1f
                        }
                    }
                    .graphicsLayer {
                        scaleX = zoomAnimado
                        scaleY = zoomAnimado
                        if (ancla != androidx.compose.ui.geometry.Offset.Unspecified && size.width > 0f) {
                            transformOrigin = TransformOrigin(
                                (ancla.x / size.width).coerceIn(0f, 1f),
                                (ancla.y / size.height).coerceIn(0f, 1f),
                            )
                        }
                    },
            ) {
                // Crop y no Fit: espejo exacto de iOS (.scaledToFill() en
                // ExploreCarouselCard). La foto llena la tarjeta de borde a
                // borde, sin franjas — el blur-backdrop es de OTRO componente
                // (memoir), no de este carrusel.
                AsyncImage(
                    model = photo.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(Modifier.fillMaxSize().background(BuddyColor.SurfaceRaised))
        }

        // Degradado propio y no material: tiene que oscurecer lo justo para
        // que el texto se lea sobre cualquier foto y desaparecer antes de la
        // mitad. Empieza transparente para no ensuciar la imagen.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to BuddyColor.Ink.copy(alpha = 0.62f),
                    ),
                ),
        )

        // Un lugar propuesto y aún sin aprobar. Va SOBRE la foto y no al pie
        // porque cambia cómo se lee toda la tarjeta: lo que muestra existe
        // solo para ti hasta que se apruebe, y enterarse al final sería
        // enterarse tarde. Sin icono de alerta: es un estado de espera, no un
        // problema del usuario.
        if (place.estaPendiente) {
            Text(
                "PENDIENTE DE APROBACIÓN",
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = BuddyColor.InkInverse,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BuddyColor.Ink.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        // Distancia sobre la foto, esquina superior izquierda. Solo texto,
        // sin icono. "Estás aquí" en color de marca: es la única señal que
        // cambia lo que el viajero puede hacer.
        if (!isSkeleton && etiqueta != null) {
            AnimatedContent(
                targetState = etiqueta,
                // Los dígitos ruedan en vez de reemplazarse de golpe.
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                },
                label = "etiquetaDistancia",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .clip(RoundedCornerShape(50))
                    .background(if (isHere) BuddyColor.Brand else BuddyColor.Surface.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) { texto ->
                Text(
                    texto,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHere) BuddyColor.InkInverse else BuddyColor.Ink,
                    maxLines = 1,
                )
            }
        }

        // Dos líneas y no tres: el nombre manda, y categoría y quién
        // recomienda comparten la segunda separadas por un punto medio.
        // Alineado a la izquierda —como el resto de la app— para que no se
        // lea como el pie de una publicación de red social.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                place.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BuddyColor.InkInverse,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val autor = photo.authorName?.trim()?.takeIf { it.isNotEmpty() }
                ?.split(" ")?.firstOrNull()
            // Blanco apagado: la segunda línea acompaña al nombre, no compite
            // con él, y sigue legible sobre el degradado. Un solo color para
            // toda la fila, como el foregroundStyle de iOS sobre el HStack.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides BuddyColor.InkInverse.copy(alpha = 0.88f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    place.category?.takeIf { it.isNotEmpty() }?.let { cat ->
                        Text(cat.replaceFirstChar { it.uppercase() }, fontSize = 12.5.sp)
                        Text("·", fontSize = 12.5.sp)
                    }
                    if (photo.authorName != null && !isSkeleton) {
                        Box(
                            Modifier.size(16.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photo.authorAvatarUrl != null) {
                                AsyncImage(
                                    model = photo.authorAvatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(16.dp).clip(CircleShape),
                                )
                            } else {
                                Text(
                                    photo.authorName.take(1).uppercase(),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BuddyColor.Ink,
                                )
                            }
                        }
                    }
                    Text(
                        // El nombre se distingue solo por peso: en brand competía de
                        // igual a igual con el del lugar.
                        buildAnnotatedString {
                            if (autor != null) {
                                append("Recomendado por ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(autor) }
                            } else {
                                append("Recomendado por la comunidad")
                            }
                        },
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
