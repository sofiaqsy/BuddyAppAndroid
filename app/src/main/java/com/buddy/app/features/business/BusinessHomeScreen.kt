package com.buddy.app.features.business

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.FincasProduct
import com.buddy.app.core.data.model.soles
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Negocio (iOS BusinessHomeView, Android fase 2, 24 Nov 2026). */
@HiltViewModel
class BusinessHomeViewModel @Inject constructor(
    private val api: BusinessApi,
    private val mapApi: com.buddy.app.features.trips.map.MapApi,
    private val homeApi: com.buddy.app.features.home.data.HomeApi,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {
    data class State(
        val loading: Boolean = true,
        val products: List<FincasProduct> = emptyList(),
        val productsError: String? = null,
        val spots: List<FarmSpot> = emptyList(),
        val categories: FarmCategories? = null,
        /** Catálogo de categorías: el negocio guarda ids; se muestran nombres. */
        val catalog: List<CatalogCategory> = emptyList(),
        val savingCategories: Boolean = false,
        val profileError: String? = null,
        val profileLoading: Boolean = true,
        /** spotId -> portada (la primera foto del dueño). */
        val covers: Map<String, String> = emptyMap(),
        /** spotId -> destino, para abrir el lugar en su mapa. */
        val destinations: Map<String, String> = emptyMap(),
    )
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var loadedFor: String? = null

    /** Categoría principal y hasta 2 más, en todos los lugares del negocio (iOS). */
    fun saveCats(farmId: String, main: String, secondary: List<String>) {
        _state.update { it.copy(savingCategories = true) }
        viewModelScope.launch {
            val r = runCatching { api.setFarmCategories(farmId, FarmCategoriesBody(main, secondary.take(2))) }
            _state.update { it.copy(savingCategories = false,
                categories = if (r.isSuccess) FarmCategories(main, secondary.take(2)) else it.categories,
                profileError = r.exceptionOrNull()?.let { "No pudimos guardar la categoría." } ?: it.profileError) }
        }
    }

    val actionError = MutableStateFlow<String?>(null)

    /** Sede nueva: un lugar de Buddy que es suyo, o uno nuevo propuesto aquí (iOS BusinessLocationPicker). */
    fun addLocation(farmId: String, spotId: String?, name: String, lat: Double, lng: Double, done: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                val id = spotId ?: homeApi.proposeSpot(com.buddy.app.features.home.data.ProposeSpotBody(name, lat, lng)).id
                api.claim(id, ClaimBody(farmId))
            }.onSuccess { done(); load(farmId, force = true) }
             .onFailure { actionError.value = "No pudimos guardar la ubicación. Si ya es de otro negocio, escríbenos." }
        }
    }

    fun removeLocation(farmId: String, spotId: String) {
        viewModelScope.launch {
            runCatching { api.release(spotId, farmId) }
                .onSuccess { load(farmId, force = true) }
                .onFailure { actionError.value = "No pudimos quitar la ubicación. Intenta de nuevo." }
        }
    }

    fun setMain(farmId: String, spotId: String) {
        BusinessStore.setMainSpot(ctx, farmId, spotId)
        _state.update { st -> st.copy(spots = st.spots.sortedByDescending { it.id == spotId }) }
    }

    fun load(farmId: String, force: Boolean = false) {
        if (!force && loadedFor == farmId) return
        loadedFor = farmId
        _state.value = State()
        viewModelScope.launch {
            val products = runCatching { FincasAuth.products(ctx, farmId) }
            _state.update { it.copy(products = products.getOrDefault(emptyList()), productsError = products.exceptionOrNull()?.message, loading = false) }
        }
        viewModelScope.launch {
            // Buddy necesita el vínculo con el negocio (lo valida Apartalo) para mostrar sus lugares.
            runCatching { api.link(LinkBody(FincasAuth.validIdToken(ctx))) }
            val main = BusinessStore.mainSpot(ctx, farmId)
            val spots = runCatching { api.farmSpots(farmId).spots.sortedByDescending { it.id == main } }
            OwnerSpots.map.value = spots.getOrDefault(emptyList()).associate { it.id.lowercase() to farmId }
            val cats = runCatching { api.farmCategories(farmId) }.getOrNull()
            val catalog = runCatching { api.catalog().categories }.getOrDefault(emptyList())
            val covers = spots.getOrDefault(emptyList()).mapNotNull { sp ->
                runCatching { api.ownerPhotos(sp.id).photos.minByOrNull { it.position }?.url }.getOrNull()?.let { sp.id to it }
            }.toMap()
            // El destino de cada lugar (para abrir su mapa): lo que hay alrededor de su punto.
            val dests = spots.getOrDefault(emptyList()).filter { it.noPlace != true }.mapNotNull { sp ->
                runCatching {
                    mapApi.spotsInBounds(sp.lat - 0.002, sp.lng - 0.002, sp.lat + 0.002, sp.lng + 0.002, 50)
                        .spots.firstOrNull { it.id.equals(sp.id, true) }?.destinationId
                }.getOrNull()?.let { sp.id to it }
            }.toMap()
            _state.update {
                it.copy(spots = spots.getOrDefault(emptyList()), categories = cats, catalog = catalog, profileLoading = false, covers = covers, destinations = dests,
                    profileError = if (spots.isFailure) "Entra a tu cuenta de Buddy en Yo para ver las ubicaciones." else null)
            }
        }
    }
}

@Composable
fun BusinessHomeScreen(
    business: FincasAuth.Business,
    modifier: Modifier = Modifier,
    onSwitchToPersonal: () -> Unit,
    onSignOut: () -> Unit,
    onOpenPlace: (com.buddy.app.core.data.model.ApiPlaceCard) -> Unit = {},
    vm: BusinessHomeViewModel = hiltViewModel(),
) {
    val farmId = business.farmId ?: return
    LaunchedEffect(farmId) { vm.load(farmId) }
    val s by vm.state.collectAsState()
    var section by remember { mutableIntStateOf(0) }   // 0 Mi negocio · 1 Productos
    var inviting by remember { mutableStateOf(false) }
    // "+" de Ubicaciones: el mapa para elegir la sede (iOS BusinessLocationPicker).
    var addingLocation by remember { mutableStateOf(false) }
    val actionError by vm.actionError.collectAsState()
    actionError?.let { msg ->
        AlertDialog(onDismissRequest = { vm.actionError.value = null }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.actionError.value = null }) { Text("Entendido") } })
    }
    if (addingLocation) {
        val start = s.spots.firstOrNull()
        Box(modifier.fillMaxSize()) {
            com.buddy.app.features.trips.map.ElegirLugarEnMapa(
                initialLat = start?.lat ?: -10.7351432, initialLng = start?.lng ?: -75.2676019,
                categories = emptyList(), isSubmitting = false, errorMessage = null,
                onBack = { addingLocation = false },
                onPickSpot = { sp -> vm.addLocation(farmId, sp.id, sp.name, sp.lat ?: 0.0, sp.lng ?: 0.0) { addingLocation = false } },
                onProposeAt = { nombre, _, lat, lng -> vm.addLocation(farmId, null, nombre.ifBlank { business.name }, lat, lng) { addingLocation = false } },
                // Mismo diseño que Compartir un lugar (Keyla, 25 Nov 2026).
                title = "Agregar ubicación",
                fixedName = business.name,
            )
        }
        return
    }
    if (inviting) InviteTeamDialog(business) { inviting = false }
    // Editor de producto (fase 3): null = cerrado; Pair(true, null) = nuevo.
    var editing by remember { mutableStateOf<Pair<Boolean, FincasProduct?>?>(null) }
    editing?.let { (_, prod) ->
        ProductEditorScreen(farmId, prod, modifier) { changed -> editing = null; if (changed) vm.load(farmId, force = true) }
        return
    }

    LazyColumn(
        modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(business.name, style = BuddyType.Title1, color = BuddyColor.Brand,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BusinessMenu(isOwner = business.role == "owner", onInvite = { inviting = true }, onSwitchToPersonal, onSignOut)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storefront, null, tint = BuddyColor.Brand, modifier = Modifier.size(32.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cuenta negocio", style = BuddyType.Subhead, color = BuddyColor.InkMuted)
                    Text(if (business.role == "owner") "Vinculado" else "Equipo",
                        style = BuddyType.FootnoteBold, color = BuddyColor.Brand,
                        modifier = Modifier.clip(CircleShape).background(BuddyColor.SageTint).padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(BuddyColor.SurfaceRaised).padding(3.dp)) {
                listOf("Mi negocio", "Productos").forEachIndexed { i, t ->
                    Box(
                        Modifier.weight(1f).height(38.dp).clip(CircleShape)
                            .background(if (section == i) BuddyColor.Surface else BuddyColor.SurfaceRaised)
                            .clickable { section = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(t, style = BuddyType.FootnoteBold, color = BuddyColor.Ink) }
                }
            }
        }
        if (section == 0) {
            item {
                MiNegocio(farmId, business.name, s, isLinked = true, onShowProducts = { section = 1 },
                    onAddLocation = { addingLocation = true },
                    onSetMain = { sp -> vm.setMain(farmId, sp.id) },
                    onRemove = { sp -> vm.removeLocation(farmId, sp.id) },
                    onOpenSpot = { sp ->
                        onOpenPlace(com.buddy.app.core.data.model.ApiPlaceCard(
                            id = sp.id, name = sp.name, destinationId = s.destinations[sp.id], lat = sp.lat, lng = sp.lng,
                            coverUrl = s.covers[sp.id]))
                    }) { main, extra -> vm.saveCats(farmId, main, extra) }
            }
        } else {
            item {
                Text(
                    "+ Añadir producto", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(BuddyColor.Brand)
                        .clickable { editing = true to null }.wrapContentHeight(),
                )
            }
            when {
                s.loading -> item { Text("Cargando productos", style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
                s.productsError != null -> item { Text(s.productsError!!, style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
                s.products.isEmpty() -> item { Text("Aún no hay productos.", style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
                else -> items(s.products, key = { it.id }) { p -> ProductRow(p) { editing = true to p } }
            }
        }
    }
}

@Composable
private fun BusinessMenu(isOwner: Boolean, onInvite: () -> Unit, onSwitchToPersonal: () -> Unit, onSignOut: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "Opciones de cuenta", tint = BuddyColor.InkMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (isOwner) DropdownMenuItem(
                text = { Text("Invitar a mi equipo", style = BuddyType.Body, color = BuddyColor.Ink) },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, tint = BuddyColor.Ink) },
                onClick = { open = false; onInvite() },
            )
            DropdownMenuItem(
                text = { Text("Cambiar a mi cuenta personal", style = BuddyType.Body, color = BuddyColor.Ink) },
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null, tint = BuddyColor.Ink) },
                onClick = { open = false; onSwitchToPersonal() },
            )
            DropdownMenuItem(
                text = { Text("Cerrar sesión del negocio", style = BuddyType.Body, color = BuddyColor.Ink) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null, tint = BuddyColor.Ink) },
                onClick = { open = false; onSignOut() },
            )
        }
    }
}

@Composable
private fun Card(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(BuddyColor.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
            }
            Text(title, style = BuddyType.Headline, color = BuddyColor.Ink)
        }
        content()
    }
}

@Composable
private fun ProductRow(p: FincasProduct, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface).clickable(onClick = onEdit).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(model = p.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(BuddyColor.SurfaceRaised))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.presentations.orEmpty().forEach { pr ->
                val price = pr.price?.let { soles(it) } ?: ""
                Text(listOf(pr.label, price).filter { it.isNotEmpty() }.joinToString(" · ") + if (pr.visible == false) " · oculto" else "",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = BuddyColor.InkMuted)
            }
        }
    }
}

/** "Invitar a mi equipo" (iOS InviteTeamSheet): crea el enlace y lo comparte. */
@Composable
private fun InviteTeamDialog(business: FincasAuth.Business, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var link by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val id = business.businessId
        if (business.role != "owner" || id == null) { error = "Solo la dueña o el dueño del negocio puede invitar a otras personas."; return@LaunchedEffect }
        runCatching { FincasAuth.createJoinCode(ctx, id) }
            .onSuccess { link = "https://www.buddyapp.biz/unirse/$it" }
            .onFailure { error = it.message }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Invitar a mi equipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Envía este enlace a la persona que te ayudará con ${business.name}. Al abrirlo y entrar con su cuenta, podrá administrar el negocio contigo.",
                    style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                when {
                    link != null -> {
                        Text(link!!, style = BuddyType.FootnoteBold, color = BuddyColor.Ink,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BuddyColor.SageTint).padding(12.dp))
                        Text("Sirve para una sola persona y vence en 48 horas.", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
                    }
                    error != null -> Text(error!!, style = BuddyType.Footnote, color = BuddyColor.Ink)
                    else -> Text("Creando el enlace", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                }
            }
        },
        confirmButton = {
            if (link != null) TextButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, "Te invito a administrar ${business.name} conmigo en Buddy. Abre este enlace: $link")
                ctx.startActivity(android.content.Intent.createChooser(send, "Compartir enlace"))
            }) { Text("Compartir enlace", color = BuddyColor.Brand) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Listo", color = BuddyColor.InkMuted) } },
    )
}

// ── Mi negocio, igual que iOS (BusinessProfileEditor) ─────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MiNegocio(farmId: String, businessName: String, s: BusinessHomeViewModel.State, isLinked: Boolean,
                      onShowProducts: () -> Unit, onOpenSpot: (FarmSpot) -> Unit,
                      onAddLocation: () -> Unit, onSetMain: (FarmSpot) -> Unit, onRemove: (FarmSpot) -> Unit, onSaveCategories: (String, List<String>) -> Unit) {
    val ctx = LocalContext.current
    var savedAbout by remember(farmId) { mutableStateOf(BusinessStore.about(ctx, farmId)) }
    var savedContact by remember(farmId) { mutableStateOf(BusinessStore.contact(ctx, farmId)) }
    var about by remember(farmId) { mutableStateOf(savedAbout) }
    var contact by remember(farmId) { mutableStateOf(savedContact) }
    var main by remember(s.categories) { mutableStateOf(s.categories?.main) }
    var extra by remember(s.categories) { mutableStateOf(s.categories?.secondary.orEmpty()) }
    var locationsOpen by remember { mutableStateOf(true) }
    var generalOpen by remember { mutableStateOf(true) }

    // "Completa tu perfil" (iOS ProfileChecklist): los mismos 6 pasos y el mismo orden.
    data class Step(val label: String, val done: Boolean, val target: String)
    val steps = buildList {
        add(Step("Agrega una descripción", savedAbout.isNotBlank(), "general"))
        add(Step("Agrega tu WhatsApp", savedContact.isNotBlank(), "general"))
        if (isLinked) add(Step("Añade tus productos", s.products.isNotEmpty(), "products"))
        add(Step("Agrega una ubicación", s.spots.isNotEmpty(), "locations"))
        add(Step("Agrega el horario de tus ubicaciones", false, "locations"))
        add(Step("Sube fotos de tus ubicaciones", s.covers.isNotEmpty(), "locations"))
    }
    val done = steps.count { it.done }
    val next = steps.firstOrNull { !it.done }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Completa tu perfil", style = BuddyType.Title3, color = BuddyColor.Brand, modifier = Modifier.weight(1f))
                Text("$done de ${steps.size}", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Brand,
                    modifier = Modifier.clip(CircleShape).background(BuddyColor.PaleSage).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            LinearProgressIndicator(progress = { done / steps.size.toFloat() }, color = BuddyColor.Brand,
                trackColor = BuddyColor.Hairline, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            next?.let { n ->
                Row(
                    Modifier.fillMaxWidth().clip(CircleShape).background(BuddyColor.SageTint)
                        .clickable {
                            when (n.target) { "products" -> onShowProducts(); "general" -> generalOpen = true; else -> locationsOpen = true }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Siguiente:", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                    Text(n.label, style = BuddyType.FootnoteBold, color = BuddyColor.Brand, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
                }
            }
        }

        Group("Ubicaciones", Icons.Outlined.Explore,
            summary = when { s.profileLoading -> "Cargando…"; s.spots.isEmpty() -> "Agrega una ubicación"; else -> s.spots.joinToString(", ") { it.name } },
            open = locationsOpen, onToggle = { locationsOpen = !locationsOpen }) {
            s.profileError?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // La MISMA tarjeta de Inicio (iOS HomePlaceCard), con la categoría del negocio.
                val catName = s.catalog.firstOrNull { it.id == s.categories?.main }?.name
                items(s.spots, key = { it.id }) { sp ->
                    // Tocar abre el lugar; mantener presionado: "Hacer principal" / "Quitar ubicación" (iOS).
                    var menu by remember { mutableStateOf(false) }
                    var confirmRemove by remember { mutableStateOf(false) }
                    Box(Modifier.combinedClickable(onClick = { onOpenSpot(sp) }, onLongClick = { menu = true })) {
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (s.spots.firstOrNull()?.id != sp.id) DropdownMenuItem(
                                text = { Text("Hacer principal") },
                                leadingIcon = { Icon(Icons.Outlined.StarOutline, null) },
                                onClick = { menu = false; onSetMain(sp) })
                            DropdownMenuItem(
                                text = { Text("Quitar ubicación", color = BuddyColor.ErrorRed) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = BuddyColor.ErrorRed) },
                                onClick = { menu = false; confirmRemove = true })
                        }
                        if (confirmRemove) AlertDialog(
                            onDismissRequest = { confirmRemove = false },
                            title = { Text("¿Quitar ${sp.name}?") },
                            text = { Text("Dejará de ser una ubicación de tu negocio. El lugar sigue en Buddy.") },
                            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove(sp) }) { Text("Quitar", color = BuddyColor.ErrorRed) } },
                            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") } },
                        )
                        com.buddy.app.features.home.inicio.PlaceCard(
                            com.buddy.app.core.data.model.ApiPlaceCard(id = sp.id, name = sp.name, lat = sp.lat, lng = sp.lng,
                                coverUrl = s.covers[sp.id], category = catName),
                            large = false,
                        )
                    }
                }
                item { AddLocationCard(businessName, onAddLocation) }
            }
        }

        Group("Información general", Icons.Outlined.Info,
            summary = when {
                savedAbout.isNotBlank() && savedContact.isNotBlank() -> "Descripción y contacto listos"
                savedAbout.isNotBlank() -> "Falta el contacto"
                savedContact.isNotBlank() -> "Falta la descripción"
                else -> "Agrega descripción y contacto"
            },
            open = generalOpen, onToggle = { generalOpen = !generalOpen }) {
            SubTitle("Categoría", Icons.Outlined.Category)
            if (s.profileLoading) Text("Cargando…", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            else {
                Combo("Principal", main, s.catalog, allowNone = false) { c -> main = c; extra = extra - listOfNotNull(c) }
                for (i in 0..1) if (i == 0 || extra.isNotEmpty()) {
                    Combo("Otra (opcional)", extra.getOrNull(i), s.catalog.filter { it.id != main }, allowNone = true) { c ->
                        val list = extra.toMutableList()
                        if (c != null) { if (i < list.size) list[i] = c else list.add(c) } else if (i < list.size) list.removeAt(i)
                        extra = list.distinct().filter { it != main }
                    }
                }
            }
            HorizontalDivider(color = BuddyColor.Hairline)
            SubTitle("Descripción", Icons.Outlined.Notes)
            OutlinedTextField(about, { about = it.take(300) }, minLines = 4, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cuenta qué ofreces, qué te hace especial y qué puede vivir el visitante.") },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = BuddyColor.Canvas, focusedContainerColor = BuddyColor.Canvas,
                    unfocusedBorderColor = BuddyColor.Canvas, focusedBorderColor = BuddyColor.Brand))
            Text("${about.length} de 300", style = BuddyType.Caption1, color = BuddyColor.InkMuted, modifier = Modifier.align(Alignment.End))
            HorizontalDivider(color = BuddyColor.Hairline)
            SubTitle("Contacto", Icons.Outlined.Phone)
            OutlinedTextField(contact, { contact = it.filter { ch -> ch.isDigit() || ch == '+' || ch == ' ' } }, singleLine = true,
                placeholder = { Text("WhatsApp, ej. 987654321") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = BuddyColor.Canvas, focusedContainerColor = BuddyColor.Canvas,
                    unfocusedBorderColor = BuddyColor.Canvas, focusedBorderColor = BuddyColor.Brand))
            val changed = about.trim() != savedAbout || contact.trim() != savedContact ||
                main != s.categories?.main || extra != s.categories?.secondary.orEmpty()
            if (changed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Cancelar", dark = false, modifier = Modifier.weight(1f)) {
                    about = savedAbout; contact = savedContact; main = s.categories?.main; extra = s.categories?.secondary.orEmpty()
                }
                Pill("Guardar cambios", dark = true, modifier = Modifier.weight(1f)) {
                    BusinessStore.saveProfile(ctx, farmId, about.trim(), contact.trim())
                    savedAbout = about.trim(); savedContact = contact.trim()
                    if (main != null && (main != s.categories?.main || extra != s.categories?.secondary.orEmpty())) onSaveCategories(main!!, extra)
                }
            }
        }
        Text("Por ahora la descripción y el contacto se guardan en este teléfono. Pronto se publicarán en tu página de Buddy.",
            style = BuddyType.Caption1, color = BuddyColor.InkMuted)
    }
}

@Composable
private fun Group(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, summary: String, open: Boolean,
                  onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(BuddyColor.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = BuddyType.Headline, color = BuddyColor.Ink)
                Text(summary, style = BuddyType.Subhead, color = BuddyColor.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = BuddyColor.Brand)
        }
        if (open) content()
    }
}

@Composable
private fun SubTitle(t: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp))
        Text(t, style = BuddyType.Headline, color = BuddyColor.Brand)
    }
}

/** id -> nombre de todas las categorías vistas (para mostrar la elegida aunque no esté en la lista filtrada). */
private val allNames = mutableMapOf<String, String>()

@Composable
private fun Combo(label: String, value: String?, options: List<CatalogCategory>, allowNone: Boolean, onPick: (String?) -> Unit) {
    // value es el id de la categoría; se muestra su nombre.
    options.forEach { allNames[it.id] = it.name }
    val shown = value?.let { id -> options.firstOrNull { it.id == id }?.name ?: allNames[id] }
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        Box {
            Row(
                Modifier.fillMaxWidth().clip(CircleShape).background(BuddyColor.Canvas).clickable { open = true }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shown ?: "Ninguna", style = BuddyType.Body, color = if (shown == null) BuddyColor.InkMuted else BuddyColor.Ink,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = BuddyColor.Ink)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text("Ninguna") }, onClick = { open = false; onPick(null) })
                options.forEach { o -> DropdownMenuItem(text = { Text(o.name) }, onClick = { open = false; onPick(o.id) }) }
            }
        }
    }
}

@Composable
private fun LocationCard(name: String, cover: String?, onClick: () -> Unit) {
    Column(Modifier.width(170.dp).shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(BuddyColor.Surface)
        .clickable(onClick = onClick)) {
        if (cover != null) AsyncImage(cover, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(130.dp).background(BuddyColor.SurfaceRaised))
        else Box(Modifier.fillMaxWidth().height(130.dp).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Storefront, null, tint = BuddyColor.Brand, modifier = Modifier.size(32.dp))
        }
        Text(name, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

/** "+" punteado como iOS: la forma de la tarjeta, vacía, con el nombre del negocio abajo. */
@Composable
private fun AddLocationCard(name: String, onClick: () -> Unit) {
    Column(
        Modifier.width(168.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.Surface)
            .drawBehind {
                drawRoundRect(color = BuddyColor.Sage, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Agregar otra ubicación" },
    ) {
        Box(Modifier.fillMaxWidth().height(126.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(BuddyColor.SageTint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, tint = BuddyColor.Brand, modifier = Modifier.size(24.dp))
            }
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Place, null, tint = BuddyColor.Brand, modifier = Modifier.size(15.dp))
            Text(name, style = BuddyType.Headline, color = BuddyColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Pill(t: String, dark: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(44.dp).clip(CircleShape).background(if (dark) BuddyColor.Brand else BuddyColor.SageTint).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(t, style = BuddyType.FootnoteBold, color = if (dark) BuddyColor.InkInverse else BuddyColor.Brand)
    }
}
