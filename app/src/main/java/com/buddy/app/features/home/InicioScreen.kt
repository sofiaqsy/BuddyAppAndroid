package com.buddy.app.features.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import com.buddy.app.core.data.model.ApiPlaceCard
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
import com.buddy.app.features.messages.ChatScreen
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
    /** Abre el lugar del carrusel en el mapa de su destino. Lo resuelve quien
     *  contiene la pantalla: el Home no conoce rutas, igual que en iOS. */
    onOpenPlace: (ApiPlaceCard) -> Unit = {},
    /** Abre el perfil de una persona: quien ayudó en Comunidad viva, o quien
     *  publicó una historia. */
    onOpenProfile: (travelerId: String, name: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
    /** "Consultar en X" — abre la conversación a pantalla completa. La monta
     *  quien contiene esta pantalla, fuera del Scaffold: dentro quedaría la
     *  barra de tabs asomando y no se leería como el chat que es. */
    onStartConversation: () -> Unit = {},
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

    // La lógica de "pedir ayuda" (pioneer, findBuddy, buddy ya asignado) se
    // mudó a ConversacionPendienteHost: ahora la dispara el tema que se elige
    // DENTRO de la conversación, y esa vive fuera de esta pantalla.

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
            val effectiveContext = state.effectiveHomeContext
            // El trip seleccionado ES el que tiene el match activo — con 2+
            // trips vivos, uno puede no tener buddy asignado (ej: Villa Rica
            // en "planning" mientras San Francisco tiene el match).
            val selectedIsActiveTrip = effectiveContext is HomeContext.Trip &&
                effectiveContext.journeyId == state.activeJourney?.id
            // true cuando algo se pinta ARRIBA de CategoryPicker (el selector,
            // o LocationContext en Case 4). CategoryPicker ya trae su propio
            // Spacer(Spacing.md) antes del heading — sin esta bandera, el
            // Spacer(Spacing.md) de arriba de la pantalla se sumaba a ese
            // incluso sin nada que separar (Case 3), dejando un espacio doble
            // e injustificado encima de "Consulta con un buddy".
            val hasHeaderRow = (effectiveContext != null && state.homeContextOptionCount > 1) || effectiveContext == null
            if (effectiveContext != null && state.homeContextOptionCount > 1) {
                // 2+ opciones distintas (Ubicación actual + uno o más trips):
                // selector interactivo. "Ubicación actual" se omite si coincide
                // con alguno de los trips (matchingTripForGPS) — esa fila ya
                // cubre ambas cosas, no se repite.
                HomeContextSelector(
                    context = effectiveContext,
                    hasCurrentLocation = state.shouldOfferCurrentLocationOption,
                    currentLocationCity = state.gpsDestinationName,
                    trips = state.liveJourneys.map { HomeContextTripOption(it.id, it.destination?.name ?: "Mi viaje") },
                    onSelect = viewModel::setHomeContext,
                )
                Spacer(Modifier.height(Spacing.xs))
            } else if (effectiveContext == null) {
                // Case 4: ni GPS ni trip — flujo de permisos/registro existente.
                LocationContext(
                    city = state.destinationName,
                    onRequestPermission = {
                        permissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ))
                    },
                )
            }
            CategoryPicker(
                destinationName = state.destinationName,
                communityContext = state.communityContext,
                activeBuddyName = if (selectedIsActiveTrip) state.activeBuddyName else null,
                activeBuddyAvatarUrl = if (selectedIsActiveTrip) state.activeBuddyAvatarUrl else null,
                // "Tú:" cuando el último mensaje es mío — misma convención que
                // WhatsApp y que la lista de Conexiones. Sin él, leer "Cafetería
                // Rosal" da a entender que lo mandó el buddy.
                activeBuddySubtitle = if (selectedIsActiveTrip) {
                    state.lastBuddyMessage?.let {
                        if (state.isLastMessageFromMe) "Tú: $it" else it
                    }
                } else null,
                activeBuddyHasUnread = selectedIsActiveTrip && state.unreadMessageCount > 0,
                exploreCards = state.exploreCards,
                isLoadingExplore = state.isLoadingExplore,
                searchingCategoryKey = state.openRequestCategory,
                isLoading = isFindingBuddy,
                topSpacing = hasHeaderRow,
                onOpenPlace = onOpenPlace,
                onOpenBuddyChat = {
                    // Con buddy asignado el CTA no empieza nada nuevo: retoma el
                    // hilo que ya existe.
                    state.activeMatchId?.let { onOpenChat(it, null) } ?: onOpenConexiones()
                },
                onStartConversation = onStartConversation,
            )

            // "Consultar en X" abre la conversación a PANTALLA COMPLETA — la
            // monta BuddyRoot fuera del Scaffold, como el chat. Una hoja modal
            // dejaba la barra de tabs asomando y se leía como algo encima del
            // Home en vez de como la conversación en la que estás.
            Spacer(Modifier.height(Spacing.md))

            // Aquí iba una segunda card de "buddy asignado". Se quitó: el CTA
            // de arriba ya ES esa card cuando hay match — avatar, nombre,
            // último mensaje y punto de no leídos—, así que la pantalla decía
            // dos veces lo mismo, una debajo de la otra.
            //
            // Ese es el sentido de que el CTA tenga una sola forma para los
            // tres momentos: no lo reemplaza otra cosa cuando aparece el buddy,
            // se va llenando. iOS quitó su equivalente por lo mismo.

            // Aquí iba "¿Vas a viajar?". Retirada: iOS ya la oculta en todos
            // los casos. El Home pide una cosa —consultar con un buddy— y una
            // segunda invitación a registrar un viaje competía con ella justo
            // debajo, en el momento en que el usuario ya decidió qué hacer.
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
            communityPulse = state.communityPulse,
            isLoading = state.isLoadingCommunity,
            formatTimeAgo = viewModel::formatTimeAgo,
            // Reutiliza la misma vía que el carrusel: quien contiene la
            // pantalla sabe abrir el mapa de un destino, el Home no.
            onOpenDestination = { destinationId, nombre ->
                onOpenPlace(ApiPlaceCard(id = destinationId, name = nombre,
                                         destinationId = destinationId,
                                         destinationName = nombre))
            },
            onOpenProfile = onOpenProfile,
            modifier = Modifier.padding(bottom = Spacing.lg),
        )

        Spacer(Modifier.height(7.dp))
        CommunitySection(
            stories = state.stories,
            isLoading = state.isLoadingFeed,
            failed = state.feedFailed,
            onRetry = viewModel::loadFeed,
            onOpenProfile = onOpenProfile,
            onOpenDestination = { destinationId, nombre ->
                onOpenPlace(ApiPlaceCard(id = destinationId, name = nombre,
                                         destinationId = destinationId,
                                         destinationName = nombre))
            },
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

/** Una fila seleccionable del dropdown: un trip vivo (journey.id + nombre a mostrar). */
private data class HomeContextTripOption(val id: String, val name: String)

// ── Home context selector — "Ubicación actual" vs Mi(s) viaje(s) ──────────
// Interactivo solo cuando hay 2+ opciones distintas (Ubicación actual + uno o
// más trips) — con una sola opción se muestra como fila fija, sin affordance
// de tap. Con 2+ trips vivos, cada uno aparece como su propia fila: no hay un
// solo "Mi viaje" genérico si el viajero tiene más de un trip. Espejo de
// HomeContextSelector (iOS).
@Composable
private fun HomeContextSelector(
    context: HomeContext,
    hasCurrentLocation: Boolean,
    currentLocationCity: String?,
    trips: List<HomeContextTripOption>,
    onSelect: (HomeContext) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactive = (if (hasCurrentLocation) 1 else 0) + trips.size > 1
    val icon = if (context is HomeContext.CurrentLocation) Icons.Filled.LocationOn else Icons.Filled.Map
    val label = when (context) {
        is HomeContext.CurrentLocation -> currentLocationCity.takeUnless { it.isNullOrEmpty() } ?: "Ubicación actual"
        is HomeContext.Trip -> trips.firstOrNull { it.id == context.journeyId }?.name ?: "Mi trip"
    }

    Box {
        Row(
            modifier = if (interactive) Modifier.clickable { expanded = true } else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(12.dp), tint = BuddyColor.Brand)
            Text(
                label,
                style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold),
                color = BuddyColor.Brand,
                maxLines = 1,
                softWrap = false,
            )
            if (interactive) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.InkMuted)
            }
        }
        if (interactive) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (hasCurrentLocation) {
                    DropdownMenuItem(
                        text = {
                            HomeContextOptionRow(
                                title = currentLocationCity.takeUnless { it.isNullOrEmpty() } ?: "Ubicación actual",
                                subtitle = "Ubicación actual",
                                checked = context is HomeContext.CurrentLocation,
                            )
                        },
                        onClick = { onSelect(HomeContext.CurrentLocation); expanded = false },
                    )
                }
                trips.forEachIndexed { index, trip ->
                    if (hasCurrentLocation || index > 0) HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            HomeContextOptionRow(
                                title = trip.name,
                                subtitle = "Mi trip",
                                checked = context is HomeContext.Trip && context.journeyId == trip.id,
                            )
                        },
                        onClick = { onSelect(HomeContext.Trip(trip.id)); expanded = false },
                    )
                }
            }
        }
    }
}

/** El nombre real del lugar (trip o ubicación) va como texto principal —
 * "Mi viaje"/"Ubicación actual" queda de subtítulo, no al revés. */
@Composable
private fun HomeContextOptionRow(title: String, subtitle: String, checked: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(14.dp), tint = BuddyColor.Brand)
            Text(title, style = BuddyType.Body)
        }
        Text(subtitle, style = BuddyType.Caption1, color = BuddyColor.InkMuted)
    }
}

// ── CategoryPicker — espejo completo de CategoryPickerView (iOS) ──────────

private data class BuddyCategory(val icon: ImageVector, val label: String, val subtitle: String, val apiKey: String)

private val categories = listOf(
    BuddyCategory(Icons.Filled.DirectionsCar, "Transporte", "Rutas y movilidad", "transport"),
    BuddyCategory(Icons.Filled.Coffee, "Comer", "Restaurantes y sabores locales", "food"),
    BuddyCategory(Icons.Filled.ShoppingBag, "Compras", "Productos locales", "shopping"),
    BuddyCategory(Icons.Filled.Hiking, "Actividades", "Tours y experiencias", "activities"),
    BuddyCategory(Icons.Filled.Bed, "Alojamiento", "Hoteles y hospedajes", "accommodation"),
    BuddyCategory(Icons.Filled.Lightbulb, "Consejos", "Recomendaciones", "recommendations"),
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

/**
 * El composer del Home — espejo de CategoryPickerView con hidesCategoryGrid
 * (iOS).
 *
 * La grilla de 6 categorías dejó de ser un componente de Home: ahí las
 * intenciones ya no se eligen primero, nacen dentro de la conversación. En su
 * lugar va el carrusel de lugares que recomiendan los buddies y, debajo, el CTA
 * que abre el hilo. Si todavía no hay fotos para este lugar, solo el CTA —
 * nunca la grilla.
 */
@Composable
private fun CategoryPicker(
    destinationName: String?,
    communityContext: ApiPlaceContext?,
    activeBuddyName: String?,
    activeBuddyAvatarUrl: String?,
    activeBuddySubtitle: String?,
    activeBuddyHasUnread: Boolean,
    exploreCards: List<ApiPlaceCard>,
    isLoadingExplore: Boolean,
    searchingCategoryKey: String?,
    isLoading: Boolean,
    /** false cuando ya hay algo pintado arriba (selector/LocationContext) —
     * evita sumar este Spacer al Spacer de arriba de la pantalla y dejar un
     * espacio doble encima del heading. */
    topSpacing: Boolean = true,
    onOpenPlace: (ApiPlaceCard) -> Unit,
    onOpenBuddyChat: () -> Unit,
    onStartConversation: () -> Unit,
) {
    val noBuddies = activeBuddyName == null &&
        (communityContext?.let { it.buddies <= 0 && it.totalBuddies <= 0 } ?: true)

    // El esqueleto cuenta como carrusel: sin esto la línea de disponibilidad se
    // dibuja ARRIBA (su lugar cuando no hay fotos) y empuja las cards hacia
    // abajo, así que al cargar todo el bloque salta. Lo que se promete y lo que
    // llega deben ocupar el mismo espacio.
    val showsCarousel = exploreCards.isNotEmpty() || isLoadingExplore

    Column {
        if (topSpacing) Spacer(Modifier.height(Spacing.md))
        // Hero heading
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = BuddyColor.Ink)) { append("Consulta con un ") }
                withStyle(SpanStyle(color = BuddyColor.Brand)) { append("buddy") }
            },
            style = BuddyType.DisplayLarge,
        )
        Spacer(Modifier.height(6.dp))
        // Con carrusel el subtítulo DESCRIBE lo que se ve; sin él sigue mandando
        // a elegir un tema. El viejo ("Elige el tema de tu consulta") venía del
        // flujo de 6 categorías: sobre el carrusel mandaba a elegir y lo único
        // elegible a la vista eran las fotos.
        Text(
            buildAnnotatedString {
                if (showsCarousel) {
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) {
                        append("Lugares que recomiendan los buddies")
                    }
                    if (destinationName != null) {
                        withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" de ") }
                        withStyle(SpanStyle(color = BuddyColor.Brand, fontWeight = FontWeight.SemiBold)) {
                            append(destinationName)
                        }
                    } else {
                        withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" por acá") }
                    }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(".") }
                } else {
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append("Elige el tema de tu consulta. Te conectaremos con una persona que conozca") }
                    if (destinationName != null) {
                        withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" ") }
                        withStyle(SpanStyle(color = BuddyColor.Brand, fontWeight = FontWeight.SemiBold)) { append(destinationName) }
                    } else {
                        withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(" el lugar") }
                    }
                    withStyle(SpanStyle(color = BuddyColor.InkMuted)) { append(".") }
                }
            },
            style = BuddyType.Callout,
        )

        // Disponibilidad de la comunidad. Con carrusel va DEBAJO de las fotos
        // (ver más abajo): ahí deja de ser una estadística suelta y pasa a
        // explicar qué son esas fotos y por qué llevan al botón.
        if (activeBuddyName == null && !showsCarousel) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (noBuddies) BuddyColor.InkFaint else BuddyColor.Accent),
                )
                Text(
                    availabilityText(communityContext, activeBuddyName),
                    style = BuddyType.Caption1,
                    color = BuddyColor.InkMuted,
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))

        if (showsCarousel) {
            // Sangra hasta el borde de la pantalla, como en iOS: el peek
            // lateral de las cards vecinas es parte del efecto.
            ExploreCarousel(
                cards = exploreCards,
                isSkeleton = exploreCards.isEmpty() && isLoadingExplore,
                onOpenPlace = onOpenPlace,
                modifier = Modifier.sangraLateral(Spacing.edge),
            )
            Spacer(Modifier.height(6.dp))
            // La bisagra entre las fotos y el CTA: nombra la ciudad y la
            // disponibilidad en la misma frase, para encadenar lugar → persona
            // → consulta.
            Text(
                exploreAvailabilityText(communityContext, destinationName),
                style = BuddyType.Caption1,
                color = BuddyColor.InkMuted,
            )
            Spacer(Modifier.height(16.dp))
        }

        ConsultCta(
            destinationName = destinationName,
            activeBuddyName = activeBuddyName,
            activeBuddyAvatarUrl = activeBuddyAvatarUrl,
            activeBuddySubtitle = activeBuddySubtitle,
            activeBuddyHasUnread = activeBuddyHasUnread,
            searchingCategoryKey = searchingCategoryKey,
            onOpenBuddyChat = onOpenBuddyChat,
            onStartConversation = onStartConversation,
        )
    }
}

/** Espejo de exploreAvailabilityText (iOS). */
private fun exploreAvailabilityText(ctx: ApiPlaceContext?, destinationName: String?): String {
    val city = destinationName ?: "este lugar"
    val n = ctx?.buddies ?: 0
    if (n <= 0) return "Buscando buddies que conozcan $city"
    return if (n == 1) "1 buddy conoce $city y está disponible ahora"
    else "$n buddies conocen $city y están disponibles ahora"
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
private fun CommunitySection(
    stories: List<ApiJourney>,
    isLoading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onOpenProfile: (travelerId: String, name: String?, avatarUrl: String?) -> Unit,
    onOpenDestination: (destinationId: String, name: String) -> Unit,
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
                PublishedTripCard(
                    story,
                    onOpenProfile = onOpenProfile,
                    onOpenDestination = onOpenDestination,
                    modifier = Modifier.padding(horizontal = Spacing.edge),
                )
            }
        }
    }
}

/** Espejo de PublishedTripCard (iOS): carrusel con scrim, nombre, dots, footer. */
@Composable
private fun PublishedTripCard(
    story: ApiJourney,
    /** La cara y el nombre llevan al perfil del autor; el resto del pie sigue
     *  abriendo la historia. Mismo reparto que en Comunidad viva: quien toca a
     *  una persona quiere ver a esa persona. */
    onOpenProfile: (travelerId: String, name: String?, avatarUrl: String?) -> Unit = { _, _, _ -> },
    /** El nombre del destino abre su mapa — mismo gesto que en Comunidad viva.
     *  Nil-safe: sin destination_id no hay guía que abrir y no hay gesto. */
    onOpenDestination: (destinationId: String, name: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
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
                val destinoId = story.destination?.id ?: story.destinationId
                Text(
                    destName,
                    style = BuddyType.FootnoteBold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(start = 14.dp, top = 12.dp)
                        .then(
                            if (destinoId != null) {
                                Modifier.clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onOpenDestination(destinoId, destName) }
                            } else Modifier,
                        ),
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
            val autorId = story.users?.id
            val gestoAutor = if (autorId != null) {
                Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onOpenProfile(autorId, story.users?.fullName, story.users?.avatarUrl) }
            } else Modifier
            Box(gestoAutor) {
                BuddyAvatar(imageUrl = story.users?.avatarUrl, name = authorName, size = 24.dp)
            }
            Text(
                authorName, style = BuddyType.Footnote, color = BuddyColor.Ink,
                modifier = Modifier.weight(1f).then(gestoAutor),
            )
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
                        "food" -> "Comer"
                        "shopping" -> "Compras"
                        "translation" -> "Traducir"
                        "activities" -> "Actividades"
                        "emergency" -> "Seguridad"
                        "recommendations" -> "Consejos"
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
