package com.buddy.app.features.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.ApiJourney
import com.buddy.app.core.data.model.ApiPlaceContext
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.Radius
import com.buddy.app.core.designsystem.Spacing
import com.buddy.app.core.designsystem.components.BuddyAvatar
import com.buddy.app.core.designsystem.components.BuddyLoading
import com.buddy.app.core.designsystem.components.BuddyPrimaryButton
import com.buddy.app.core.designsystem.components.BuddySheet
import com.buddy.app.core.designsystem.components.BuddyTextButton
import com.buddy.app.features.matching.MatchingViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Espejo 1:1 de InicioView (iOS): locationContext ("Estás en X" / activar
 * ubicación), CategoryPickerView completo (hero, grid 2×3, CTA pill oscuro
 * con availability text integrado), RegisterCTACard y HISTORIAS DE VIAJEROS
 * (carrusel con scrim + nombre + dots + footer autor/duración).
 */
@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    onOpenTrips: () -> Unit = {},
    onOpenConexiones: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    matchingViewModel: MatchingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val searchState by matchingViewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> viewModel.onPermissionResult(grants.values.any { it }) }

    LaunchedEffect(state.needsLocationPermission) {
        if (state.needsLocationPermission) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ))
        }
    }

    if (state.isLoading) {
        BuddyLoading(modifier)
        return
    }

    val isFindingBuddy = searchState is MatchingViewModel.SearchState.Searching

    Column(
        modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.md))

        if (state.loadFailed) RetryRow(onRetry = viewModel::load)

        // ── Composer (con o sin trip — mismo layout, distinto destino) ─────
        Column(Modifier.padding(horizontal = Spacing.edge).alpha(if (isFindingBuddy) 0.5f else 1f)) {
            LocationContext(
                city = state.destinationName,
                onRequestPermission = {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ))
                },
            )
            CategoryPicker(
                destinationName = state.destinationName,
                communityContext = state.communityContext,
                activeBuddyName = state.activeBuddyName,
                activeBuddyAvatarUrl = state.activeBuddyAvatarUrl,
                isLoading = isFindingBuddy,
                onRequest = { category ->
                    // Espejo de submitHelpFromHome (iOS):
                    // buddy activo → seguir la conversación; pioneer con destino →
                    // trip + solicitud y a "Tu trip"; pioneer sin destino pero con
                    // GPS → pioneerHelpFlow; sin nada → registro de trip.
                    when {
                        state.activeBuddyName != null -> onOpenConexiones()
                        state.communityContext?.totalBuddies == 0 && state.destinationId != null -> {
                            matchingViewModel.findBuddy(state.destinationId!!, category)
                            onOpenTrips()
                        }
                        state.destinationId != null ->
                            matchingViewModel.findBuddy(state.destinationId!!, category)
                        state.userLat != null && state.userLng != null -> {
                            matchingViewModel.findBuddyPioneer(state.userLat!!, state.userLng!!, category)
                            onOpenTrips()
                        }
                        else -> onOpenTrips()   // sin ubicación: registrar trip a mano (como iOS)
                    }
                },
            )
            Spacer(Modifier.height(Spacing.xs))
            RegisterCtaCard(onTap = onOpenTrips)
        }

        Spacer(Modifier.height(Spacing.xl))
        CommunitySection(
            stories = state.stories,
            isLoading = state.isLoadingFeed,
            failed = state.feedFailed,
            onRetry = viewModel::loadFeed,
        )
        Spacer(Modifier.height(100.dp))
    }

    MatchingSheet(searchState, matchingViewModel, onOpenConexiones)
}

// ── Location context — "Estás en X" / activar ubicación ───────────────────
@Composable
private fun LocationContext(city: String?, onRequestPermission: () -> Unit) {
    if (city != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, Modifier.size(11.dp), tint = BuddyColor.Brand)
            Text("Estás en $city", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Brand)
        }
    } else {
        Row(
            Modifier.clickable(onClick = onRequestPermission),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, Modifier.size(13.dp), tint = BuddyColor.Brand)
            Text(
                "Activa tu ubicación para conectarte con ayuda cerca",
                style = BuddyType.Caption1, color = BuddyColor.Brand,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                Modifier.size(10.dp), tint = BuddyColor.Brand.copy(alpha = 0.5f),
            )
        }
    }
}

// ── CategoryPicker — espejo completo de CategoryPickerView (iOS) ──────────

private data class BuddyCategory(val icon: ImageVector, val label: String, val subtitle: String, val apiKey: String)

private val categories = listOf(
    BuddyCategory(Icons.Filled.Map, "Cómo llegar", "Rutas y transporte", "transport"),
    BuddyCategory(Icons.Filled.Coffee, "Comer", "Comida y restaurantes", "food"),
    BuddyCategory(Icons.AutoMirrored.Filled.Chat, "Traducir", "Frases, señales y más", "translation"),
    BuddyCategory(Icons.Filled.AutoAwesome, "Qué hacer", "Tours y actividades", "activities"),
    BuddyCategory(Icons.Filled.Bed, "Alojamiento", "Hoteles, hostales y más", "accommodation"),
    BuddyCategory(Icons.Filled.Shield, "Seguridad", "Emergencias y consejos útiles", "emergency"),
)

/** Texto bajo el título del CTA — mismas frases exactas que iOS. */
private fun availabilityText(ctx: ApiPlaceContext?, activeBuddyName: String?): String {
    if (activeBuddyName != null) return "Tu buddy sigue disponible para ayudarte."
    if (ctx == null) return "Te conectamos con el primer buddy disponible"
    if (ctx.buddies > 0) return if (ctx.buddies == 1)
        "1 buddy disponible para ti ahora"
    else "${ctx.buddies} buddies disponibles para ti ahora"
    if (ctx.totalBuddies > 0) return if (ctx.totalBuddies == 1)
        "1 buddy ayuda en esta zona. Ahora mismo está ocupado."
    else "${ctx.totalBuddies} buddies ayudan en esta zona. Ahora mismo están ocupados."
    if (ctx.stories > 0) return if (ctx.stories == 1)
        "1 viajero ya visitó aquí. Aún buscamos buddies locales."
    else "${ctx.stories} viajeros visitaron aquí. Aún buscamos buddies locales."
    return "Todavía no hay buddies aquí. Sé el primero en explorar."
}

@Composable
private fun CategoryPicker(
    destinationName: String?,
    communityContext: ApiPlaceContext?,
    activeBuddyName: String?,
    activeBuddyAvatarUrl: String?,
    isLoading: Boolean,
    onRequest: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<BuddyCategory?>(null) }
    val noBuddies = activeBuddyName == null &&
        (communityContext?.let { it.buddies <= 0 && it.totalBuddies <= 0 } ?: true)
    // canRequest — misma regla que iOS: categoría elegida, buddy activo, o
    // pioneer (totalBuddies == 0) que no exige categoría.
    val canRequest = selected != null || activeBuddyName != null ||
        (communityContext != null && communityContext.totalBuddies == 0)

    Column {
        Spacer(Modifier.height(Spacing.md))
        // Hero heading
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Consulta con un ") }
                withStyle(SpanStyle(color = BuddyColor.Brand)) { append("buddy") }
            },
            style = BuddyType.DisplayLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append("Cuéntanos qué necesitas. Te conectaremos con un buddy") }
                if (destinationName != null) {
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" de ") }
                    withStyle(SpanStyle(color = BuddyColor.Brand, fontWeight = FontWeight.SemiBold)) { append(destinationName) }
                }
                withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(".") }
            },
            style = BuddyType.Callout,
        )
        Spacer(Modifier.height(Spacing.lg))

        // Grid 2×3 — icon square + title + subtitle (estilo exacto iOS)
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cat ->
                    CategoryCell(
                        category = cat,
                        selected = selected == cat,
                        onTap = { selected = if (selected == cat) null else cat },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(Spacing.md))

        // CTA pill oscuro — burbuja/avatar + título dinámico + flecha/spinner
        val ctaTitle = when {
            activeBuddyName != null -> "Sigue hablando con $activeBuddyName"
            noBuddies -> "Sé el primero en explorar"
            else -> "Hablar con un buddy"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(if (canRequest) BuddyColor.Brand else BuddyColor.BrandDisabled)
                .clickable(enabled = canRequest && !isLoading) {
                    val cat = selected?.apiKey ?: "general"
                    selected = null
                    onRequest(cat)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (activeBuddyAvatarUrl != null) {
                    AsyncImage(
                        model = activeBuddyAvatarUrl, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp),
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, Modifier.size(18.dp), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(ctaTitle, style = BuddyType.FootnoteBold, color = Color.White)
                Text(
                    availabilityText(communityContext, activeBuddyName),
                    style = BuddyType.Caption1, color = Color.White.copy(alpha = 0.75f),
                )
            }
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, Modifier.size(14.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: BuddyCategory,
    selected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .clip(shape)
            .background(BuddyColor.Surface)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) BuddyColor.Brand.copy(alpha = 0.4f) else BuddyColor.Border,
                shape,
            )
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) BuddyColor.Brand.copy(alpha = 0.12f) else BuddyColor.GroupedBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                category.icon, contentDescription = null, Modifier.size(16.dp),
                tint = if (selected) BuddyColor.Brand else BuddyColor.Accent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                category.label, style = BuddyType.FootnoteBold,
                color = if (selected) BuddyColor.Brand else BuddyColor.Ink, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(category.subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 2)
        }
    }
}

// ── Register CTA — "¿Vas a viajar?" ────────────────────────────────────────
@Composable
private fun RegisterCtaCard(onTap: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, shape)
            .clickable(onClick = onTap)
            .padding(horizontal = Spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            Modifier.size(40.dp).background(BuddyColor.GroupedBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.Brand)
        }
        Column(Modifier.weight(1f)) {
            Text("¿Vas a viajar?", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            Text(
                "Regístralo y prepara tu llegada para aprovechar al máximo.",
                style = BuddyType.Caption1, color = BuddyColor.InkMuted,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = BuddyColor.InkMuted.copy(alpha = 0.5f),
        )
    }
}

// ── Historias de viajeros — carrusel + scrim + dots + footer ──────────────
@Composable
private fun CommunitySection(
    stories: List<ApiJourney>,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            "HISTORIAS DE VIAJEROS",
            style = BuddyType.Eyebrow.copy(letterSpacing = 1.5.sp),
            color = BuddyColor.Ink,
            modifier = Modifier.padding(horizontal = Spacing.edge),
        )
        when {
            failed -> Column(
                Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text("No pudimos cargar la comunidad", style = BuddyType.Callout, color = BuddyColor.InkMuted)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BuddyColor.Surface)
                        .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = Spacing.lg, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Ink)
                    Text("Reintentar", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                }
            }
            isLoading && stories.isEmpty() -> BuddyLoading(Modifier.height(200.dp))
            else -> stories.forEach { story ->
                PublishedTripCard(story, Modifier.padding(horizontal = Spacing.edge))
            }
        }
    }
}

/** Espejo de PublishedTripCard (iOS): carrusel con scrim, nombre, dots, footer. */
@Composable
private fun PublishedTripCard(story: ApiJourney, modifier: Modifier = Modifier) {
    val thumbs = story.pageThumbs.orEmpty().ifEmpty { listOfNotNull(story.coverUrl) }
    val destName = story.destination?.name ?: story.title ?: ""
    val authorName = story.users?.fullName ?: "Viajero"

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(BuddyColor.Surface),
    ) {
        if (thumbs.isNotEmpty()) {
            val pagerState = rememberPagerState(pageCount = { thumbs.size })
            Box {
                HorizontalPager(state = pagerState) { page ->
                    AsyncImage(
                        model = thumbs[page],
                        contentDescription = destName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
                    )
                }
                // Scrim superior — legibilidad en fotos claras
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent))),
                )
                Text(
                    destName,
                    style = BuddyType.FootnoteBold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 14.dp, top = 12.dp),
                )
                if (thumbs.size > 1) {
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(thumbs.size) { i ->
                            Box(
                                Modifier.size(6.dp).background(
                                    if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.5f),
                                    CircleShape,
                                ),
                            )
                        }
                    }
                }
            }
        }
        // Footer minimalista: viajero + duración
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BuddyAvatar(imageUrl = story.users?.avatarUrl, name = authorName, size = 24.dp)
            Text(authorName, style = BuddyType.Footnote, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
            durationLine(story)?.let {
                Text(it, style = BuddyType.Subhead, color = BuddyColor.InkMuted)
            }
        }
    }
}

private fun durationLine(j: ApiJourney): String? {
    val a = parseDate(j.arrivalAt) ?: return null
    val d = parseDate(j.departureAt) ?: return null
    if (d < a) return null
    val days = maxOf(1, ChronoUnit.DAYS.between(a, d).toInt())
    return if (days == 1) "1 día" else "$days días"
}

private fun parseDate(iso: String?): LocalDate? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = Spacing.edge).clickable(onClick = onRetry),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.InkMuted)
        Text("No pudimos cargar. Reintentar", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}

// ── Sheet de búsqueda (searching / matched / failed) ───────────────────────
@Composable
private fun MatchingSheet(
    searchState: MatchingViewModel.SearchState,
    viewModel: MatchingViewModel,
    onOpenConexiones: () -> Unit,
) {
    if (searchState is MatchingViewModel.SearchState.Idle) return
    BuddySheet(
        onDismiss = {
            if (searchState is MatchingViewModel.SearchState.Searching) viewModel.cancelSearch()
            else viewModel.dismiss()
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (searchState) {
                is MatchingViewModel.SearchState.Searching -> {
                    BuddyLoading(Modifier.height(60.dp))
                    Text("Buscando un buddy para ti…", style = BuddyType.Title3, color = BuddyColor.Ink)
                    Text("Te conectaremos con la primera persona disponible.", style = BuddyType.Subhead, color = BuddyColor.InkMuted)
                    BuddyTextButton("Cancelar búsqueda", onClick = { viewModel.cancelSearch() })
                }
                is MatchingViewModel.SearchState.Matched -> {
                    BuddyAvatar(imageUrl = searchState.buddy?.avatarUrl, name = searchState.buddy?.fullName, size = 64.dp)
                    Text(
                        "¡${searchState.buddy?.fullName ?: "Tu buddy"} está listo para ayudarte!",
                        style = BuddyType.Title3, color = BuddyColor.Ink,
                    )
                    BuddyPrimaryButton("Ir a la conversación", onClick = { viewModel.dismiss(); onOpenConexiones() })
                }
                is MatchingViewModel.SearchState.Failed -> {
                    Text(searchState.message, style = BuddyType.Subhead, color = BuddyColor.InkMuted)
                    BuddyTextButton("Entendido", onClick = { viewModel.dismiss() })
                }
                else -> {}
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
