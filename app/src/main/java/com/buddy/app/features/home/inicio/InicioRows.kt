package com.buddy.app.features.home.inicio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.buddy.app.R
import com.buddy.app.core.data.model.ApiPlaceCard
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.features.home.data.ProductHit

private val Edge = 20.dp
private val CardCorner = 10.dp

/**
 * Inicio en filas, espejo exacto de HomeRowsView (iOS, 20 Nov 2026): portada de
 * Villa Rica con buscador, "Explora por categoría", "Cerca de ti", una fila por
 * categoría (tarjetas verticales 4:5) y "Más para descubrir". La búsqueda
 * también encuentra lugares por sus productos ("Tiene: …").
 */
@Composable
fun InicioRows(
    cards: List<ApiPlaceCard>,
    placeName: String?,
    destinationId: String?,
    onOpenPlace: (ApiPlaceCard) -> Unit,
    modifier: Modifier = Modifier,
    activeBuddyName: String? = null,
    activeBuddyHasUnread: Boolean = false,
    onOpenBuddyChat: () -> Unit = {},
    locationPrompt: (@Composable () -> Unit)? = null,
    /** Sube cada vez que se toca Inicio estando en Inicio (iOS tabReselected). */
    reselectToken: Int = 0,
    viewModel: InicioRowsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // Tiempo real hasta ver lugares (para medir el arranque).
    androidx.compose.runtime.LaunchedEffect(cards.isNotEmpty()) {
        if (cards.isNotEmpty()) com.buddy.app.core.perf.PerfLog.sinceStartOnce("inicio-con-lugares")
    }
    // Sin ubicación no hay destino resuelto: se usa el de la mayoría de los lugares (Villa Rica).
    val destinoMayoria = remember(cards) {
        cards.mapNotNull { it.destinationId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
    // Un destino sin lugares en la lista (p. ej. el de un trip viejo) no sirve de portada.
    val destinoConLugares = destinationId?.takeIf { id -> cards.isEmpty() || cards.any { it.destinationId == id } }
    val destino = destinoConLugares ?: destinoMayoria
    androidx.compose.runtime.LaunchedEffect(destino) { viewModel.loadHero(destino); viewModel.loadTouristPlaces(destino) }

    // "Ver todo" de una categoría: lista a pantalla completa (iOS HomeCategoryListView).
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }

    val local = remember(cards) { InicioRowsLogic.local(cards) }
    val nearby = remember(local) { InicioRowsLogic.nearby(local) }
    // Productos de la zona; tocar Inicio otra vez (reselectToken) los vuelve a pedir.
    androidx.compose.runtime.LaunchedEffect(reselectToken) { if (reselectToken > 0) viewModel.reshuffle() }
    androidx.compose.runtime.LaunchedEffect(local.take(20).map { it.id }, ui.reshuffleToken) {
        viewModel.loadLocalProducts(local)
    }
    val results = remember(local, ui.query, ui.productHits) {
        InicioRowsLogic.searchResults(local, ui.query, InicioRowsLogic::cardText) {
            ui.productHits.containsKey(it.id.lowercase())
        }
    }

    openCategory?.let { name ->
        BackHandler { openCategory = null }
        val list = if (name == InicioRowsLogic.NEARBY_ROUTE) local
            else emptyList()
        CategoryListScreen(name, list, onBack = { openCategory = null }, onOpenPlace = onOpenPlace, modifier = modifier)
        return
    }

    val listState = rememberLazyListState()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val heroGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 200 } }
    val focus = LocalFocusManager.current

    Box(modifier.fillMaxSize().background(BuddyColor.Canvas)) {
        LazyColumn(
            state = listState,
            // La barra de tabs de Android no flota (el Scaffold ya deja su
            // espacio): basta un margen corto al final.
            contentPadding = PaddingValues(bottom = 24.dp),
            // Más aire entre secciones (Keyla, 20 Nov 2026: "add a bit padding").
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxSize().testTag("inicioList"),
        ) {
            item(key = "hero") {
                Hero(topInset, (if (destinoConLugares != null) placeName else null) ?: ui.hero?.name ?: "Descubre", ui.hero?.slogan, ui.hero?.coverUrl,
                     ui.query, viewModel::onQueryChange, onSubmit = { focus.clearFocus() })
            }
            if (ui.query.isNotEmpty()) {
                item(key = "search") { SearchSection(ui.query, results, ui.productHits, onOpenPlace) }
            } else {
                locationPrompt?.let { p -> item(key = "prompt") { Box(Modifier.padding(horizontal = Edge)) { p() } } }
                activeBuddyName?.let { n -> item(key = "buddy") { BuddyRow(n, activeBuddyHasUnread, onOpenBuddyChat) } }
                if (cards.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                } else {
                    // Sin "Explora por categoría" ni filas por categoría (Keyla, 23 Nov 2026).
                    if (nearby.isNotEmpty()) item(key = "nearby") {
                        PlaceRow("Cerca de ti", "Lugares a pocos minutos", nearby, large = true,
                                 onSeeAll = { openCategory = InicioRowsLogic.NEARBY_ROUTE }, capsuleAction = true,
                                 onOpenPlace = onOpenPlace)
                    }
                    if (ui.localProducts.isNotEmpty()) item(key = "products") {
                        ProductsRow(ui.localProducts, ui.reshuffleToken) { onOpenPlace(it.card) }
                    }
                    // Lugares turísticos sin dueño, después de los productos (Keyla, 24 Nov 2026).
                    if (ui.touristPlaces.isNotEmpty()) item(key = "tourist") {
                        PlaceRow("Lugares turísticos", "Cataratas, miradores y naturaleza",
                                 InicioRowsLogic.rotated(ui.touristPlaces, ui.reshuffleToken), large = false,
                                 onSeeAll = null, onOpenPlace = onOpenPlace, tall = true)
                    }
                    item(key = "more") {
                        PlaceRow("Más para descubrir", "Una selección de lugares de Villa Rica",
                                 InicioRowsLogic.rotated(local, ui.reshuffleToken), large = false,
                                 onSeeAll = null, onOpenPlace = onOpenPlace, tall = true)
                    }
                }
            }
        }
        // Cuando la portada ya pasó, una franja tapa la zona de la hora.
        if (heroGone) Box(Modifier.fillMaxWidth().height(topInset).background(BuddyColor.Canvas))
    }
}

// ── Portada ─────────────────────────────────────────────────────────────────

@Composable
private fun Hero(topInset: Dp, title: String, slogan: String?, coverUrl: String?,
                 query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit) {
    val photoHeight = topInset + 190.dp
    Box(Modifier.fillMaxWidth().padding(bottom = 26.dp)) {
        Box(Modifier.fillMaxWidth().height(photoHeight).background(BuddyColor.Brand)) {
            if (coverUrl != null) {
                AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                           modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            Column(Modifier.fillMaxSize().padding(start = Edge, end = Edge, top = topInset + 4.dp, bottom = 46.dp)) {
                Image(painterResource(R.drawable.buddy_logo_white), contentDescription = "Buddy",
                      modifier = Modifier.height(26.dp))
                Spacer(Modifier.weight(1f))
                Text(title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!slogan.isNullOrBlank()) {
                    Text(slogan, color = Color.White.copy(alpha = 0.95f), fontSize = 16.sp,
                         fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        SearchField(query, onQuery, onSubmit,
                    Modifier.align(Alignment.BottomCenter).padding(horizontal = Edge).offset(y = 26.dp))
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(52.dp)
            .shadow(12.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.10f))
            .background(BuddyColor.Surface, CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = BuddyColor.InkMuted)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("¿Qué quieres descubrir?", style = BuddyType.Body, color = BuddyColor.InkFaint)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = BuddyType.Body.copy(color = BuddyColor.Ink),
                cursorBrush = SolidColor(BuddyColor.Brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().testTag("searchField")
                    .semantics { contentDescription = "¿Qué quieres descubrir?" },
            )
        }
        if (query.isNotEmpty()) {
            Icon(Icons.Filled.Cancel, contentDescription = "Borrar búsqueda", tint = BuddyColor.InkFaint,
                 modifier = Modifier.size(22.dp).clip(CircleShape).clickable { onQuery("") })
        }
    }
}

// ── Búsqueda ────────────────────────────────────────────────────────────────

@Composable
private fun SearchSection(query: String, results: List<ApiPlaceCard>, hits: Map<String, List<ProductHit>>,
                          onOpenPlace: (ApiPlaceCard) -> Unit) {
    if (results.isEmpty()) {
        Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Sin resultados para \"$query\"", style = BuddyType.Headline, color = BuddyColor.Ink)
            Text("Buscamos en lugares y en sus productos. Prueba con otra palabra, como café o miel.",
                 style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        }
        return
    }
    // Rejilla de 2 columnas (iOS LazyVGrid), arriba alineada.
    Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        results.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                pair.forEach { card ->
                    Column(Modifier.weight(1f).pressable { onOpenPlace(card) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlaceCard(card, large = false, fillWidth = true)
                        hits[card.id.lowercase()]?.firstOrNull()?.let { p ->
                            Text(InicioRowsLogic.productLine(p.name, p.price), style = BuddyType.Caption1,
                                 color = BuddyColor.Brand, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ── Secciones ───────────────────────────────────────────────────────────────

/** "Productos locales": la cara de la tarjeta de producto del panel (foto
 *  cuadrada, nombre, precio y tamaño). Tocar abre el lugar en Productos. */
@Composable
private fun ProductsRow(tiles: List<InicioRowsViewModel.LocalProductTile>, token: Int,
                        onOpen: (InicioRowsViewModel.LocalProductTile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RowHeader("Productos locales", "De los negocios de Villa Rica", categoryIcon("Productos locales"))
        // Al tocar Inicio otra vez la fila vuelve al inicio (nueva clave).
        androidx.compose.runtime.key(token) {
            LazyRow(contentPadding = PaddingValues(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top) {
                items(tiles, key = { it.id }) { t ->
                    Box(Modifier.pressable {
                            com.buddy.app.features.trips.map.ProductFocus.set(t.card.id, t.product.id, t.pres?.id)
                            onOpen(t)
                        }.semantics { contentDescription = "${t.product.name}, abre ${t.card.name}" }) {
                        com.buddy.app.features.trips.map.ProductCardFace(t.product, t.pres)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, subtitle: String?, items: List<ApiPlaceCard>, large: Boolean,
                     onSeeAll: (() -> Unit)?, onOpenPlace: (ApiPlaceCard) -> Unit,
                     icon: ImageVector? = null, capsuleAction: Boolean = false, tall: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RowHeader(title, subtitle, icon, onSeeAll, capsuleAction)
        LazyRow(contentPadding = PaddingValues(horizontal = Edge),
                horizontalArrangement = Arrangement.spacedBy(if (icon == null) 12.dp else 8.dp),
                verticalAlignment = Alignment.Top) {
            items(items, key = { it.id }) { card ->
                // En filas de categoría no se repite la categoría en cada tarjeta.
                Box(Modifier.pressable { onOpenPlace(card) }) {
                    PlaceCard(card, large = large, showsCategory = icon == null, tall = tall)
                }
            }
        }
    }
}

@Composable
private fun RowHeader(title: String, subtitle: String? = null, icon: ImageVector? = null,
                      onSeeAll: (() -> Unit)? = null, capsuleAction: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Edge), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) {
            Box(Modifier.size(40.dp).background(BuddyColor.PaleSage, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BuddyType.Title3, color = BuddyColor.Brand, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = BuddyType.Footnote, color = BuddyColor.InkMuted, maxLines = 1,
                                       overflow = TextOverflow.Ellipsis)
        }
        if (onSeeAll != null) {
            if (capsuleAction) {
                Row(Modifier.heightIn(min = 44.dp).pressable(onSeeAll), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.height(34.dp).background(BuddyColor.SageTint, CircleShape).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Ver todos", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Box(Modifier.size(44.dp).pressable(onSeeAll).semantics { contentDescription = "Ver todo: $title" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).background(BuddyColor.SageTint, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BuddyRow(name: String, unread: Boolean, onOpen: () -> Unit) {
    Row(Modifier.padding(horizontal = Edge).fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(CardCorner), ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.05f))
            .background(BuddyColor.Surface, RoundedCornerShape(CardCorner)).pressable(onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Tu buddy: $name", style = BuddyType.Headline, color = BuddyColor.Ink)
            Text(if (unread) "Tienes un mensaje nuevo" else "Abrir conversación", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
        }
        if (unread) Box(Modifier.size(8.dp).background(BuddyColor.Terracotta, CircleShape))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.InkMuted)
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Todavía no hay lugares cerca", style = BuddyType.Headline, color = BuddyColor.Ink)
        Text("Cuando los negocios y lugares de la zona estén aprobados, aparecerán aquí.",
             style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}

// ── Tarjeta ─────────────────────────────────────────────────────────────────

/** Foto primero; nombre en tinta; categoría con ícono; distancia solo en
 *  "Cerca de ti". Filas de categoría: foto vertical 4:5 de 150 dp. */
@Composable
fun PlaceCard(card: ApiPlaceCard, large: Boolean, showsCategory: Boolean = true, fillWidth: Boolean = false,
              /** Foto vertical 4:5 con categoría ("Más para descubrir", 23 Nov 2026). */
              tall: Boolean = false) {
    val portrait = tall || (!showsCategory && !large)
    val width = if (large) 264.dp else if (portrait) 150.dp else 168.dp
    val ratio = if (portrait) 4f / 5f else 4f / 3f
    val shape = RoundedCornerShape(CardCorner)
    val sizeMod = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(width)
    Column(
        sizeMod
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(shape).background(BuddyColor.Surface)
            .testTag("placeCard")
            .semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(BuddyColor.GroupedBg)) {
            AsyncImage(model = card.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                       modifier = Modifier.fillMaxSize())
            val d = InicioRowsLogic.distanceText(card.distanceMeters)
            if (large && d != null) {
                Row(Modifier.padding(10.dp).background(BuddyColor.Surface, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LocationPin(Modifier.width(11.dp).height(14.dp))
                    Text(d, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink)
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.name, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val cat = card.category
            if (showsCategory && !cat.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(categoryIcon(cat), null, tint = BuddyColor.Brand, modifier = Modifier.size(14.dp))
                    Text(cat, style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CategoryListScreen(name: String, cards: List<ApiPlaceCard>, onBack: () -> Unit,
                               onOpenPlace: (ApiPlaceCard) -> Unit, modifier: Modifier) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).padding(top = topInset)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = BuddyColor.Brand) }
        }
        Text(name, style = BuddyType.DisplayXL, color = BuddyColor.Ink, modifier = Modifier.padding(horizontal = Edge))
        LazyColumn(contentPadding = PaddingValues(start = Edge, end = Edge, top = Edge, bottom = 96.dp),
                   verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(cards.chunked(2), key = { it.first().id }) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { c -> Box(Modifier.weight(1f).pressable { onOpenPlace(c) }) { PlaceCard(c, false, fillWidth = true) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Piezas ──────────────────────────────────────────────────────────────────

@Composable
private fun CategoryBadge(category: String, diameter: Dp) {
    Box(Modifier.size(diameter).background(BuddyColor.PaleSage, CircleShape), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(category), null, tint = BuddyColor.Brand, modifier = Modifier.size(diameter * 0.5f))
    }
}

/** Un solo pin en toda la app (gota con hueco), dibujado como en iOS. */
@Composable
private fun LocationPin(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val r = w / 2
        val cy = r
        val p = Path().apply {
            fillType = PathFillType.EvenOdd
            moveTo(w / 2, h)
            cubicTo(w / 2 - w * 0.18f, h - h * 0.2f, 0f, cy + r * 0.75f, 0f, cy)
            arcTo(Rect(Offset(w / 2, cy), r), 180f, 180f, false)
            cubicTo(w, cy + r * 0.75f, w / 2 + w * 0.18f, h - h * 0.2f, w / 2, h)
            close()
            addOval(Rect(Offset(w / 2, cy), r * 0.42f))
        }
        drawPath(p, BuddyColor.Brand)
    }
}

fun categoryIcon(category: String): ImageVector = when (InicioRowsLogic.iconKey(category)) {
    "tree" -> Icons.Outlined.Park
    "flame" -> Icons.Outlined.LocalFireDepartment
    "cup" -> Icons.Outlined.Coffee
    "fork" -> Icons.Outlined.Restaurant
    "bed" -> Icons.Outlined.Bed
    "bag" -> Icons.Outlined.ShoppingBag
    "car" -> Icons.Outlined.DirectionsCar
    "hiking" -> Icons.Outlined.Hiking
    "leaf" -> Icons.Outlined.Eco
    "sun" -> Icons.Outlined.WbSunny
    "gear" -> Icons.Outlined.Settings
    "calendar" -> Icons.Outlined.CalendarMonth
    "museum" -> Icons.Outlined.Museum
    else -> Icons.Outlined.MoreHoriz
}

/** Botón que se achica un poco al tocarlo (iOS .pressable). */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    return this.scale(s).clickable(interactionSource = source, indication = null, onClick = onClick)
}

/** Esqueleto del Inicio (iOS HomeRowsSkeleton): la misma silueta que el
 *  estado cargado para que nada salte al llegar los lugares. */
@Composable
fun InicioRowsSkeleton(modifier: Modifier = Modifier) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier.fillMaxSize().background(BuddyColor.Canvas)
            .semantics { contentDescription = "Cargando lugares" }) {
        Box(Modifier.fillMaxWidth().height(topInset + 190.dp).background(BuddyColor.GroupedBg))
        Spacer(Modifier.height(52.dp))
        Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(160.dp).height(28.dp).background(BuddyColor.GroupedBg, RoundedCornerShape(6.dp)))
            Box(Modifier.width(200.dp).height(14.dp).background(BuddyColor.GroupedBg, RoundedCornerShape(4.dp)))
        }
        listOf(true, false).forEach { large ->
            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(horizontal = Edge).width(120.dp).height(18.dp).background(BuddyColor.GroupedBg, RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(Modifier.width(if (large) 264.dp else 168.dp).height(if (large) 198.dp else 126.dp)
                        .background(BuddyColor.GroupedBg, RoundedCornerShape(CardCorner)))
                }
            }
        }
    }
}
