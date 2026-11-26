package com.buddy.app.features.business

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
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
        /** spotId -> ciudad ("Villa Rica"), bajo el nombre en Ubicaciones. */
        val cities: Map<String, String> = emptyMap(),
        /** Perfil publicado en el servidor (26 Nov 2026). */
        val about: String = "",
        val contact: String = "",
        /** spotId -> horario de esa sede. */
        val hours: Map<String, List<DayHours>> = emptyMap(),
        val profileSaveError: String? = null,
        /** Lugares nuevos que esperan aprobación ("En revisión", 26 Nov 2026). */
        val pending: Set<String> = emptySet(),
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

    /** Descripción y WhatsApp: al servidor (la página del lugar muestra la descripción). */
    fun saveProfile(farmId: String, about: String, contact: String) {
        BusinessStore.saveProfile(ctx, farmId, about, contact)
        _state.update { it.copy(about = about, contact = contact, profileSaveError = null) }
        viewModelScope.launch {
            runCatching { api.saveFarmProfile(farmId, FarmProfileBody(about.ifBlank { null }, contact.ifBlank { null })) }
                .onFailure { _state.update { it.copy(profileSaveError = "No pudimos publicar la descripción. Revisa tu conexión.") } }
        }
    }

    fun saveHours(farmId: String, spotId: String, hours: List<DayHours>, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { api.saveSpotHours(spotId, SpotHoursBody(farmId, hours)) }
                .onSuccess { _state.update { it.copy(hours = it.hours + (spotId to hours), profileSaveError = null) }; done(true) }
                .onFailure { _state.update { it.copy(profileSaveError = "No pudimos publicar el horario. Revisa tu conexión.") }; done(false) }
        }
    }

    /** Lo del servidor gana; lo que solo estaba en este teléfono se sube una vez (iOS). */
    private suspend fun syncProfile(farmId: String) {
        val server = runCatching { api.farmProfile(farmId) }.getOrNull()
        val localAbout = BusinessStore.about(ctx, farmId); val localContact = BusinessStore.contact(ctx, farmId)
        if (server == null) { _state.update { it.copy(about = localAbout, contact = localContact) }; return }
        if (server.about != null || server.contact != null) {
            BusinessStore.saveProfile(ctx, farmId, server.about.orEmpty(), server.contact.orEmpty())
            _state.update { it.copy(about = server.about.orEmpty(), contact = server.contact.orEmpty()) }
        } else {
            _state.update { it.copy(about = localAbout, contact = localContact) }
            if (localAbout.isNotBlank() || localContact.isNotBlank())
                runCatching { api.saveFarmProfile(farmId, FarmProfileBody(localAbout.ifBlank { null }, localContact.ifBlank { null })) }
        }
        _state.update { it.copy(hours = server.hours.orEmpty()) }
    }

    /** Sede nueva: un lugar de Buddy que es suyo, o uno nuevo propuesto aquí (iOS BusinessLocationPicker). */
    // Paso 2 de "Agregar ubicación": la sede se crea/reclama y luego se sube su portada (iOS, 26 Nov 2026).
    fun addLocation(farmId: String, spotId: String?, name: String, lat: Double, lng: Double,
                    photo: android.net.Uri? = null, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                val id = spotId ?: homeApi.proposeSpot(com.buddy.app.features.home.data.ProposeSpotBody(name, lat, lng)).id
                api.claim(id, ClaimBody(farmId))
                if (photo != null) {
                    val jpeg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Jpeg.from(ctx, photo) }
                    val body = okhttp3.RequestBody.create("image/jpeg".toMediaType(), jpeg)
                    api.uploadOwnerPhoto(id, okhttp3.MultipartBody.Part.createFormData("photo", "foto.jpg", body),
                        okhttp3.RequestBody.create(null, farmId))
                }
            }
            r.onSuccess { done(true); load(farmId, force = true) }
             .onFailure { done(false); actionError.value = "No pudimos guardar la ubicación. Revisa tu conexión. Si ya es de otro negocio, escríbenos." }
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
            syncProfile(farmId)
            val cats = runCatching { api.farmCategories(farmId) }.getOrNull()
            val catalog = runCatching { api.catalog().categories }.getOrDefault(emptyList())
            val covers = spots.getOrDefault(emptyList()).mapNotNull { sp ->
                runCatching { api.ownerPhotos(sp.id).photos.minByOrNull { it.position }?.url }.getOrNull()?.let { sp.id to it }
            }.toMap()
            // El destino de cada lugar (para abrir su mapa): lo que hay alrededor de su punto.
            val pendingIds = mutableSetOf<String>()
            val dests = spots.getOrDefault(emptyList()).filter { it.noPlace != true }.mapNotNull { sp ->
                runCatching {
                    val hit = mapApi.spotsInBounds(sp.lat - 0.002, sp.lng - 0.002, sp.lat + 0.002, sp.lng + 0.002, 50)
                        .spots.firstOrNull { it.id.equals(sp.id, true) }
                    if (hit?.estaPendiente == true) pendingIds += sp.id
                    hit?.destinationId
                }.getOrNull()?.let { sp.id to it }
            }.toMap()
            val cities = spots.getOrDefault(emptyList()).mapNotNull { sp ->
                runCatching { homeApi.searchCuratedSpots(sp.name).spots.firstOrNull { it.id.equals(sp.id, true) }?.destination?.name }
                    .getOrNull()?.let { sp.id to it }
            }.toMap()
            _state.update {
                it.copy(spots = spots.getOrDefault(emptyList()), categories = cats, catalog = catalog, profileLoading = false, covers = covers, destinations = dests, cities = cities, pending = pendingIds,
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
    // Agregar ubicación en 2 pasos (iOS, Keyla 26 Nov 2026): 1) el lugar en el mapa,
    // 2) la ubicación elegida + foto de portada obligatoria + un solo "Guardar ubicación".
    var picked by remember { mutableStateOf<PickedLocation?>(null) }
    if (addingLocation && picked != null) {
        AddLocationStep2(picked!!, business.name, modifier,
            onBack = { picked = null },
            onSave = { photo, done ->
                val p = picked!!
                vm.addLocation(farmId, p.spotId, p.name, p.lat, p.lng, photo) { ok ->
                    done(ok); if (ok) { picked = null; addingLocation = false }
                }
            })
        return
    }
    if (addingLocation) {
        val start = s.spots.firstOrNull()
        Box(modifier.fillMaxSize()) {
            com.buddy.app.features.trips.map.ElegirLugarEnMapa(
                initialLat = start?.lat ?: -10.7351432, initialLng = start?.lng ?: -75.2676019,
                categories = emptyList(), isSubmitting = false, errorMessage = null,
                onBack = { addingLocation = false },
                onPickSpot = { sp -> picked = PickedLocation(sp.id, sp.name, sp.lat ?: 0.0, sp.lng ?: 0.0, sp.name) },
                onProposeAt = { nombre, _, lat, lng -> picked = PickedLocation(null, nombre.ifBlank { business.name }, lat, lng, null) },
                // Mismo diseño que Compartir un lugar (Keyla, 25 Nov 2026).
                title = "Agregar ubicación",
                fixedName = business.name,
                proposeTitle = "Siguiente",
            )
        }
        return
    }
    if (inviting) InviteTeamDialog(business) { inviting = false }
    // Editor de producto: null = cerrado. Con focusId = solo esa presentación;
    // addPresentation = una nueva vacía (iOS, Keyla 26 Nov 2026).
    // Horario de una sede a pantalla completa (iOS: ficha de la sede, 26 Nov 2026).
    var editingHours by remember { mutableStateOf<FarmSpot?>(null) }
    editingHours?.let { sp ->
        HoursEditorScreen(sp.name, s.hours[sp.id], modifier, onClose = { editingHours = null }) { hours, done ->
            vm.saveHours(farmId, sp.id, hours) { ok -> done(ok); if (ok) editingHours = null }
        }
        return
    }
    var editing by remember { mutableStateOf<ProductEdit?>(null) }
    editing?.let { e ->
        ProductEditorScreen(farmId, e.product, modifier, focusPresentationId = e.focusId, addPresentation = e.add) { changed ->
            editing = null; if (changed) vm.load(farmId, force = true)
        }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (business.role == "owner") "Vinculado" else "Equipo",
                            style = BuddyType.FootnoteBold, color = BuddyColor.Brand,
                            modifier = Modifier.clip(CircleShape).background(BuddyColor.SageTint).padding(horizontal = 12.dp, vertical = 5.dp))
                        // Estado en Inicio (iOS, 26 Nov 2026): rojo sin ubicación, ámbar en revisión, verde visible.
                        if (!s.profileLoading) {
                            val (label, tint) = when {
                                s.spots.isEmpty() -> "No visible en Inicio" to BuddyColor.ErrorRed
                                s.spots.all { it.id in s.pending } -> "En revisión" to BuddyColor.WarningAmber
                                else -> "Visible en Inicio" to BuddyColor.Brand
                            }
                            Text(label, style = BuddyType.FootnoteBold, color = tint, maxLines = 1,
                                modifier = Modifier.clip(CircleShape)
                                    .background(if (tint == BuddyColor.Brand) BuddyColor.SageTint else tint.copy(alpha = 0.14f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
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
                    },
                    onEditHours = { sp -> editingHours = sp },
                    onSaveProfile = { a, c -> vm.saveProfile(farmId, a, c) },
                ) { main, extra -> vm.saveCats(farmId, main, extra) }
            }
        } else {
            item {
                Text(
                    "+ Añadir producto", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().height(50.dp).clip(CircleShape).background(BuddyColor.Brand)
                        .clickable { editing = ProductEdit(null) }.wrapContentHeight(),
                )
            }
            when {
                s.loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = BuddyColor.Brand)
                        Text("Cargando productos", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                    }
                }
                s.productsError != null -> item { Text(s.productsError!!, style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
                s.products.isEmpty() -> item { Text("Aún no hay productos.", style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
                else -> items(s.products, key = { it.id }) { p -> ProductRow(p,
                    onEdit = { editing = ProductEdit(p) },
                    onEditPresentation = { id -> editing = ProductEdit(p, focusId = id) },
                    onAddPresentation = { editing = ProductEdit(p, add = true) }) }
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
            DropdownMenuItem(
                text = { Text("Cambiar a mi cuenta personal", style = BuddyType.Body, color = BuddyColor.Ink) },
                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null, tint = BuddyColor.Ink) },
                onClick = { open = false; onSwitchToPersonal() },
            )
            if (isOwner) DropdownMenuItem(
                text = { Text("Invitar a mi equipo", style = BuddyType.Body, color = BuddyColor.Ink) },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, tint = BuddyColor.Ink) },
                onClick = { open = false; onInvite() },
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

private data class ProductEdit(val product: FincasProduct?, val focusId: String? = null, val add: Boolean = false)

/** Igual que iOS remoteCard (Keyla, 26 Nov 2026): nombre, descripción, estado a la
 *  derecha; cada presentación con su foto, precio en píldora (oculta: solo borde
 *  tenue) y "+ Agregar presentación". */
@Composable
private fun ProductRow(p: FincasProduct, onEdit: () -> Unit, onEditPresentation: (String) -> Unit, onAddPresentation: () -> Unit) {
    val active = p.status == "active"
    val statusText = when (p.status) { "active" -> "En la tienda"; "archived" -> "Oculto"; else -> "Borrador" }
    val pres = p.presentations.orEmpty()
    Column(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(p.name, style = BuddyType.Headline, color = BuddyColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = BuddyType.Footnote, color = BuddyColor.InkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.align(Alignment.End).clip(CircleShape)
                    .background(if (active) BuddyColor.SageTint else BuddyColor.SurfaceRaised)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(if (active) Icons.Outlined.Storefront else Icons.Outlined.VisibilityOff, null,
                    tint = if (active) BuddyColor.Brand else BuddyColor.InkMuted, modifier = Modifier.size(13.dp))
                Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (active) BuddyColor.Brand else BuddyColor.InkMuted)
            }
        }
        pres.forEachIndexed { i, pr ->
            val oculta = pr.visible == false
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { onEditPresentation(pr.id) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(model = pr.ownPhoto ?: p.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                        alpha = if (oculta) 0.5f else 1f,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage))
                    if (oculta) Icon(Icons.Outlined.VisibilityOff, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(16.dp))
                    Text((pr.label.ifEmpty { "Presentación" }) + if (oculta) " · oculta" else "",
                        style = BuddyType.Footnote, color = if (oculta) BuddyColor.InkMuted else BuddyColor.Ink,
                        modifier = Modifier.weight(1f))
                    pr.price?.let { price ->
                        Text(soles(price), style = BuddyType.FootnoteBold,
                            color = if (oculta) BuddyColor.InkMuted.copy(alpha = 0.6f) else BuddyColor.Brand,
                            modifier = Modifier.clip(CircleShape)
                                .then(if (oculta) Modifier.border(1.dp, BuddyColor.InkMuted.copy(alpha = 0.2f), CircleShape)
                                      else Modifier.background(BuddyColor.SageTint))
                                .padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                if (i < pres.size - 1) Box(Modifier.padding(start = 60.dp, top = 6.dp).fillMaxWidth().height(0.5.dp)
                    .background(BuddyColor.InkMuted.copy(alpha = 0.15f)))
            }
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth().height(40.dp).clip(CircleShape).background(BuddyColor.SageTint)
                .clickable(onClick = onAddPresentation),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar presentación", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
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
                      onAddLocation: () -> Unit, onSetMain: (FarmSpot) -> Unit, onRemove: (FarmSpot) -> Unit,
                      onEditHours: (FarmSpot) -> Unit, onSaveProfile: (String, String) -> Unit,
                      onSaveCategories: (String, List<String>) -> Unit) {
    // Lo publicado en el servidor (el ViewModel ya subió lo que solo estaba en el teléfono).
    val savedAbout = s.about
    val savedContact = s.contact
    var about by remember(farmId, savedAbout) { mutableStateOf(savedAbout) }
    var contact by remember(farmId, savedContact) { mutableStateOf(savedContact) }
    var hoursOpen by remember { mutableStateOf(true) }
    var main by remember(s.categories) { mutableStateOf(s.categories?.main) }
    var extra by remember(s.categories) { mutableStateOf(s.categories?.secondary.orEmpty()) }
    var locationsOpen by remember { mutableStateOf(true) }
    var generalOpen by remember { mutableStateOf(true) }
    /** Tocó la alerta "Obligatorio": Ubicaciones en rojo hasta tener una (iOS). */
    var flagLocations by remember { mutableStateOf(false) }

    // "Completa tu perfil" (iOS ProfileChecklist): los mismos 6 pasos y el mismo orden.
    data class Step(val label: String, val done: Boolean, val target: String)
    val steps = buildList {
        // La ubicación primero: sin ella el negocio no aparece en Inicio (iOS, 26 Nov 2026).
        add(Step("Agrega tu ubicación", s.spots.isNotEmpty(), "locations"))
        add(Step("Agrega una descripción", savedAbout.isNotBlank(), "general"))
        add(Step("Agrega tu WhatsApp", savedContact.isNotBlank(), "general"))
        if (isLinked) add(Step("Añade tus productos", s.products.isNotEmpty(), "products"))
        add(Step("Agrega el horario de tus ubicaciones", s.spots.isNotEmpty() && s.spots.all { s.hours[it.id] != null }, "hours"))
        add(Step("Sube fotos de tus ubicaciones", s.covers.isNotEmpty(), "locations"))
    }
    val done = steps.count { it.done }
    val next = steps.firstOrNull { !it.done }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Completo: desaparece; mientras carga, espera para no mostrar un paso falso (iOS).
        if (next != null && !s.loading && !s.profileLoading) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Completa tu perfil", style = BuddyType.Title3, color = BuddyColor.Brand, modifier = Modifier.weight(1f))
                Text("$done de ${steps.size}", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Brand,
                    modifier = Modifier.clip(CircleShape).background(BuddyColor.PaleSage).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            LinearProgressIndicator(progress = { done / steps.size.toFloat() }, color = BuddyColor.Brand,
                trackColor = BuddyColor.Hairline, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            next?.let { n ->
                // Sin ubicación: "Siguiente" es la alerta obligatoria, en rojo (iOS, 26 Nov 2026).
                val required = s.spots.isEmpty() && n.target == "locations"
                val tint = if (required) BuddyColor.ErrorRed else BuddyColor.Brand
                Row(
                    Modifier.fillMaxWidth().clip(if (required) RoundedCornerShape(16.dp) else CircleShape)
                        .background(if (required) BuddyColor.ErrorRed.copy(alpha = 0.10f) else BuddyColor.SageTint)
                        .clickable {
                            when (n.target) {
                                "products" -> onShowProducts()
                                "general" -> generalOpen = true
                                // Horario: abre la primera sede sin horario (iOS, 26 Nov 2026).
                                "hours" -> s.spots.firstOrNull { s.hours[it.id] == null }?.let(onEditHours) ?: run { locationsOpen = true }
                                else -> { locationsOpen = true; if (s.spots.isEmpty()) flagLocations = true }
                            }
                        }.padding(horizontal = 16.dp, vertical = if (required) 12.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (required) Icon(Icons.Filled.Error, null, tint = tint, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (required) "Obligatorio:" else "Siguiente:", style = BuddyType.Footnote,
                                color = if (required) tint else BuddyColor.InkMuted)
                            Text(n.label, style = BuddyType.FootnoteBold, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (required) Text("Sin ubicación, tu negocio no aparece en Inicio.", style = BuddyType.Footnote, color = tint)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
        }

        Group("Ubicaciones", Icons.Outlined.Explore,
            summary = when { s.profileLoading -> "Cargando…"; s.spots.isEmpty() -> "Agrega una ubicación"; else -> s.spots.joinToString(", ") { it.name } },
            open = locationsOpen, onToggle = { locationsOpen = !locationsOpen },
            highlight = flagLocations && s.spots.isEmpty()) {
            s.profileError?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.InkMuted) }
            if (flagLocations && s.spots.isEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Error, null, tint = BuddyColor.ErrorRed, modifier = Modifier.size(18.dp))
                Text("Agrega al menos una ubicación. Es obligatorio para aparecer en Inicio.",
                    style = BuddyType.FootnoteBold, color = BuddyColor.ErrorRed)
            }
            // Centradas cuando caben (una sola sede queda al medio, Keyla 26 Nov 2026).
            androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
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
                            city = s.cities[sp.id],
                        )
                    }
                }
                if (s.spots.isEmpty()) item { AddLocationCard(businessName, onAddLocation) }
            }
            // Otra sede: un botón con texto, no una tarjeta con "+" (se confundía
            // con agregar fotos, Keyla 26 Nov 2026).
            if (s.spots.isNotEmpty()) Row(
                Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(BuddyColor.SageTint).clickable(onClick = onAddLocation),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, null, tint = BuddyColor.Brand, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Agregar otra ubicación", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
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
                    onSaveProfile(about.trim(), contact.trim())
                    if (main != null && (main != s.categories?.main || extra != s.categories?.secondary.orEmpty())) onSaveCategories(main!!, extra)
                }
            }
        }
        // Horario después de Información general (iOS, 26 Nov 2026): cada fila abre el de esa sede.
        Group("Horario de atención", Icons.Outlined.Schedule,
            summary = when {
                s.spots.isEmpty() -> "Sin ubicaciones"
                s.spots.any { s.hours[it.id] == null } -> "Falta el horario"
                s.spots.size == 1 -> HoursSummary.text(s.hours[s.spots[0].id]!!)
                else -> "Listo en ${s.spots.size} ubicaciones"
            },
            open = hoursOpen, onToggle = { hoursOpen = !hoursOpen }) {
            if (s.spots.isEmpty()) Text("Agrega una ubicación para poner su horario.", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            s.spots.forEachIndexed { i, sp ->
                if (i > 0) HorizontalDivider(color = BuddyColor.Hairline)
                val h = s.hours[sp.id]
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEditHours(sp) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (s.spots.size > 1) Text(sp.name, style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                        Text(h?.let { HoursSummary.text(it) } ?: "Sin horario", style = BuddyType.Footnote,
                            color = if (h == null) BuddyColor.Brand else BuddyColor.InkMuted)
                    }
                    Text(if (h == null) "Agregar" else "Editar", style = BuddyType.FootnoteBold, color = BuddyColor.Brand)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.InkMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        s.profileSaveError?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.ErrorRed) }
        Text("La descripción y el horario se ven en la página de tu lugar. Tu WhatsApp no se muestra.",
            style = BuddyType.Caption1, color = BuddyColor.InkMuted)
    }
}

@Composable
private fun Group(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, summary: String, open: Boolean,
                  onToggle: () -> Unit, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(BuddyColor.Surface)
            .then(if (highlight) Modifier.border(2.dp, BuddyColor.ErrorRed, RoundedCornerShape(22.dp)) else Modifier)
            .padding(16.dp),
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


/** Lo elegido en el paso 1 de "Agregar ubicación". label = nombre del lugar existente; null = punto nuevo. */
data class PickedLocation(val spotId: String?, val name: String, val lat: Double, val lng: Double, val label: String?)

/** Paso 2 (iOS, Keyla 26 Nov 2026): ubicación elegida arriba, foto de portada obligatoria con la
 *  misma tarjeta de Inicio como vista previa, y un solo "Guardar ubicación". Nada se guarda antes. */
@Composable
private fun AddLocationStep2(picked: PickedLocation, businessName: String, modifier: Modifier,
                             onBack: () -> Unit, onSave: (android.net.Uri, (Boolean) -> Unit) -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Punto nuevo: se muestra la calle, no el nombre del negocio.
    var street by remember(picked) { mutableStateOf<String?>(null) }
    LaunchedEffect(picked) {
        if (picked.label == null) street = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                android.location.Geocoder(ctx).getFromLocation(picked.lat, picked.lng, 1)?.firstOrNull()
                    ?.let { a -> listOfNotNull(a.thoroughfare?.let { t -> a.subThoroughfare?.let { "$t $it" } ?: t }, a.locality).joinToString(", ") }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }
    val pick = com.buddy.app.core.designsystem.components.rememberPhotoCapture { uris -> uris.firstOrNull()?.let { photo = it } }
    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = BuddyColor.Brand) }
            Text("Agregar ubicación", style = BuddyType.Headline, color = BuddyColor.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Paso 2 de 2", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            Text("UBICACIÓN ELEGIDA", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp), color = BuddyColor.InkMuted)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BuddyColor.Surface).clickable(onClick = onBack).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage), contentAlignment = Alignment.Center) {
                    com.buddy.app.features.home.inicio.LocationPin(Modifier.size(16.dp))
                }
                Text(picked.label ?: street ?: "Punto en el mapa", style = BuddyType.FootnoteBold, color = BuddyColor.Ink,
                    maxLines = 2, modifier = Modifier.weight(1f))
                Text("Cambiar", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold), color = BuddyColor.Brand)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
            }
            Text("FOTO DE PORTADA", style = BuddyType.Caption1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp), color = BuddyColor.InkMuted,
                modifier = Modifier.padding(top = 8.dp))
            Text("Es la foto que verán los viajeros en Inicio.", style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            // La misma tarjeta (y medidas) de los lugares en Inicio.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.clickable(enabled = !saving) { pick() }
                    .semantics { contentDescription = if (photo == null) "Elegir foto de portada" else "Cambiar foto de portada" }) {
                    com.buddy.app.features.home.inicio.PlaceCard(
                        com.buddy.app.core.data.model.ApiPlaceCard(id = "preview", name = picked.label ?: businessName,
                            lat = picked.lat, lng = picked.lng, coverUrl = photo?.toString()),
                        large = false)
                    if (photo == null) Box(Modifier.matchParentSize().padding(bottom = 44.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(BuddyColor.Brand), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            val ready = photo != null && !saving
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp).clip(CircleShape)
                    .background(if (ready) BuddyColor.Ink else BuddyColor.InkMuted.copy(alpha = 0.4f))
                    .clickable(enabled = ready) {
                        saving = true
                        onSave(photo!!) { ok -> if (!ok) saving = false }
                    },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (saving) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = BuddyColor.InkInverse)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Guardar ubicación", style = BuddyType.Headline, color = BuddyColor.InkInverse)
            }
        }
    }
}
