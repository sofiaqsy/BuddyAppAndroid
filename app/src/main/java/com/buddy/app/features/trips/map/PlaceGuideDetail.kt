package com.buddy.app.features.trips.map

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.FincasProduct
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import com.buddy.app.core.designsystem.components.FollowViewer
import com.buddy.app.core.designsystem.components.photoSourceModifier
import com.buddy.app.features.home.inicio.categoryIcon

/** Alto de una foto de la pestaña Fotos: mismo ancho que un producto, más alta. */
private val FotoAlto = 160.dp

/**
 * Ficha del lugar DENTRO del panel, espejo de PlaceGuideDetailSheet (iOS,
 * 23 Nov 2026): nombre con la categoría debajo, "Cómo llegar", pestañas
 * Productos (primero) · Información · Fotos.
 * - Productos: una tarjeta por PRESENTACIÓN (250 g y 1 kg son dos tarjetas).
 * - Cerrado, una fila; abierto, una cuadrícula de 3 con el MISMO tamaño de
 *   tarjeta. Fotos igual (114 x 160).
 * - Sin X: la pantalla ya tiene su botón de volver.
 */
@Composable
fun PlaceGuideDetail(
    spot: ApiGuideSpot,
    presenceText: String?,
    fotos: List<FotoDeLugar>,
    isLoadingFotos: Boolean,
    canRecommend: Boolean,
    onAddPhoto: () -> Unit,
    products: List<FincasProduct>,
    isLoadingProducts: Boolean,
    expanded: Boolean,
    onNavigate: () -> Unit,
    onOpenPhoto: (FotoDeLugar) -> Unit,
    onOpenProductPhoto: (url: String) -> Unit,
    gridState: LazyGridState,
    /** "Preguntar al negocio": con la tarjeta del producto, o null para el lugar. */
    onAsk: (FincasProduct?, FincasProduct.Presentation?) -> Unit = { _, _ -> },
    /** El producto que va primero (tocado en Inicio), para que el visor use el mismo orden. */
    onFocus: (String?) -> Unit = {},
) {
    var tab by remember(spot.id) { mutableStateOf(0) }
    var userPickedTab by remember(spot.id) { mutableStateOf(false) }
    // Sin productos: Fotos primero y abre en Fotos (iOS, 24 Nov 2026).
    // Solo después de que la carga empezó y terminó: antes de empezar, la
    // lista vacía no quiere decir "sin productos".
    var sawLoading by remember(spot.id) { mutableStateOf(false) }
    if (isLoadingProducts) sawLoading = true
    val noProducts = sawLoading && !isLoadingProducts && products.isEmpty()
    LaunchedEffect(spot.id, noProducts) { if (noProducts && !userPickedTab && tab == 0) tab = 2 }
    val focus = remember(spot.id) { ProductFocus.take(spot.id) }
    LaunchedEffect(spot.id, focus) { onFocus(focus) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                // Con letra grande el nombre baja a 2 líneas en vez de cortarse.
                Text(spot.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BuddyColor.Ink, maxLines = 2,
                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, lineHeight = 26.sp)
                spot.categoryName?.let { cat ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(categoryIcon(cat), null, Modifier.size(14.dp), tint = BuddyColor.InkMuted)
                        Text(cat, style = BuddyType.Footnote, color = BuddyColor.InkMuted, maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(BuddyColor.Brand)
                    .clickable(onClick = onNavigate).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.NearMe, null, Modifier.size(16.dp), tint = BuddyColor.InkInverse)
                Text("Cómo llegar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.InkInverse)
            }
        }

        if (presenceText != null) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BuddyColor.Accent))
                Text(presenceText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BuddyColor.Ink, maxLines = 1)
            }
        }

        val titulos = listOf("Productos", "Información", "Fotos")
        val orden = if (noProducts) listOf(2, 1, 0) else listOf(0, 1, 2)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp)) {
            orden.forEach { i -> val titulo = titulos[i]
                Column(
                    Modifier.weight(1f).clickable { tab = i; userPickedTab = true }
                        .semantics { selected = tab == i; role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Pestañas en una línea: con letra grande crecen como máximo 1.1x ("Información" entera).
                    val d = androidx.compose.ui.platform.LocalDensity.current
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides
                            androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.1f)),
                    ) {
                    Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                         color = if (tab == i) BuddyColor.Brand else BuddyColor.InkMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp)
                        .background(if (tab == i) BuddyColor.Brand else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = BuddyColor.Hairline)

        when (tab) {
            0 -> ProductosTab(products, isLoadingProducts, expanded, onOpenProductPhoto, gridState, focus, onAsk)
            1 -> InfoTabNew(spot, canAsk = products.isNotEmpty()) { onAsk(null, null) }
            else -> FotosTab(fotos, isLoadingFotos, canRecommend, expanded, onAddPhoto, onOpenPhoto, gridState)
        }
    }
}

@Composable
private fun InfoTabNew(spot: ApiGuideSpot, canAsk: Boolean, onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Solo lugares con negocio (tienen productos), como iOS.
        if (canAsk) com.buddy.app.features.businesschat.AskBusinessRow(onAsk)
        if (spot.estaPendiente) {
            Text("Este lugar está esperando aprobación. Por ahora solo tú lo ves.",
                 style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        } else {
            Text("Sin datos todavía. La guía de este lugar la construye su comunidad.",
                 style = BuddyType.Footnote, color = BuddyColor.InkMuted)
        }
    }
}

@Composable
private fun Vacio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxWidth().padding(top = 30.dp), Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.Brand, strokeWidth = 2.dp)
    }
}

@Composable
private fun ProductosTab(
    products: List<FincasProduct>,
    isLoading: Boolean,
    expanded: Boolean,
    onOpenPhoto: (String) -> Unit,
    gridState: LazyGridState,
    focus: String? = null,
    onAsk: (FincasProduct?, FincasProduct.Presentation?) -> Unit = { _, _ -> },
) {
    val entries = remember(products, focus) {
        val all = products.flatMap { p -> p.entries().map { p to it } }
        // El producto tocado en Inicio va primero.
        val i = all.indexOfFirst { (p, pr) -> "${p.id}-${pr?.id ?: ""}" == focus }
        if (i <= 0) all else listOf(all[i]) + all.filterIndexed { j, _ -> j != i }
    }
    // Tocar un producto en Inicio: además de ir primero, su foto se abre a
    // pantalla completa una vez (iOS, 24 Nov 2026).
    var focusOpened by remember(focus) { mutableStateOf(false) }
    LaunchedEffect(entries, focus) {
        if (focus == null || focusOpened) return@LaunchedEffect
        val e = entries.firstOrNull { (p, pr) -> "${p.id}-${pr?.id ?: ""}" == focus } ?: return@LaunchedEffect
        val url = e.first.coverFor(e.second) ?: return@LaunchedEffect
        focusOpened = true
        kotlinx.coroutines.delay(350)   // que la tarjeta exista y el visor crezca desde ella
        onOpenPhoto(url)
    }
    val owner = LocalOwnerActions.current
    when {
        isLoading -> Cargando()
        entries.isEmpty() && owner == null -> Vacio("Este lugar aún no muestra productos")
        expanded -> {
          // Al pasar de foto en el visor, la cuadrícula sigue al producto que se
          // ve; al cerrar, la foto vuelve a su tarjeta (como iOS).
          com.buddy.app.core.designsystem.components.FollowViewerGrid(gridState) { k ->
              entries.indexOfFirst { (p, pr) -> productSourceKey(p, pr) == k }
          }
          LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Dueño: "Añadir producto" primero (iOS).
            if (owner != null) item(key = "añadir-producto") { AddTile("Añadir producto", ProductCardSide, owner.onAddProduct) }
            items(entries.size, key = { i -> "${entries[i].first.id}-${entries[i].second?.id}" }) { i ->
                ProductTile(entries[i].first, entries[i].second, onOpenPhoto, onAsk)
            }
        }
        }
        else -> {
          val rowState = rememberLazyListState()
          FollowViewer(rowState) { k -> entries.indexOfFirst { (p, pr) -> productSourceKey(p, pr) == k } }
          LazyRow(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            state = rowState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (owner != null) item(key = "añadir-producto") { AddTile("Añadir producto", ProductCardSide, owner.onAddProduct) }
            items(entries, key = { (p, pr) -> "${p.id}-${pr?.id}" }) { (p, pr) -> ProductTile(p, pr, onOpenPhoto, onAsk) }
        }
        }
    }
}

/** Clave de la tarjeta de un producto en una presentación: el visor abre desde
 *  ella y, al cerrar, vuelve a ella aunque se esté viendo otra de sus fotos. */
fun productSourceKey(p: FincasProduct, pr: FincasProduct.Presentation?): String = "product-${p.id}-${pr?.id ?: ""}"

@Composable
private fun ProductTile(p: FincasProduct, pr: FincasProduct.Presentation?, onOpenPhoto: (String) -> Unit,
                        onAsk: (FincasProduct?, FincasProduct.Presentation?) -> Unit) {
    val url = p.coverFor(pr)
    Box {
        ProductCardFace(
            p, pr,
            photoModifier = if (url != null) photoSourceModifier(productSourceKey(p, pr)).clickable { onOpenPhoto(url) } else Modifier,
        )
        // Dueño viendo su lugar: lápiz para editar (iOS modo edición). Si no, la burbuja de preguntar.
        val edit = LocalProductEdit.current
        if (edit != null) Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(BuddyColor.Surface)
                .clickable { edit(p) }
                .semantics { contentDescription = "Editar ${p.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.material.icons.Icons.Outlined.Edit, null, tint = BuddyColor.Brand, modifier = Modifier.size(15.dp))
        } else Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(BuddyColor.Surface)
                .clickable { onAsk(p, pr) }
                .semantics { contentDescription = "Preguntar por ${p.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline, null, tint = BuddyColor.Brand, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun FotosTab(
    fotos: List<FotoDeLugar>,
    isLoading: Boolean,
    canRecommend: Boolean,
    expanded: Boolean,
    onAddPhoto: () -> Unit,
    onOpenPhoto: (FotoDeLugar) -> Unit,
    gridState: LazyGridState,
) {
    // Dueño: "Añadir foto" sube una foto de su lugar (iOS), aunque no haya entrado como viajero.
    val ownerFotos = LocalOwnerActions.current
    when {
        isLoading -> Cargando()
        fotos.isEmpty() && !canRecommend && LocalOwnerActions.current == null -> Vacio("Todavía no hay fotos de este lugar")
        expanded -> LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canRecommend || ownerFotos != null) item(key = "añadir") { AddTile(ownerFotos?.onAddPhoto ?: onAddPhoto) }
            items(fotos.size, key = { i -> "$i-${fotos[i].url}" }) { i -> Foto(fotos[i], onOpenPhoto) }
        }
        else -> {
            val rowState = rememberLazyListState()
            FollowViewer(rowState) { k ->
                val i = fotos.indexOfFirst { it.url == k }
                if (i < 0) -1 else i + (if (canRecommend || ownerFotos != null) 1 else 0)
            }
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                state = rowState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRecommend || ownerFotos != null) item(key = "añadir") { AddTile(ownerFotos?.onAddPhoto ?: onAddPhoto) }
                itemsIndexed(fotos, key = { i, f -> "$i-${f.url}" }) { _, foto -> Foto(foto, onOpenPhoto) }
            }
        }
    }
}

@Composable
private fun AddTile(onAddPhoto: () -> Unit) = AddTile("Añadir foto", FotoAlto, onAddPhoto)

/** Casilla punteada "+" (iOS): Añadir foto / Añadir producto. */
@Composable
private fun AddTile(label: String, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        Modifier.width(ProductCardSide).height(height).clip(RoundedCornerShape(14.dp)).background(BuddyColor.Surface)
            .drawBehind {
                drawRoundRect(color = BuddyColor.Border, cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(28.dp), tint = BuddyColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text(label, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Foto(foto: FotoDeLugar, onOpenPhoto: (FotoDeLugar) -> Unit) {
    // Dueño: mantener presionada una foto suya → "Usar como portada" / "Quitar foto" (iOS).
    val owner = LocalOwnerActions.current
    val ownId = owner?.ownerPhotoIds?.get(foto.url)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box {
    if (ownId != null) {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (owner.coverId != ownId) DropdownMenuItem(text = { Text("Usar como portada") },
                leadingIcon = { Icon(Icons.Outlined.StarOutline, null) }, onClick = { menu = false; owner.onMakeCover(ownId) })
            DropdownMenuItem(text = { Text("Quitar foto", color = BuddyColor.ErrorRed) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = BuddyColor.ErrorRed) }, onClick = { menu = false; confirm = true })
        }
        if (confirm) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Quitar esta foto?") },
            text = { Text("Los visitantes ya no la verán.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = false; owner.onRemovePhoto(ownId) }) { Text("Quitar foto", color = BuddyColor.ErrorRed) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
    AsyncImage(
        model = foto.url,
        contentDescription = foto.autor?.let { "Foto de $it" },
        contentScale = ContentScale.Crop,
        onSuccess = { com.buddy.app.core.designsystem.components.PhotoSources.noteAspect(foto.url, it.painter.intrinsicSize) },
        modifier = Modifier.width(ProductCardSide).height(FotoAlto).clip(RoundedCornerShape(10.dp))
            .background(BuddyColor.SurfaceRaised)
            .then(photoSourceModifier(foto.url))
            .combinedClickable(onClick = { onOpenPhoto(foto) }, onLongClick = if (ownId != null) ({ menu = true }) else null),
    )
    }
}

/** Si el lugar es del negocio activo: abre el editor de ese producto (Android, 24 Nov 2026). */
val LocalProductEdit = androidx.compose.runtime.staticCompositionLocalOf<((FincasProduct) -> Unit)?> { null }

val LocalOwnerActions = androidx.compose.runtime.staticCompositionLocalOf<com.buddy.app.features.business.OwnerActions?> { null }
