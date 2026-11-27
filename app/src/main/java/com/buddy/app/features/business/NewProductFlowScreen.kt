package com.buddy.app.features.business

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.app.core.data.model.soles
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

private val NEW_UNITS = listOf("g", "kg", "ml", "L", "unidad")

/** Lado de la foto en "Nuevo producto": 30 % más grande que en Inicio (Keyla, 26 Nov 2026). */
private val PreviewSide = 148.dp

/**
 * "Nuevo producto" en 2 pasos, igual que iOS (Keyla, 26 Nov 2026):
 * 1 Foto, 2 Nombre y precio. La tarjeta "Así lo verán" (la de Inicio) queda en
 * el mismo lugar en los dos pasos, sobre el botón; en el paso 1 solo la foto.
 * Sin descripción al crear. Se oculta mientras el teclado está abierto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewProductFlowScreen(
    farmId: String, modifier: Modifier = Modifier,
    /** Producto ya creado: solo agrega una presentación ("Nueva presentación", 26 Nov 2026). */
    existing: com.buddy.app.core.data.model.FincasProduct? = null,
    onClose: (changed: Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var step by remember { mutableIntStateOf(1) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("250") }
    var unit by remember { mutableStateOf("g") }
    var price by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var unitOpen by remember { mutableStateOf(false) }
    var examplesOpen by remember { mutableStateOf(false) }
    // Llaves fijas: reintentar "Crear" no duplica nada.
    val productKey = remember { UUID.randomUUID().toString() }
    val presKey = remember { UUID.randomUUID().toString() }
    var createdProductId by remember { mutableStateOf<String?>(null) }
    var createdPresId by remember { mutableStateOf<String?>(null) }
    val picker = com.buddy.app.core.designsystem.components.rememberPhotoCapture { uris -> uris.firstOrNull()?.let { photo = it } }

    fun num(t: String) = t.replace(",", ".").trim().toDoubleOrNull()
    val canGoOn = when (step) {
        1 -> photo != null
        else -> (existing != null || name.isNotBlank()) && (num(size) ?: 0.0) > 0 && (num(price) ?: -1.0) >= 0
    }
    fun back() { focus.clearFocus(); if (step == 2) step = 1 else onClose(false) }
    BackHandler(enabled = !busy) { back() }

    fun create() {
        val s = num(size) ?: return; val p = num(price) ?: return; val img = photo ?: return
        error = null; busy = true
        scope.launch {
            runCatching {
                val pid = existing?.id ?: createdProductId ?: FincasAuth.createProduct(ctx, farmId, name.trim(), "", productKey).also { createdProductId = it }
                val presId = createdPresId ?: FincasAuth.createPresentation(
                    ctx, farmId, pid,
                    JSONObject().put("packSize", s).put("unit", unit).put("price", Math.round(p * 100) / 100.0).put("visible", true),
                    presKey,
                )?.also { createdPresId = it } ?: throw FincasAuth.FincasError("No recibimos la presentación. Revisa tu lista de productos.")
                val jpeg = withContext(Dispatchers.IO) { productJpeg(ctx, img) }
                FincasAuth.uploadPresentationPhoto(ctx, farmId, pid, presId, jpeg)
            }.onSuccess { onClose(true) }
             .onFailure { error = it.message ?: "No pudimos guardar. Intenta de nuevo."; busy = false }
        }
    }

    Column(modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding().imePadding()) {
        // Encabezado: título, barras de pasos y contador (igual que iOS).
        Text(if (existing == null) "Nuevo producto" else "Nueva presentación", style = BuddyType.Headline, color = BuddyColor.Ink,
             modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
             textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..2).forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (i <= step) BuddyColor.Brand else BuddyColor.Hairline))
                }
            }
            Text("Paso $step de 2", style = BuddyType.Caption1, color = BuddyColor.InkMuted)
            if (step == 1) Text(if (existing == null) "Foto del producto" else "Foto de la presentación", style = BuddyType.Title2, color = BuddyColor.Brand)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (step == 2) {
                Text(if (existing == null) "Nombre y precio" else "Tamaño y precio", style = BuddyType.Title2, color = BuddyColor.Brand)
                if (existing == null) PillField(name, { name = it }, "Ej. Café tostado", Modifier.fillMaxWidth())
                // Los ejemplos van detrás de un "?" (Keyla, 26 Nov 2026).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Cuánto trae y cuánto cuesta?", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                    Box {
                        IconButton(onClick = { examplesOpen = true }, modifier = Modifier.semantics { contentDescription = "Ver ejemplos" }) {
                            Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = BuddyColor.Brand)
                        }
                        DropdownMenu(expanded = examplesOpen, onDismissRequest = { examplesOpen = false }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Ejemplos", style = BuddyType.FootnoteBold, color = BuddyColor.Ink)
                                Text("Café: 250 g a S/ 20", style = BuddyType.Footnote, color = BuddyColor.Ink)
                                Text("Llavero: 1 unidad a S/ 10", style = BuddyType.Footnote, color = BuddyColor.Ink)
                                Text("Miel: 500 ml a S/ 25", style = BuddyType.Footnote, color = BuddyColor.Ink)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Labeled("Cantidad", Modifier.weight(1.2f)) {
                        PillField(size, { size = it }, "250", Modifier.fillMaxWidth(), number = true)
                    }
                    Labeled("Medida", Modifier.weight(0.8f)) {
                        Box {
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(BuddyColor.Surface)
                                    .clickable { unitOpen = true }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(unit, style = BuddyType.Callout, color = BuddyColor.Ink)
                                Icon(Icons.Filled.KeyboardArrowDown, null, tint = BuddyColor.Brand, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = unitOpen, onDismissRequest = { unitOpen = false }) {
                                NEW_UNITS.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitOpen = false }) }
                            }
                        }
                    }
                    Labeled("Precio", Modifier.weight(1.2f)) {
                        PillField(price, { price = it }, "20.00", Modifier.fillMaxWidth(), number = true, prefix = "S/")
                    }
                }
                error?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.ErrorRed) }
            }
        }

        // La tarjeta en el mismo lugar en los dos pasos; se oculta con el teclado abierto.
        if (step == 1 || !WindowInsets.isImeVisible) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                   verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ASÍ LO VERÁN", style = BuddyType.Eyebrow, color = BuddyColor.InkMuted,
                     modifier = Modifier.alpha(if (step == 1) 0f else 1f))
                Column(Modifier.width(PreviewSide), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.size(PreviewSide).clip(RoundedCornerShape(10.dp)).background(BuddyColor.PaleSage)
                            .clickable { focus.clearFocus(); if (step == 1) picker() else step = 1 }
                            .semantics { contentDescription = if (photo == null) "Elegir foto" else "Cambiar foto" },
                        contentAlignment = Alignment.Center,
                    ) {
                        val img = photo
                        if (img != null) AsyncImage(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Box(Modifier.size(44.dp).clip(CircleShape).background(BuddyColor.Brand), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = BuddyColor.InkInverse)
                        }
                    }
                    // Paso 1: solo la foto, sin mover su lugar.
                    Column(Modifier.alpha(if (step == 1) 0f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(existing?.name ?: name.trim().ifEmpty { "Nombre del producto" }, style = BuddyType.Caption1, color = BuddyColor.Ink,
                             maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                            num(price)?.let { Text(soles(it), style = BuddyType.FootnoteBold, color = BuddyColor.Brand, maxLines = 1) }
                            if (size.isNotBlank()) Text("$size $unit", style = BuddyType.Caption1, color = BuddyColor.InkMuted, maxLines = 1)
                        }
                    }
                }
            }
        }

        // Volver y seguir, abajo, donde está el pulgar.
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(BuddyColor.Surface)
                    .clickable(enabled = !busy) { back() }.semantics { contentDescription = "Atrás" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BuddyColor.Ink, modifier = Modifier.size(20.dp)) }
            Box(
                Modifier.weight(1f).height(52.dp).clip(CircleShape)
                    .background(if (canGoOn && !busy) BuddyColor.Brand else BuddyColor.InkMuted.copy(alpha = 0.2f))
                    .clickable(enabled = canGoOn && !busy) { focus.clearFocus(); if (step == 1) step = 2 else create() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.InkInverse, strokeWidth = 2.dp)
                else Text(if (step == 1) "Siguiente" else if (existing == null) "Crear producto" else "Crear presentación", style = BuddyType.FootnoteBold,
                          color = if (canGoOn) BuddyColor.InkInverse else BuddyColor.InkMuted)
            }
        }
    }
}

@Composable
private fun Labeled(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = BuddyType.Caption1, color = BuddyColor.InkMuted, modifier = Modifier.padding(start = 12.dp))
        content()
    }
}

@Composable
private fun PillField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier,
                      number: Boolean = false, prefix: String? = null) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier,
        placeholder = { Text(placeholder, color = BuddyColor.InkMuted) },
        prefix = prefix?.let { p -> { Text("$p ", color = BuddyColor.InkMuted) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = BuddyColor.Surface, unfocusedContainerColor = BuddyColor.Surface,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            cursorColor = BuddyColor.Brand,
        ),
    )
}
