package com.buddy.app.features.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyAvatar
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Espejo de TripFeedCard (TripsView.swift): header con cover + destino +
 * badge de estado, canvas de momentos (carrusel de page_thumbs con slide
 * "Agregar otro momento" / empty state "Tu historia empieza aquí") y la
 * fila del buddy. El editor de collage (Memoir) llega en su propia fase —
 * onEdit queda enganchado para conectarlo ahí.
 */
@Composable
fun TripFeedCard(
    journey: ApiJourney,
    buddyName: String? = null,
    buddyAvatarUrl: String? = null,
    /** Espejo de iOS onEdit(Int): -1 = nuevo momento; índice = editar esa página. */
    onEdit: (Int) -> Unit,
    onBuddyTap: () -> Unit,
    /** Tap en el logo/nombre del header → pantalla del mapa (como iOS). */
    onMapTap: (() -> Unit)? = null,
    /** Tap en "Publicar esta historia" — el gate de login vive en el caller. */
    onPublishTap: (() -> Unit)? = null,
    isPublishing: Boolean = false,
    /** Acciones al final de la cabecera (el menú ⋯ del trip). Van DENTRO de la
     *  fila, después del estado: como overlay flotante se montaban sobre
     *  "Desde hoy". */
    headerTrailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val destName = journey.destination?.name ?: journey.title ?: "Trip"
    val destCity = journey.destination?.city ?: ""
    val isCompleted = journey.status == "completed"

    // Portadas LOCALES del memoir — espejo de loadPages() + .memoirPageSaved
    // (iOS): la card muestra los thumbnails generados al guardar el editor,
    // no las URLs del backend (esas solo existen tras publicar).
    val context = androidx.compose.ui.platform.LocalContext.current
    val persistence = remember {
        com.buddy.app.features.trips.memoir.MemoirPersistence(context.applicationContext)
    }
    var pages by remember(journey.id) {
        mutableStateOf<List<com.buddy.app.features.trips.memoir.CollagePage>>(emptyList())
    }
    androidx.compose.runtime.LaunchedEffect(journey.id, com.buddy.app.features.trips.memoir.MemoirEvents.saveTick) {
        pages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            persistence.load(journey.id)
        }
    }
    // Eliminar portada — espejo de deleteTarget + deletePage(at:) en iOS
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var deleteTarget by remember { mutableStateOf<Int?>(null) }
    fun deletePageAt(index: Int) {
        if (index !in pages.indices) return
        val updated = pages.toMutableList().apply { removeAt(index) }
        pages = updated
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            persistence.save(updated, journey.id)
        }
    }
    // Confirmación "¿Eliminar esta portada?" — misma copy que iOS
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = BuddyColor.Surface,
            title = { Text("¿Eliminar esta portada?", style = BuddyType.Headline, color = BuddyColor.Ink) },
            text = {
                Text(
                    "Las fotos de esta portada se quitarán del trip.",
                    style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { deletePageAt(target); deleteTarget = null }) {
                    Text("Eliminar portada", color = BuddyColor.ErrorRed, style = BuddyType.FootnoteBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancelar", color = BuddyColor.Brand, style = BuddyType.FootnoteBold)
                }
            },
        )
    }
    var showComoLlegar by remember { mutableStateOf(false) }
    val destLat = journey.destination?.lat
    val destLng = journey.destination?.lng

    if (showComoLlegar && destLat != null && destLng != null) {
        com.buddy.app.core.navigation.ComoLlegarDialog(
            placeName = destName,
            lat = destLat,
            lng = destLng,
            onDismiss = { showComoLlegar = false },
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(BuddyColor.Surface),
    ) {
        // ── Header: cover + nombre + ciudad + badge ──────────────────────
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Logo/nombre → mapa (como el Button(onMapTap) de iOS); sin cover
            // del lugar cae al logo de la app (Image("AppIconImage") en iOS).
            val headerTap = if (onMapTap != null) {
                Modifier.clickable(onClick = onMapTap)
            } else Modifier
            if (journey.destination?.coverUrl.isNullOrEmpty()) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.buddy.app.R.drawable.splash_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(BuddyColor.SurfaceRaised)
                        .then(headerTap),
                )
            } else {
                AsyncImage(
                    model = journey.destination?.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = androidx.compose.ui.res.painterResource(com.buddy.app.R.drawable.splash_logo),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .then(headerTap),
                )
            }
            Column(Modifier.weight(1f).then(headerTap)) {
                Text(destName, style = BuddyType.Headline, color = BuddyColor.Ink)
                if (destCity.isNotEmpty() && destCity != destName) {
                    Text(destCity, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                }
            }
            // Cómo llegar — abre Google Maps / Waze (espejo del botón de iOS)
            if (destLat != null && destLng != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BuddyColor.GroupedBg)
                        .clickable { showComoLlegar = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = "Cómo llegar a $destName",
                        Modifier.size(16.dp),
                        tint = BuddyColor.Brand,
                    )
                }
            }
            StatusBadge(journey)
            headerTrailing?.invoke()
        }

        // ── Canvas de momentos ────────────────────────────────────────────
        if (pages.isEmpty()) {
            EmptyCanvas(journey, destName, enabled = !isCompleted, onTap = { onEdit(-1) })
        } else {
            val pagerState = rememberPagerState(pageCount = { pages.size + if (isCompleted) 0 else 1 })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
                if (index < pages.size) {
                    LocalPageSlide(
                        page = pages[index],
                        journeyId = journey.id,
                        journey = journey,
                        persistence = persistence,
                        enabled = !isCompleted,
                        canDelete = pages.size > 1,
                        onTap = { onEdit(index) },
                        onNewMoment = { onEdit(-1) },
                        onDeleteRequest = { deleteTarget = index },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(canvasAspect()),
                    )
                } else {
                    AddMomentSlide(journey, onTap = { onEdit(-1) })
                }
            }
            // Page dots
            Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pagerState.pageCount) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .background(
                                if (i == pagerState.currentPage) BuddyColor.Brand else BuddyColor.InkFaint,
                                CircleShape,
                            ),
                    )
                }
            }
        }

        // ── Publicar historia — acción primaria cuando hay contenido listo
        // (espejo de "Publicar esta historia" en TripFeedCard de iOS)
        val hasPublishableContent = pages.any { it.itemSnapshots.isNotEmpty() || it.backgroundImageFile != null }
        if (journey.status == "active" && hasPublishableContent && onPublishTap != null) {
            HorizontalDivider(color = BuddyColor.Border, modifier = Modifier.padding(start = Spacing.md))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPublishing, onClick = onPublishTap)
                    .padding(horizontal = Spacing.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isPublishing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            Modifier.size(16.dp), color = BuddyColor.Brand, strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Publicar esta historia", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Check, contentDescription = null,
                            Modifier.size(10.dp), tint = BuddyColor.Brand,
                        )
                        Text("Tu historia está lista", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = BuddyColor.InkMuted.copy(alpha = 0.5f),
                )
            }
        }

        // ── Fila del buddy — SOLO con buddy asignado en trip activo ───────
        // Sin match no hay a quién preguntarle la duda: la fila se oculta.
        if (journey.status == "active" && buddyName != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBuddyTap)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                BuddyAvatar(imageUrl = buddyAvatarUrl, name = buddyName ?: "B", size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text("¿Una duda en $destName?", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                    Text(
                        buddyName?.let { "$it sigue disponible" } ?: "Tu buddy sigue disponible",
                        style = BuddyType.Caption1,
                        color = BuddyColor.InkMuted,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = BuddyColor.InkMuted.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Badge de estado — misma lógica y copy que statusBadge (iOS) ───────────
@Composable
private fun StatusBadge(journey: ApiJourney) {
    val (label, color) = when (journey.status) {
        "active" -> {
            // Sin arrival_at (trips creados por matching/pioneer) el badge caía
            // siempre en "Desde hoy" — usar created_at como respaldo real.
            val days = daysSince(journey.arrivalAt ?: journey.createdAt)
            val text = when {
                days == null || days <= 0 -> "Desde hoy"
                days == 1 -> "Desde hace 1 día"
                else -> "Desde hace $days días"
            }
            text to BuddyColor.Brand
        }
        "completed" -> "COMPLETADO" to BuddyColor.Accent
        else -> {
            val days = daysUntil(journey.arrivalAt)
            val text = when {
                days == null -> "POR LLEGAR"
                days == 0 -> "Llega hoy"
                days == 1 -> "Llega mañana"
                else -> "En $days días"
            }
            text to BuddyColor.InkMuted
        }
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, style = BuddyType.Eyebrow, color = color)
    }
}

/**
 * Proporción del preview de portada = la del canvas del editor — espejo de
 * previewHeight en TripsView.swift: el preview muestra EXACTAMENTE lo que se
 * editó, sin recorte ni barras. El canvas es retrato (máx 390dp × 480, como
 * iPhone), así que el preview siempre es más alto que ancho.
 */
@Composable
private fun canvasAspect(): Float =
    com.buddy.app.features.trips.memoir.MemoirPage.aspect(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat(),
    )

/**
 * Slide de una portada local — espejo de TripPageThumbnailFeed (iOS):
 * thumbnail SOLO si la página tiene contenido real; si no, fallback al
 * cover del destino. Long-press → menú contextual como iOS:
 * "Nuevo momento" y "Eliminar momento" (solo con más de una portada).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalPageSlide(
    page: com.buddy.app.features.trips.memoir.CollagePage,
    journeyId: String,
    journey: ApiJourney,
    persistence: com.buddy.app.features.trips.memoir.MemoirPersistence,
    enabled: Boolean,
    canDelete: Boolean,
    onTap: () -> Unit,
    onNewMoment: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val thumbnail by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        null, page.id, page.editVersion,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (page.itemSnapshots.isNotEmpty()) {
                page.thumbnailFileName?.let { persistence.loadThumbnail(it, journeyId) }
            } else null
        }
    }
    Box(
        modifier.combinedClickable(
            enabled = enabled,
            onClick = onTap,
            onLongClick = { showMenu = true },
        ),
    ) {
        val thumb = thumbnail
        if (thumb != null) {
            androidx.compose.foundation.Image(
                bitmap = thumb.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverBackground(journey)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        }
        // Menú contextual — espejo del .contextMenu de iOS
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Nuevo momento", style = BuddyType.Body, color = BuddyColor.Ink) },
                onClick = { showMenu = false; onNewMoment() },
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Eliminar momento", style = BuddyType.Body, color = BuddyColor.ErrorRed) },
                    onClick = { showMenu = false; onDeleteRequest() },
                )
            }
        }
    }
}

// ── Empty canvas — "Tu historia empieza aquí" ──────────────────────────────
@Composable
private fun EmptyCanvas(journey: ApiJourney, destName: String, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(canvasAspect())
            .clickable(enabled = enabled, onClick = onTap),
    ) {
        CoverBackground(journey)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Column(
            Modifier.align(Alignment.Center).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            DashedPlusCircle(64.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tu historia empieza aquí", style = BuddyType.Headline, color = Color.White)
                Text(
                    "Guarda los momentos mientras vives $destName",
                    style = BuddyType.Footnote,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Slide final "Agregar otro momento" ─────────────────────────────────────
@Composable
private fun AddMomentSlide(journey: ApiJourney, onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(canvasAspect()).clickable(onClick = onTap)) {
        CoverBackground(journey)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DashedPlusCircle(56.dp)
            Text("Agregar otro momento", style = BuddyType.FootnoteBold, color = Color.White)
        }
    }
}

@Composable
private fun CoverBackground(journey: ApiJourney) {
    if (journey.destination?.coverUrl != null) {
        AsyncImage(
            model = journey.destination.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(BuddyColor.BrandGradientDark, BuddyColor.Brand),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        )
    }
}

/** Círculo punteado con "+" — mismo motivo visual del canvas iOS. */
@Composable
private fun DashedPlusCircle(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

private fun daysSince(iso: String?): Int? = parseDate(iso)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() }
private fun daysUntil(iso: String?): Int? = parseDate(iso)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
private fun parseDate(iso: String?): LocalDate? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
}
