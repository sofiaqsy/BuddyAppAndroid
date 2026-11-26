package com.buddy.app.features.trips

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.buddy.app.core.designsystem.BuddyColor
import com.buddy.app.core.designsystem.BuddyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recomendar un lugar NUEVO (iOS RecomendarFotosView, 26 Nov 2026): fotos primero;
 * el lugar y la recomendación se crean al tocar "Publicar". Si sales sin publicar,
 * no queda nada creado.
 */
@Composable
fun RecomendarFotosScreen(lugar: NuevoLugar, onClose: () -> Unit, onPublish: (List<ByteArray>, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fotos = remember { mutableStateListOf<android.net.Uri>() }
    var publicando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val max = 10
    val picker = com.buddy.app.core.designsystem.components.rememberPhotoCapture(max) { uris ->
        fotos.addAll(uris.take(max - fotos.size))
    }
    BackHandler(enabled = !publicando) { onClose() }

    Column(Modifier.fillMaxSize().background(BuddyColor.Canvas).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, enabled = !publicando) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = BuddyColor.Brand) }
            Text("Recomendar lugar", style = BuddyType.Headline, color = BuddyColor.Ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lugar.nombre, style = BuddyType.Title3, color = BuddyColor.Ink)
            Text("Agrega fotos de este lugar para recomendarlo.", style = BuddyType.Body, color = BuddyColor.InkMuted)
            LazyVerticalGrid(GridCells.Adaptive(104.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                itemsIndexed(fotos) { i, uri ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (!publicando) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).clip(CircleShape)
                            .background(BuddyColor.Ink.copy(alpha = 0.6f)).clickable { fotos.removeAt(i) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Quitar foto", tint = BuddyColor.InkInverse, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (fotos.size < max) item {
                    Column(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(BuddyColor.SageTint)
                        .clickable(enabled = !publicando) { picker() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.Add, null, tint = BuddyColor.Brand)
                        Text("Añadir foto", style = BuddyType.Footnote, color = BuddyColor.Brand)
                    }
                }
            }
        }
        error?.let { Text(it, style = BuddyType.Footnote, color = BuddyColor.ErrorRed, modifier = Modifier.padding(horizontal = 20.dp)) }
        Box(
            Modifier.padding(20.dp).fillMaxWidth().height(52.dp).clip(CircleShape)
                .background(if (fotos.isEmpty() || publicando) BuddyColor.InkMuted else BuddyColor.Ink)
                .clickable(enabled = fotos.isNotEmpty() && !publicando) {
                    publicando = true; error = null
                    scope.launch {
                        val jpegs = withContext(Dispatchers.IO) {
                            fotos.mapNotNull { runCatching { com.buddy.app.features.business.Jpeg.from(ctx, it) }.getOrNull() }
                        }
                        onPublish(jpegs) { ok ->
                            publicando = false
                            if (ok) onClose() else error = "No pudimos publicar. Revisa tu conexión e intenta de nuevo."
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (publicando) CircularProgressIndicator(Modifier.size(22.dp), color = BuddyColor.InkInverse, strokeWidth = 2.dp)
            else Text("Publicar", style = BuddyType.FootnoteBold, color = BuddyColor.InkInverse)
        }
    }
}
