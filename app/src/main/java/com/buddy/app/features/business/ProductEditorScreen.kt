package com.buddy.app.features.business

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.FincasProduct
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Unidades de un tamaño: las de productos y las de servicios (día, viaje). */
private val UNITS = listOf("g", "kg", "ml", "L", "unidad", "día", "viaje")

private class SizeDraft(
    val id: String?,                // null = tamaño nuevo
    size: String, unit: String, price: String, stock: String, visible: Boolean,
    photoUrl: String?,
    /** Id de la foto en Apartalo: solo así se puede quitar. */
    var photoId: String? = null,
) {
    var photoUrl by mutableStateOf(photoUrl)
    var size by mutableStateOf(size)
    var unit by mutableStateOf(unit)
    var price by mutableStateOf(price)
    var stock by mutableStateOf(stock)
    var visible by mutableStateOf(visible)
    var newPhoto by mutableStateOf<Uri?>(null)
    val key = UUID.randomUUID().toString()   // misma llave si se reintenta crear
}

private fun num(d: Double?) = d?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""

/**
 * Crear o editar un producto de Apartalo (iOS "Nuevo producto" / "Editar
 * producto", Android fase 3, 24 Nov 2026). Los cambios van directo a Apartalo.
 */
@Composable
fun ProductEditorScreen(
    farmId: String, product: FincasProduct?, modifier: Modifier = Modifier,
    /** Desde una presentación de la lista: solo esa, sin nombre ni descripción (iOS, 26 Nov 2026). */
    focusPresentationId: String? = null,
    /** Desde "+ Agregar presentación": una nueva vacía, sola. */
    addPresentation: Boolean = false,
    onClose: (changed: Boolean) -> Unit,
) {
    // Producto nuevo: el flujo de 2 pasos, igual que iOS (Keyla, 26 Nov 2026).
    if (product == null && focusPresentationId == null && !addPresentation) {
        NewProductFlowScreen(farmId, modifier, onClose = onClose); return
    }
    if (product != null && addPresentation) {
        NewProductFlowScreen(farmId, modifier, existing = product, onClose = onClose); return
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var description by remember { mutableStateOf(product?.description.orEmpty()) }
    val sizes = remember {
        mutableStateListOf<SizeDraft>().apply {
            product?.presentations.orEmpty().forEach { p ->
                add(SizeDraft(p.id, num(p.packSize), p.unit ?: "g", num(p.price), p.stock?.toString() ?: "", p.visible != false, p.ownPhoto,
                    p.photos?.firstOrNull { it.url == p.ownPhoto }?.id))
            }
            if (isEmpty()) add(SizeDraft(null, "250", "g", "", "", true, null))
            if (addPresentation && product != null) add(SizeDraft(null, "", "g", "", "", true, null))
        }
    }
    // Llave del borrador a mostrar solo (null = producto completo).
    val focusKey = remember {
        when {
            addPresentation && product != null -> sizes.last().key
            focusPresentationId != null -> sizes.firstOrNull { it.id == focusPresentationId }?.key
            else -> null
        }
    }
    val shownSizes = if (focusKey != null) sizes.filter { it.key == focusKey } else sizes
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }
    /** Una foto quitada ya cambió el producto: al volver, la lista se recarga. */
    var changedSomething by remember { mutableStateOf(false) }
    val productKey = remember { UUID.randomUUID().toString() }
    var createdId by remember { mutableStateOf(product?.id) }
    // Nuevo producto en 2 pasos (Keyla, 26 Nov 2026): 1 Foto (4:5, como en Inicio), 2 Nombre y precio.
    val stepped = product == null && focusKey == null
    var step by remember { mutableStateOf(if (stepped) 1 else 2) }
    val firstPhotoPicker = com.buddy.app.core.designsystem.components.rememberPhotoCapture { uris ->
        uris.firstOrNull()?.let { sizes.first().newPhoto = it }
    }
    fun back() { if (stepped && step == 2) step = 1 else onClose(changedSomething) }
    BackHandler(enabled = !saving) { back() }

    fun save() {
        error = null
        val n = name.trim()
        if (n.isEmpty()) { error = "Escribe el nombre del producto."; return }
        shownSizes.forEach { s ->
            if (s.size.replace(",", ".").toDoubleOrNull()?.let { it > 0 } != true) { error = "Revisa el tamaño de cada presentación."; return }
            if (s.price.replace(",", ".").toDoubleOrNull()?.let { it >= 0 } != true) { error = "Escribe el precio de cada presentación."; return }
        }
        saving = true
        scope.launch {
            runCatching {
                val pid = createdId ?: FincasAuth.createProduct(ctx, farmId, n, description.trim(), productKey).also { createdId = it }
                if (product != null && (n != product.name || description.trim() != product.description.orEmpty()))
                    FincasAuth.updateProduct(ctx, farmId, pid, JSONObject().put("name", n).put("description", description.trim()))
                shownSizes.forEach { s ->
                    val fields = JSONObject()
                        .put("packSize", s.size.replace(",", ".").toDouble())
                        .put("unit", s.unit)
                        .put("price", s.price.replace(",", ".").toDouble())
                        .put("visible", s.visible)
                    s.stock.toIntOrNull()?.let { fields.put("stock", it) }
                    val presId = s.id ?: FincasAuth.createPresentation(ctx, farmId, pid, fields, s.key)
                    if (s.id != null) FincasAuth.updatePresentation(ctx, farmId, pid, s.id, fields)
                    val photo = s.newPhoto
                    if (photo != null && presId != null) {
                        val jpeg = withContext(Dispatchers.IO) { toJpeg(ctx, photo) }
                        FincasAuth.uploadPresentationPhoto(ctx, farmId, pid, presId, jpeg)
                    }
                }
            }.onSuccess { onClose(true) }
             .onFailure { error = it.message ?: "No pudimos guardar. Intenta de nuevo."; saving = false }
        }
    }

    // Encima de la barra de pestañas: recibe su margen (modifier) para que "Guardar" no quede tapado.
    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(BuddyColor.Surface)
                    .clickable(enabled = !saving) { back() }.semantics { contentDescription = "Atrás" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BuddyColor.Brand, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    when {
                        focusKey != null && addPresentation -> "Nueva presentación"
                        focusKey != null -> "Editar presentación"
                        product == null -> "Nuevo producto"
                        else -> "Editar producto"
                    },
                    style = BuddyType.Title3, color = BuddyColor.Ink,
                )
                if (stepped) Text("Paso $step de 2 · " + if (step == 1) "Foto del producto" else "Nombre y precio",
                    style = BuddyType.Footnote, color = BuddyColor.InkMuted)
                if (focusKey != null) Text(name, style = BuddyType.Footnote, color = BuddyColor.InkMuted)
            }
        }
        Column(
            Modifier.weight(1f).then(if (step == 1) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (step == 1) {
            val photo = sizes.first().newPhoto
            // La foto abajo, al alcance del pulgar (Keyla, 26 Nov 2026).
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(148.dp)
                    .clip(RoundedCornerShape(10.dp)).background(BuddyColor.SageTint)
                    .clickable { firstPhotoPicker() }
                    .semantics { contentDescription = if (photo == null) "Elegir foto" else "Cambiar foto" },
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.size(52.dp).clip(CircleShape).background(BuddyColor.Brand), contentAlignment = Alignment.Center) {
                    Text("+", style = BuddyType.Title3, color = BuddyColor.InkInverse)
                }
            }
          } else {
            if (focusKey == null) {
                Field("Nombre", name, { name = it })
                // Sin descripción al crear (Keyla, 26 Nov 2026).
                if (product != null) Field("Descripción (opcional)", description, { description = it.take(500) }, singleLine = false)
                Text("Tamaños y precios", style = BuddyType.Headline, color = BuddyColor.Ink)
            }
            shownSizes.forEach { s ->
                SizeCard(s, removable = focusKey == null && s.id == null && sizes.size > 1, onRemove = { sizes.remove(s) },
                    onRemovePhoto = {
                        // Foto nueva (aún no subida): se descarta. Foto guardada: se quita en Apartalo.
                        if (s.newPhoto != null) s.newPhoto = null
                        else {
                            val pid = createdId; val presId = s.id; val photoId = s.photoId
                            if (pid != null && presId != null && photoId != null) scope.launch {
                                // Indicador mientras se quita: con mala señal tarda (Keyla, 26 Nov 2026).
                                saving = true
                                runCatching { FincasAuth.deletePresentationPhoto(ctx, farmId, pid, presId, photoId) }
                                    .onSuccess { s.photoUrl = null; s.photoId = null; changedSomething = true }
                                    .onFailure { error = it.message }
                                saving = false
                            } else error = "Esta foto todavía no se puede quitar desde Buddy."
                        }
                    })
            }
            if (focusKey == null) Text(
                "+ Añadir tamaño", style = BuddyType.FootnoteBold, color = BuddyColor.Brand,
                modifier = Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(BuddyColor.SageTint)
                    .clickable { sizes.add(SizeDraft(null, "1", "unidad", "", "", true, null)) }
                    .wrapContentHeight(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (product != null && focusKey == null) {
                Text(
                    "Quitar producto", style = BuddyType.FootnoteBold, color = BuddyColor.ErrorRed,
                    modifier = Modifier.padding(vertical = 8.dp).clickable { confirmArchive = true },
                )
            }
            Spacer(Modifier.height(8.dp))
          }
        }
        error?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.ErrorRed, modifier = Modifier.padding(horizontal = 20.dp)) }
        val canGo = !saving && (step == 2 || sizes.first().newPhoto != null)
        Box(
            Modifier.padding(20.dp).fillMaxWidth().height(52.dp).clip(CircleShape)
                .background(if (canGo) BuddyColor.Brand else BuddyColor.BrandDisabled)
                .clickable(enabled = canGo) { if (step == 1) step = 2 else save() },
            contentAlignment = Alignment.Center,
        ) {
            if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.InkInverse, strokeWidth = 2.dp)
            else Text(if (step == 1) "Siguiente" else if (product == null) "Crear producto" else "Guardar cambios", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
        }
    }

    if (confirmArchive && product != null) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("¿Quitar este producto?") },
            text = { Text("Dejará de verse en Buddy. No se borra: queda oculto y puedes volver a mostrarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false; saving = true
                    scope.launch {
                        runCatching { FincasAuth.archiveProduct(ctx, farmId, product.id) }
                            .onSuccess { onClose(true) }
                            .onFailure { error = it.message; saving = false }
                    }
                }) { Text("Quitar", color = BuddyColor.ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SizeCard(s: SizeDraft, removable: Boolean, onRemove: () -> Unit, onRemovePhoto: () -> Unit) {
    var photoMenu by remember { mutableStateOf(false) }
    var confirmPhoto by remember { mutableStateOf(false) }
    // Cámara con la galería en la misma pantalla (26 Nov 2026).
    val picker = com.buddy.app.core.designsystem.components.rememberPhotoCapture { uris -> uris.firstOrNull()?.let { s.newPhoto = it } }
    var unitOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(BuddyColor.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Foto arriba y campos a lo ancho: con letra grande las etiquetas ya no se parten.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(BuddyColor.SurfaceRaised)
                    // Tocar: elegir otra foto. Mantener presionada: "Quitar foto" (iOS).
                    .combinedClickable(
                        onClick = { picker() },
                        onLongClick = { if (s.newPhoto != null || s.photoUrl != null) photoMenu = true },
                    )
                    .semantics { contentDescription = "Foto de este tamaño" },
                contentAlignment = Alignment.Center,
            ) {
                val model: Any? = s.newPhoto ?: s.photoUrl
                if (model != null) AsyncImage(model, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Outlined.AddAPhoto, null, tint = BuddyColor.Brand)
                DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                    DropdownMenuItem(text = { Text("Quitar foto", color = BuddyColor.ErrorRed) },
                        onClick = { photoMenu = false; confirmPhoto = true })
                }
                if (confirmPhoto) AlertDialog(
                    onDismissRequest = { confirmPhoto = false },
                    title = { Text("¿Quitar esta foto?") },
                    text = { Text("Los visitantes ya no la verán.") },
                    confirmButton = { TextButton(onClick = { confirmPhoto = false; onRemovePhoto() }) { Text("Quitar foto", color = BuddyColor.ErrorRed) } },
                    dismissButton = { TextButton(onClick = { confirmPhoto = false }) { Text("Cancelar") } },
                )
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Cantidad", s.size, { s.size = it }, number = true, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        Field("Medida", s.unit, {}, readOnly = true, modifier = Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().clickable { unitOpen = true })
                        DropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                            UNITS.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { s.unit = u; unitOpen = false }) }
                        }
                    }
                }
                Field("Precio", s.price, { s.price = it }, number = true, prefix = "S/")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar en Buddy", style = BuddyType.Footnote, color = BuddyColor.Ink, modifier = Modifier.weight(1f))
            Switch(checked = s.visible, onCheckedChange = { s.visible = it },
                colors = SwitchDefaults.colors(checkedTrackColor = BuddyColor.Brand))
        }
        if (s.unit != "día" && s.unit != "viaje") Field("Stock (opcional)", s.stock, { s.stock = it.filter(Char::isDigit) }, number = true)
        if (removable) Text("Quitar este tamaño", style = BuddyType.Footnote, color = BuddyColor.ErrorRed,
            modifier = Modifier.clickable(onClick = onRemove))
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
                  singleLine: Boolean = true, number: Boolean = false, readOnly: Boolean = false, prefix: String? = null) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, readOnly = readOnly,
        prefix = prefix?.let { p -> { Text("$p ") } },
        minLines = if (singleLine) 1 else 3, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BuddyColor.Brand, focusedLabelColor = BuddyColor.Brand,
            unfocusedContainerColor = BuddyColor.Surface, focusedContainerColor = BuddyColor.Surface),
    )
}

/** JPEG de máx. 1600 px a 0.8, como iOS. */
internal fun productJpeg(ctx: android.content.Context, uri: Uri): ByteArray = toJpeg(ctx, uri)

private fun toJpeg(ctx: android.content.Context, uri: Uri): ByteArray {
    val src = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        ?: throw FincasAuth.FincasError("No pudimos leer la foto.")
    val scale = minOf(1f, 1600f / maxOf(src.width, src.height))
    val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
    return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
}
