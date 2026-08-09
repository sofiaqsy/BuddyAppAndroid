package com.buddy.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.Radius
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

// Medidas del carrusel — las mismas que iOS, en dp.
//
// El tamaño BASE es fijo y el escalado NUNCA lo toca: si el layout dependiera
// del tamaño ya escalado, las tres cards terminarían ocupando el mismo espacio
// visual y el efecto se pierde. La fila siempre reserva este ancho por card;
// scale + zIndex dibujan la del centro invadiendo el espacio de sus vecinas,
// como el carrusel destacado de la App Store.
private val CardWidth = 176.dp
private val PhotoHeight = 228.dp
/** La banda de texto mide 70: 7 de aire arriba, 8 (categoría) + 3 + 20 (nombre)
 *  + 3 + 20 (autor) = 54, y 7 abajo. */
private val CardHeight = PhotoHeight + 70.dp

/** 0.22 y no más: con 0.32 el contraste era tan alto que la card central se
 *  leía como "opción seleccionada" en vez de como profundidad. Y no menos,
 *  porque el efecto vive justamente de ese contraste. */
private const val ScaleDelta = 0.22f

/** Distancia a la que una card ya está completamente "al fondo". En dp, no en
 *  píxeles: iOS la expresa en puntos y hay que convertirla, no copiarla. */
private val ScaleFalloff = 160.dp

/** Aire vertical que la fila reserva ARRIBA Y ABAJO para que la card escalada
 *  no se recorte. scale no altera el layout, así que la fila mide CardHeight y
 *  la central —un 22% más alta— se sale por los dos lados: hay que sumarlo dos
 *  veces, no una. Con una sola la tarjeta salía cortada. */
private val VerticalSlack = CardHeight * ScaleDelta / 2 + 8.dp

private val CardSpacing = 10.dp

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
@Composable
fun ExploreCarousel(
    cards: List<ApiPlaceCard>,
    /** Tocar la card ya centrada abre ese lugar. Las laterales solo se centran. */
    onOpenPlace: (ApiPlaceCard) -> Unit,
    modifier: Modifier = Modifier,
    /** Dibuja el carrusel REAL con tarjetas de relleno, no una silueta parecida:
     *  cualquier réplica hecha a mano se desalinea y el layout salta al llegar
     *  los datos. Con esto además se desactiva el scroll — arrastrar un
     *  esqueleto sugiere que hay contenido que explorar y no lo hay. */
    isSkeleton: Boolean = false,
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

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sideInset = ((maxWidth - CardWidth) / 2).coerceAtLeast(0.dp)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewportCenterPx = with(density) { maxWidth.toPx() / 2f }
        // El falloff de iOS está en PUNTOS y acá las distancias vienen en
        // PÍXELES. Comparar 160 contra píxeles lo hacía ~3× más estrecho en un
        // teléfono de densidad 3: casi ninguna card llegaba a escalar y el
        // tamaño saltaba de golpe en vez de degradarse.
        val falloffPx = with(density) { ScaleFalloff.toPx() }

        // Índice de la card centrada — fuente de verdad única para el zIndex y
        // para decidir si un tap abre el lugar o solo lo centra. Derivado del
        // scroll real y no de un estado propio: escribirlo a mano dejaba el
        // z-order desactualizado durante todo el arrastre.
        val centerIndex by remember {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.minByOrNull {
                    abs((it.offset + it.size / 2f) - viewportCenterPx)
                }?.index ?: 0
            }
        }

        // TEMPORAL — quitar antes de publicar. Dice qué card cree el carrusel
        // que está centrada y con qué escala dibuja cada una: es la única forma
        // de distinguir "el cálculo está mal" de "el cálculo va tarde".
        androidx.compose.runtime.LaunchedEffect(centerIndex) {
            val vis = listState.layoutInfo.visibleItemsInfo.map { i ->
                val mid = i.offset + i.size / 2f
                val s = 1f + (1f - min(abs(mid - viewportCenterPx) / falloffPx, 1f)) * ScaleDelta
                "[${i.index}] mid=${mid.toInt()} d=${(mid - viewportCenterPx).toInt()} scale=${"%.2f".format(s)}"
            }
            android.util.Log.d(
                "ExploreCarousel",
                "centro=$centerIndex viewportCenter=${viewportCenterPx.toInt()} falloff=${falloffPx.toInt()} fotos=${photos.size} | ${vis.joinToString(" ")}"
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            contentPadding = PaddingValues(horizontal = sideInset),
            // El snap deja siempre una card centrada — sin él el carrusel para
            // a mitad de camino y ninguna es "la del medio", que es justo lo
            // que el zIndex y el tap necesitan saber.
            flingBehavior = rememberSnapFlingBehavior(listState),
            userScrollEnabled = !isSkeleton,
            // Aire vertical para que la card escalada no se recorte contra el
            // borde de la fila: scale no altera el layout, así que la fila
            // sigue midiendo CardHeight y la central se saldría por arriba.
            modifier = Modifier.height(CardHeight + VerticalSlack * 2),
        ) {
            itemsIndexed(photos) { index, photo ->
                ExploreCarouselCard(
                    photo = photo,
                    isSkeleton = isSkeleton,
                    modifier = Modifier
                        .width(CardWidth)
                        .height(CardHeight)
                        .zIndex(-abs(index - centerIndex).toFloat())
                        // graphicsLayer y no scale(valor): el bloque se re-evalúa
                        // cuando cambia el estado que lee, o sea en cada frame de
                        // scroll. Leyendo layoutInfo en la composición del item,
                        // la escala se calculaba con las posiciones del frame
                        // ANTERIOR —y en la primera pasada con la lista vacía—,
                        // así que la card grande no era la del centro. Es el
                        // equivalente de visualEffect en iOS: render-only.
                        .graphicsLayer {
                            val info = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                            val distance = info
                                ?.let { abs((it.offset + it.size / 2f) - viewportCenterPx) }
                                ?: falloffPx
                            val s = 1f + (1f - min(distance / falloffPx, 1f)) * ScaleDelta
                            scaleX = s
                            scaleY = s
                        }
                        .clickable(
                            enabled = !isSkeleton,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            // La card centrada ya no necesita centrarse: su tap
                            // es el que abre el lugar. Las laterales se traen al
                            // medio, para no obligar a arrastrar de a una.
                            if (index == centerIndex) onOpenPlace(photo.place)
                            else scope.launch { listState.animateScrollToItem(index) }
                        },
                )
            }
        }
    }
}

/**
 * Deja que el carrusel sangre hasta el borde de la pantalla aunque su
 * contenedor tenga padding lateral. El peek de las cards vecinas es parte del
 * efecto: con el padding del Home la del centro quedaba pegada al recorte y las
 * laterales casi no asomaban.
 *
 * Un layout modifier y no padding negativo: Compose rechaza un padding menor
 * que cero.
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

/** itemsIndexed con key estable: sin ella, reordenar cover_urls recicla las
 *  cards contra la foto equivocada. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    photos: List<ExplorePhoto>,
    content: @Composable (Int, ExplorePhoto) -> Unit,
) = items(photos.size, key = { photos[it].id }) { i -> content(i, photos[i]) }

/**
 * Foto arriba, ficha abajo — espejo de ExploreCarouselCard (iOS).
 *
 * Con el texto SOBRE la imagen hacía falta oscurecerla justo donde suele estar
 * el lugar; con la ficha aparte la foto se ve entera y el texto no depende de
 * lo que haya detrás.
 */
@Composable
private fun ExploreCarouselCard(
    photo: ExplorePhoto,
    isSkeleton: Boolean,
    modifier: Modifier = Modifier,
) {
    val place = photo.place
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, RoundedCornerShape(Radius.md)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PhotoHeight)
                .background(BuddyColor.SurfaceRaised),
        ) {
            if (photo.url != null) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(PhotoHeight),
                )
            }
            // Un lugar propuesto y aún sin aprobar. Va SOBRE la foto y no al pie
            // porque cambia cómo se lee toda la tarjeta: lo que muestra existe
            // solo para ti hasta que se apruebe, y enterarse al final sería
            // enterarse tarde. Sin icono de alerta: es un estado de espera, no
            // un problema del usuario.
            if (place.estaPendiente) {
                Text(
                    "PENDIENTE DE APROBACIÓN",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = BuddyColor.InkInverse,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BuddyColor.Ink.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            // Una línea y no un degradado: la foto termina donde termina y la
            // ficha empieza donde empieza. Es la misma línea del borde de la
            // card, así el corte se lee como parte del recuadro.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BuddyColor.Border),
            )
        }

        // Dentro de la ficha la jerarquía la hacen el color y el aire —etiqueta
        // tenue, nombre en ink, autor apagado—, sin más reglas.
        Column(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            place.category?.takeIf { it.isNotEmpty() }?.let { cat ->
                Text(
                    cat.uppercase(),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = BuddyColor.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
            }

            // Tamaños fijos y no tokens: mezclar un estilo del sistema con dos
            // textos ya escalados rompería la proporción entre los tres.
            Text(
                place.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = BuddyColor.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))

            // Quién recomienda el lugar, no cuánta gente lo conoce: la
            // recomendación de una persona concreta pesa más como prueba social
            // que un conteo, y encadena con el subtítulo de arriba.
            val autor = photo.authorName?.trim()?.takeIf { it.isNotEmpty() }
                ?.split(" ")?.firstOrNull()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (photo.authorName != null && !isSkeleton) {
                    Box(
                        Modifier.size(15.dp).clip(CircleShape).background(BuddyColor.SurfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photo.authorAvatarUrl != null) {
                            AsyncImage(
                                model = photo.authorAvatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(15.dp).clip(CircleShape),
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
                    fontSize = 9.5.sp,
                    color = BuddyColor.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
