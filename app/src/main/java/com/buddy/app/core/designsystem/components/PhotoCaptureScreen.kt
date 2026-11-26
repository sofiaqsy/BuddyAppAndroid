package com.buddy.app.core.designsystem.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.buddy.app.core.designsystem.BuddyType
import java.io.File

/**
 * "Agregar foto" en toda la app (iOS PhotoCaptureView, 26 Nov 2026): la cámara se
 * abre sola y, en la misma pantalla, "Galería" para elegir fotos. Devuelve Uris
 * (de la cámara, un archivo en caché; de la galería, el del selector).
 */
@Composable
fun PhotoCaptureScreen(maxCount: Int = 1, onDismiss: () -> Unit, onPick: (List<android.net.Uri>) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { onPick(listOf(uri)); onDismiss() }
    }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, maxCount))) { uris ->
        if (uris.isNotEmpty()) { onPick(uris.take(maxCount)); onDismiss() }
    }
    fun openGallery() {
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (maxCount > 1) multi.launch(req) else single.launch(req)
    }
    val hasCamera = remember { ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val controller = remember {
        LifecycleCameraController(ctx).apply { setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE) }
    }
    DisposableEffect(granted) {
        if (granted && hasCamera) controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { onDismiss() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (granted && hasCamera) {
                AndroidView(factory = { c -> PreviewView(c).apply { this.controller = controller } }, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.align(Alignment.Center).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(34.dp))
                    Text(
                        when {
                            !hasCamera -> "La cámara no está disponible. Elige una foto de tu galería."
                            asked -> "Buddy no tiene permiso para usar la cámara. Actívalo en Ajustes, o elige una foto de tu galería."
                            else -> "Abriendo la cámara…"
                        },
                        style = BuddyType.Footnote, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                    )
                }
            }
            Box(
                Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 40.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onDismiss).semantics { contentDescription = "Cerrar" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = Color.White) }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 56.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier.size(72.dp, 60.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))
                        .clickable { openGallery() }.semantics { contentDescription = "Elegir de la galería" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White)
                    Text("Galería", color = Color.White, fontSize = 12.sp)
                }
                val ready = granted && hasCamera && !capturing
                Box(
                    Modifier.size(78.dp).border(4.dp, Color.White.copy(alpha = if (ready) 1f else 0.4f), CircleShape).padding(7.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = if (ready) 1f else 0.4f))
                        .clickable(enabled = ready) {
                            capturing = true
                            val file = File(ctx.cacheDir, "captura_${System.currentTimeMillis()}.jpg")
                            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(ctx),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                        capturing = false; onPick(listOf(android.net.Uri.fromFile(file))); onDismiss()
                                    }
                                    override fun onError(e: ImageCaptureException) { capturing = false }
                                })
                        }.semantics { contentDescription = "Tomar foto" },
                )
                Box(
                    Modifier.size(72.dp, 60.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))
                        .clickable(enabled = granted && hasCamera) {
                            controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        }.semantics { contentDescription = "Cambiar de cámara" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Cameraswitch, null, tint = Color.White.copy(alpha = if (granted && hasCamera) 1f else 0.4f)) }
            }
            if (capturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

/** Como un selector de fotos, pero abre la cámara con la galería en la misma
 *  pantalla. Devuelve la función que la abre. */
@Composable
fun rememberPhotoCapture(maxCount: Int = 1, onPick: (List<android.net.Uri>) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(onPick)
    if (open) PhotoCaptureScreen(maxCount, onDismiss = { open = false }) { latest(it) }
    return remember { { open = true } }
}
