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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
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
    onEdit: () -> Unit,
    onBuddyTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destName = journey.destination?.name ?: journey.title ?: "Trip"
    val destCity = journey.destination?.city ?: ""
    val isCompleted = journey.status == "completed"
    val pages = journey.pageThumbs.orEmpty()

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
            AsyncImage(
                model = journey.destination?.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm)),
            )
            Column(Modifier.weight(1f)) {
                Text(destName, style = BuddyType.Headline, color = BuddyColor.Ink)
                if (destCity.isNotEmpty() && destCity != destName) {
                    Text(destCity, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                }
            }
            StatusBadge(journey)
        }

        // ── Canvas de momentos ────────────────────────────────────────────
        if (pages.isEmpty()) {
            EmptyCanvas(journey, destName, enabled = !isCompleted, onTap = onEdit)
        } else {
            val pagerState = rememberPagerState(pageCount = { pages.size + if (isCompleted) 0 else 1 })
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
                if (index < pages.size) {
                    AsyncImage(
                        model = pages[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
                    )
                } else {
                    AddMomentSlide(journey, onTap = onEdit)
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

        // ── Fila del buddy (solo trip activo) ─────────────────────────────
        if (journey.status == "active") {
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
            val days = daysSince(journey.arrivalAt)
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

// ── Empty canvas — "Tu historia empieza aquí" ──────────────────────────────
@Composable
private fun EmptyCanvas(journey: ApiJourney, destName: String, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
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
    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clickable(onClick = onTap)) {
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
