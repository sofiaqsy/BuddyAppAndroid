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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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
import com.buddy.app.core.designsystem.components.BuddyCard
import com.buddy.app.core.designsystem.components.BuddyLoading
import com.buddy.app.core.designsystem.components.BuddyPrimaryButton

/**
 * Espejo de InicioView (iOS): composer "Consulta con un buddy" (6 categorías,
 * availability text del contexto de comunidad), RegisterCTACard y la sección
 * "HISTORIAS DE VIAJEROS". El flujo de matching se conecta en Fase 5.
 */
@Composable
fun InicioScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    matchingViewModel: com.buddy.app.features.matching.MatchingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val searchState by matchingViewModel.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> viewModel.onPermissionResult(grants.values.any { it }) }

    androidx.compose.runtime.LaunchedEffect(state.needsLocationPermission) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BuddyColor.Canvas)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.md))

        if (state.loadFailed) {
            RetryRow(onRetry = viewModel::load)
        }

        Column(Modifier.padding(horizontal = Spacing.edge)) {
            CategoryPicker(
                destinationName = state.destinationName,
                communityContext = state.communityContext,
                onRequest = { category ->
                    state.destinationId?.let { matchingViewModel.findBuddy(it, category) }
                },
            )
            Spacer(Modifier.height(Spacing.md))
            RegisterCtaCard(onTap = { /* registro de trip — Fase 4b */ })
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

    MatchingSheet(searchState, matchingViewModel)
}

/** Estado de la búsqueda de buddy — bottom sheet (adaptación Android del flujo modal de iOS). */
@Composable
private fun MatchingSheet(
    searchState: com.buddy.app.features.matching.MatchingViewModel.SearchState,
    viewModel: com.buddy.app.features.matching.MatchingViewModel,
) {
    if (searchState is com.buddy.app.features.matching.MatchingViewModel.SearchState.Idle) return
    com.buddy.app.core.designsystem.components.BuddySheet(
        onDismiss = {
            if (searchState is com.buddy.app.features.matching.MatchingViewModel.SearchState.Searching)
                viewModel.cancelSearch() else viewModel.dismiss()
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.edge, vertical = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (searchState) {
                is com.buddy.app.features.matching.MatchingViewModel.SearchState.Searching -> {
                    BuddyLoading(Modifier.height(60.dp))
                    Text("Buscando un buddy para ti…", style = BuddyType.Title3, color = BuddyColor.Ink)
                    Text(
                        "Te conectaremos con la primera persona disponible.",
                        style = BuddyType.Subhead, color = BuddyColor.InkMuted,
                    )
                    com.buddy.app.core.designsystem.components.BuddyTextButton(
                        "Cancelar búsqueda",
                        onClick = { viewModel.cancelSearch() },
                    )
                }
                is com.buddy.app.features.matching.MatchingViewModel.SearchState.Matched -> {
                    BuddyAvatar(imageUrl = searchState.buddy?.avatarUrl, name = searchState.buddy?.fullName, size = 64.dp)
                    Text(
                        "¡${searchState.buddy?.fullName ?: "Tu buddy"} está listo para ayudarte!",
                        style = BuddyType.Title3, color = BuddyColor.Ink,
                    )
                    BuddyPrimaryButton("Ir a la conversación", onClick = { viewModel.dismiss() /* tab Conexiones */ })
                }
                is com.buddy.app.features.matching.MatchingViewModel.SearchState.Failed -> {
                    Text(searchState.message, style = BuddyType.Subhead, color = BuddyColor.InkMuted)
                    com.buddy.app.core.designsystem.components.BuddyTextButton(
                        "Entendido",
                        onClick = { viewModel.dismiss() },
                    )
                }
                else -> {}
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ── Composer "Consulta con un buddy" ──────────────────────────────────────

private data class BuddyCategory(val icon: ImageVector, val label: String, val subtitle: String, val apiKey: String)

private val categories = listOf(
    BuddyCategory(Icons.Filled.Map, "Cómo llegar", "Rutas y transporte", "transport"),
    BuddyCategory(Icons.Filled.Coffee, "Comer", "Comida y restaurantes", "food"),
    BuddyCategory(Icons.AutoMirrored.Filled.Chat, "Traducir", "Frases, señales y más", "translation"),
    BuddyCategory(Icons.Filled.AutoAwesome, "Qué hacer", "Tours y actividades", "activities"),
    BuddyCategory(Icons.Filled.Bed, "Alojamiento", "Hoteles, hostales y más", "accommodation"),
    BuddyCategory(Icons.Filled.Shield, "Seguridad", "Emergencias y consejos útiles", "emergency"),
)

/** Texto bajo el CTA — mismas frases exactas que CategoryPickerView (iOS). */
private fun availabilityText(ctx: ApiPlaceContext?): String {
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
    onRequest: (String) -> Unit,
) {
    var selected by remember { mutableStateOf<BuddyCategory?>(null) }
    val pioneer = communityContext?.totalBuddies == 0
    val canRequest = selected != null || (!pioneer && communityContext != null && communityContext.totalBuddies == 0)

    Column {
        // Hero heading — "Consulta con un buddy" (buddy en brand)
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
            style = BuddyType.Subhead,
        )
        Spacer(Modifier.height(Spacing.md))

        // Grid 2×3 de categorías
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                row.forEach { cat ->
                    CategoryCell(
                        category = cat,
                        selected = selected == cat,
                        onTap = { selected = if (selected == cat) null else cat },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        Spacer(Modifier.height(Spacing.sm))
        BuddyPrimaryButton(
            text = "Buscar un buddy",
            onClick = { selected?.let { onRequest(it.apiKey) } },
            enabled = canRequest || selected != null,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            availabilityText(communityContext),
            style = BuddyType.Caption1,
            color = if ((communityContext?.buddies ?: 0) > 0) BuddyColor.Accent else BuddyColor.InkMuted,
            modifier = Modifier.fillMaxWidth(),
        )
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
        modifier = modifier
            .clip(shape)
            .background(if (selected) BuddyColor.SurfaceRaised else BuddyColor.Surface)
            .border(1.dp, if (selected) BuddyColor.Brand else BuddyColor.Border, shape)
            .clickable(onClick = onTap)
            .padding(horizontal = Spacing.sm + 4.dp, vertical = Spacing.sm + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
    ) {
        Box(
            Modifier.size(34.dp).background(BuddyColor.GroupedBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(category.icon, contentDescription = null, Modifier.size(16.dp), tint = BuddyColor.Brand)
        }
        Column {
            Text(category.label, style = BuddyType.FootnoteBold, color = BuddyColor.Ink, maxLines = 1)
            Text(category.subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1, fontSize = 10.sp)
        }
    }
}

// ── Register CTA ──────────────────────────────────────────────────────────

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

// ── Historias de viajeros ─────────────────────────────────────────────────

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
                StoryCard(story, Modifier.padding(horizontal = Spacing.edge))
            }
        }
    }
}

@Composable
private fun StoryCard(story: ApiJourney, modifier: Modifier = Modifier) {
    val imageUrl = story.pageThumbs?.firstOrNull() ?: story.coverUrl
    BuddyCard(modifier = modifier.fillMaxWidth(), contentPadding = 0.dp) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = story.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(320.dp),
            )
        }
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            BuddyAvatar(imageUrl = story.users?.avatarUrl, name = story.users?.fullName, size = 24.dp)
            Text(
                story.users?.fullName ?: "Viajero",
                style = BuddyType.Caption1,
                color = BuddyColor.Ink,
                modifier = Modifier.weight(1f),
            )
            if (story.destination != null) {
                Text(story.destination.name, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            }
        }
    }
}

@Composable
private fun RetryRow(onRetry: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = Spacing.edge)
            .clickable(onClick = onRetry),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.InkMuted)
        Text("No pudimos cargar. Reintentar", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}
