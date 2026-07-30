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
import androidx.compose.material.icons.filled.Person
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
import com.buddy.app.core.TravelerAlias
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
    onOpenChat: (matchId: String, initialCategory: String?) -> Unit = { _, _ -> },
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

    val isPioneerRegistering by matchingViewModel.isPioneerRegistering.collectAsState()
    val isFindingBuddy = searchState is MatchingViewModel.SearchState.Searching || isPioneerRegistering

    Column(
        modifier.fillMaxSize().background(BuddyColor.Canvas).verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.md))

        if (state.loadFailed) RetryRow(onRetry = viewModel::load)

        // Confirmación pioneer — espejo del banner pioneerConfirmation (iOS)
        val pioneerNote by matchingViewModel.pioneerConfirmation.collectAsState()
        if (pioneerNote != null) {
            Row(
                Modifier
                    .padding(horizontal = Spacing.edge)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(BuddyColor.Accent.copy(alpha = 0.10f))
                    .border(1.dp, BuddyColor.Accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md))
                    .clickable { matchingViewModel.clearPioneerConfirmation() }
                    .padding(Spacing.md),
            ) {
                Text(pioneerNote!!, style = BuddyType.Footnote, color = BuddyColor.Ink)
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        // ── Composer (con o sin trip — mismo layout, distinto destino) ─────
        // Box: el loader flota centrado sobre el composer dimmeado mientras la
        // intención se procesa (pioneer: trip + solicitud) — paridad con iOS.
        Box {
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
                    val isPioneer = state.communityContext?.totalBuddies == 0
                    val activeMatchId = state.activeMatchId
                    when {
                        // Buddy asignado: la intención va como card al chat existente
                        // (espejo de checkStatus en iOS: match activo → chat directo
                        // con chosenCategory → category_card como primer mensaje).
                        activeMatchId != null -> onOpenChat(activeMatchId, category)
                        state.activeBuddyName != null -> onOpenConexiones()
                        // Pioneer: sin buddies no hay nada que buscar — registra
                        // trip + solicitud en silencio y navega a "Tu trip" (iOS).
                        isPioneer && (state.destinationId != null || state.userLat != null) ->
                            matchingViewModel.pioneerRegister(
                                destinationId = state.destinationId,
                                lat = state.userLat, lng = state.userLng,
                                category = category,
                                cityName = state.destinationName,
                                onDone = onOpenTrips,
                            )
                        state.destinationId != null ->
                            matchingViewModel.findBuddy(state.destinationId!!, category)
                        else -> onOpenTrips()   // sin ubicación: registrar trip a mano (como iOS)
                    }
                },
            )
            Spacer(Modifier.height(Spacing.md))

            // ── Assigned buddy card (if active match) ────────────────────────
            val activeBuddy = state.activeBuddyName
            if (activeBuddy != null) {
                AssignedBuddyCard(
                    buddyName = activeBuddy,
                    buddyAvatarUrl = state.activeBuddyAvatarUrl,
                    lastMessage = state.lastBuddyMessage,
                    isLastFromMe = state.isLastMessageFromMe,
                    unreadCount = state.unreadMessageCount,
                    onTap = { state.activeMatchId?.let { onOpenChat(it, null) } },
                )
                Spacer(Modifier.height(Spacing.md))
            }

            // "¿Vas a viajar?" solo sin trip — con uno vivo, el registro ya
            // ocurrió y la card es ruido (mismo criterio en iOS).
            if (state.activeJourney == null) {
                RegisterCtaCard(onTap = onOpenTrips)
            }
        }

        // Loader — cápsula con spinner mientras se registra la solicitud
        if (isFindingBuddy) {
            Row(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(BuddyColor.Surface)
                    .border(1.dp, BuddyColor.Border, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = BuddyColor.Brand,
                    strokeWidth = 2.dp,
                )
                Text("Registrando tu solicitud…", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
            }
        }
        }

        Spacer(Modifier.height(4.dp))

        // ── Comunidad viva (recent help + pulse) ────────────────────────
        CommunityLiveSection(
            recentHelp = state.recentHelp,
            communityPulse = state.communityPulse,
            isLoading = state.isLoadingCommunity,
            formatTimeAgo = viewModel::formatTimeAgo,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )

        Spacer(Modifier.height(7.dp))
        CommunitySection(
            stories = state.stories,
            isLoading = state.isLoadingFeed,
            failed = state.feedFailed,
            onRetry = viewModel::loadFeed,
        )
        Spacer(Modifier.height(100.dp))
    }

    MatchingSheet(searchState, matchingViewModel, onOpenChat, onOpenConexiones)
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
    val noBuddies = activeBuddyName == null &&
        (communityContext?.let { it.buddies <= 0 && it.totalBuddies <= 0 } ?: true)

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
        // Tapping a category directly triggers the help request flow
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cat ->
                    CategoryCell(
                        category = cat,
                        onTap = { onRequest(cat.apiKey) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategoryCell(
    category: BuddyCategory,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .clip(shape)
            .background(BuddyColor.Surface)
            .border(1.dp, BuddyColor.Border, shape)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BuddyColor.GroupedBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                category.icon, contentDescription = null, Modifier.size(16.dp),
                tint = BuddyColor.Accent,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                category.label, style = BuddyType.FootnoteBold,
                color = BuddyColor.Ink, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(category.subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 2)
        }
    }
}

// ── Assigned Buddy Card ────────────────────────────────────────────────────
@Composable
private fun AssignedBuddyCard(
    buddyName: String,
    buddyAvatarUrl: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    lastMessage: String? = null,
    isLastFromMe: Boolean = false,
    unreadCount: Int = 0,
) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BuddyColor.Surface)
            .border(1.5.dp, BuddyColor.Brand.copy(alpha = 0.25f), shape)
            .clickable(onClick = onTap)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BuddyColor.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                if (!buddyAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = buddyAvatarUrl,
                        contentDescription = buddyName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = BuddyColor.InkMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(BuddyColor.ErrorRed, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$unreadCount",
                        style = BuddyType.Caption1.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = Color.White,
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = BuddyColor.InkMuted, fontSize = 12.sp)) {
                        append("Tu buddy asignado ")
                    }
                    withStyle(SpanStyle(color = BuddyColor.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)) {
                        append(buddyName)
                    }
                },
                maxLines = 1,
            )
            if (!lastMessage.isNullOrEmpty()) {
                Text(
                    if (isLastFromMe) "Tú: $lastMessage" else lastMessage,
                    style = BuddyType.Caption1,
                    color = BuddyColor.InkMuted,
                    maxLines = 1,
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = BuddyColor.Brand,
            modifier = Modifier.size(18.dp),
        )
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
    val authorName = TravelerAlias.displayName(story.users?.fullName, story.users?.id)

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
    onOpenChat: (matchId: String, initialCategory: String?) -> Unit,
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
                    val categoryLabel = when (searchState.category) {
                        "transport" -> "Transporte"
                        "accommodation" -> "Alojamiento"
                        "food" -> "Comida"
                        "translation" -> "Traducir"
                        "activities" -> "Qué hacer"
                        "emergency" -> "Seguridad"
                        else -> "Ayuda"
                    }
                    BuddyLoading(Modifier.height(60.dp))
                    Text("Buscando un buddy para ti…", style = BuddyType.Title3, color = BuddyColor.Ink)
                    Text("Para: $categoryLabel", style = BuddyType.Headline, color = BuddyColor.Brand, fontWeight = FontWeight.SemiBold)
                    Text("Te conectaremos con la primera persona disponible.", style = BuddyType.Subhead, color = BuddyColor.InkMuted)
                    BuddyTextButton("Cancelar búsqueda", onClick = { viewModel.cancelSearch() })
                }
                is MatchingViewModel.SearchState.Matched -> {
                    BuddyAvatar(imageUrl = searchState.buddy?.avatarUrl, name = searchState.buddy?.fullName, size = 64.dp)
                    Text(
                        "¡${TravelerAlias.displayName(searchState.buddy?.fullName, searchState.buddy?.id)} está listo para ayudarte!",
                        style = BuddyType.Title3, color = BuddyColor.Ink,
                    )
                    BuddyPrimaryButton("Ir a la conversación", onClick = {
                        // Chat directo con la card de la intención elegida (como iOS)
                        val matchId = searchState.matchId
                        val category = searchState.category
                        viewModel.dismiss()
                        onOpenChat(matchId, category)
                    })
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
