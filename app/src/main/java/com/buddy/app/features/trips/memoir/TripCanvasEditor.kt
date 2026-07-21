package com.buddy.app.features.trips.memoir

import android.graphics.ImageDecoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.buddy.app.core.designsystem.BuddyColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class ActivePanel { NONE, EDGE, BORDER, LAYOUT }

// Colores exactos de iOS (DesignSystem.swift + AccentColor asset):
// Color.sand = Color.teal = brand #6E3B2D; Color.accent = sage #6F9885;
// Color.accentColor (stroke de selección) = asset #7A5C3A; .red = #FF3B30.
private val MemoirSand = Color(0xFF6E3B2D)
private val MemoirSelectionStroke = Color(0xFF7A5C3A)
private val MemoirSnapAccent = Color(0xFF6F9885)
private val MemoirRed = Color(0xFFFF3B30)

/** Paleta de bordes — espejo de memoirBorderPalette (iOS). */
private val borderPalette: List<Pair<String, Long>> = listOf(
    "Blanco" to 0xFFFFFFFF, "Negro" to 0xFF000000,
    "Crema" to 0xFFFAF2E0, "Azul" to 0xFF8CCCFF,
    "Rosa" to 0xFFFFB8C4, "Menta" to 0xFFB3F2CC, "Arena" to 0xFFEDD9AD,
)

/**
 * Editor de página — port de TripCanvasEditorView (iOS):
 * lienzo screenWidth×480dp sobre letterbox oscuro, gestos drag/pinch/rotate
 * con guías de snap + háptica, columna de herramientas, píldora inferior,
 * paneles de bordes/marcos/layouts, cámara y galería.
 */
@Composable
fun TripCanvasEditor(book: TripBookState) {
    val canvas = book.editingCanvas
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Página retrato como iOS: lienzo LÓGICO de máx 390dp (iPhone) × 480.
    // Las coordenadas de los items viven en ese espacio lógico (independiente
    // del dispositivo); en pantallas grandes el lienzo se ESCALA visualmente
    // para llenar el ancho manteniendo la proporción — responsive sin romper
    // las coordenadas guardadas.
    val conf = LocalConfiguration.current
    val pageW = MemoirPage.width(conf.screenWidthDp.toFloat())
    val pageH = MemoirPage.HEIGHT_DP
    val visualScale = minOf(conf.screenWidthDp / pageW, conf.screenHeightDp / pageH)
    canvas.canvasW = pageW
    canvas.canvasH = pageH

    var activePanel by remember { mutableStateOf(ActivePanel.NONE) }
    var showFreeCrop by remember { mutableStateOf(false) }

    // Espejo de onChange(vm.selectedItemId): deselección → cerrar panel
    androidx.compose.runtime.LaunchedEffect(canvas.selectedItemId) {
        if (canvas.selectedItemId == null) activePanel = ActivePanel.NONE
    }

    // Galería (hasta 10, como iOS)
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CanvasState.MAX_ITEMS),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val bmp = if (Build.VERSION.SDK_INT >= 28) {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                        }
                        BitmapEffects.limitedToMaxDimension(bmp, CanvasState.MAX_IMAGE_PX)
                    }.getOrNull()
                }
            }
            bitmaps.forEach { canvas.addPhoto(it) }
        }
    }
    // Cámara a resolución completa — espejo de UIImagePickerController(.camera)
    // con originalImage (TakePicturePreview solo devuelve un thumbnail).
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        if (ok && uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ ->
                            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }.getOrNull()
                }
                if (bmp != null) canvas.addPhoto(bmp)
            }
        }
    }
    fun launchCamera() {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF141414))) {

        // ── Lienzo (lógico 390×480, escalado visualmente para llenar) ───────
        Box(
            Modifier
                .align(Alignment.Center)
                .width(pageW.dp)
                .height(pageH.dp)
                .graphicsLayer {
                    scaleX = visualScale
                    scaleY = visualScale
                    transformOrigin = TransformOrigin.Center
                }
                .clip(RoundedCornerShape(0.dp))
                .background(Color(MemoirPersistence.rgbaToArgb(canvas.backgroundRGBA))),
        ) {
            // Capa de fondo: tap = deseleccionar. Es un hijo (no el padre) para
            // que los taps sobre items no lleguen aquí — solo el lienzo vacío.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            canvas.selectedItemId = null
                            activePanel = ActivePanel.NONE
                        }
                    },
            )
            // Fondo strip + velo blanco (como iOS)
            canvas.backgroundBitmap?.let { bg ->
                Image(
                    bitmap = bg.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.55f)))
            }

            // key(item.id): identidad por item, como el ForEach(id:) de iOS.
            // Sin esto, al subir un item de capa la lista reordenada reasigna
            // items por posición y REINICIA los pointerInput en pleno gesto —
            // el drag siguiente se "traga" el primer intento.
            canvas.sortedItems.forEachIndexed { stackIndex, item ->
                androidx.compose.runtime.key(item.id) {
                    CanvasItemView(
                        item = item,
                        isSelected = canvas.selectedItemId == item.id,
                        stackIndex = stackIndex,
                        canvas = canvas,
                        onGestureEnd = { canvas.snapGuides = emptySet() },
                        onSnap = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    )
                }
            }

            // Guías de snap — Color.accent (sage) 0.55, fade easeInOut 0.1s como iOS
            val vGuideAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (SnapGuide.verticalCenter in canvas.snapGuides) 0.55f else 0f,
                animationSpec = androidx.compose.animation.core.tween(100),
                label = "vGuide",
            )
            val hGuideAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (SnapGuide.horizontalCenter in canvas.snapGuides) 0.55f else 0f,
                animationSpec = androidx.compose.animation.core.tween(100),
                label = "hGuide",
            )
            Canvas(Modifier.fillMaxSize().zIndex(999f)) {
                if (vGuideAlpha > 0f) {
                    drawLine(
                        MemoirSnapAccent.copy(alpha = vGuideAlpha),
                        Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx(),
                    )
                }
                if (hGuideAlpha > 0f) {
                    drawLine(
                        MemoirSnapAccent.copy(alpha = hGuideAlpha),
                        Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
                    )
                }
            }

            // Lienzo vacío: invitación directa
            if (canvas.items.isEmpty() && !canvas.isProcessing) {
                // Icono 34 light + teal (=brand), texto callout inkMuted — como iOS
                Column(
                    Modifier
                        .fillMaxSize()
                        .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.AddPhotoAlternate, contentDescription = null,
                        Modifier.size(34.dp), tint = BuddyColor.Brand,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Toca para agregar tus fotos", color = BuddyColor.InkMuted, fontSize = 16.sp)
                }
            }
        }

        // ── Top bar — padding top 56 como iOS (ignoresSafeArea) ─────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .clickable {
                        activePanel = ActivePanel.NONE
                        book.exitEdit(pageW, pageH)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Volver", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${book.currentPageIndex + 1} / ${book.pages.size}",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        // ── Columna de herramientas (item seleccionado) ──────────────────────
        // Transición: entra/sale por la derecha con fade — spring(0.32, 0.8) en iOS
        androidx.compose.animation.AnimatedVisibility(
            visible = canvas.selectedItemId != null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp, bottom = 110.dp),
            enter = androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f),
            ) { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f),
            ) { it } + androidx.compose.animation.fadeOut(),
        ) {
            val selected = canvas.selectedItem
            Column(
                Modifier
                    .shadow(12.dp, RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF292929).copy(alpha = 0.96f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(vertical = 10.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (selected != null) {
                    if (selected.isSticker) {
                        ToolButton(Icons.Filled.ImageIcon, "Revertir", tint = BuddyColor.Brand) {
                            activePanel = ActivePanel.NONE
                            canvas.revertToPhoto(selected.id)
                        }
                    } else {
                        ToolButton(Icons.Filled.AutoFixHigh, "Sticker") {
                            activePanel = ActivePanel.NONE
                            scope.launch { canvas.makeSticker(selected.id) }
                        }
                    }
                    ToolDivider()
                    ToolButton(Icons.Filled.CropSquare, "Marco", active = activePanel == ActivePanel.BORDER) {
                        activePanel = if (activePanel == ActivePanel.BORDER) ActivePanel.NONE else ActivePanel.BORDER
                    }
                    ToolButton(Icons.Filled.GridView, "Diseño", active = activePanel == ActivePanel.LAYOUT) {
                        activePanel = if (activePanel == ActivePanel.LAYOUT) ActivePanel.NONE else ActivePanel.LAYOUT
                    }
                    if (!selected.isSticker) {
                        ToolButton(Icons.Filled.ContentCut, "Recortar") {
                            activePanel = ActivePanel.NONE
                            showFreeCrop = true
                        }
                    }
                    ToolButton(Icons.Filled.CopyAll, "Copiar") { canvas.duplicateItem(selected.id) }
                    if (!selected.isSticker) {
                        ToolButton(Icons.Filled.Crop, "Bordes", active = activePanel == ActivePanel.EDGE) {
                            activePanel = if (activePanel == ActivePanel.EDGE) ActivePanel.NONE else ActivePanel.EDGE
                        }
                    }
                    ToolDivider()
                    ToolButton(Icons.Filled.Delete, "Borrar", tint = MemoirRed) {
                        canvas.deleteItem(selected.id)
                        activePanel = ActivePanel.NONE
                    }
                }
            }
        }

        // ── Panel + píldora inferior ─────────────────────────────────────────
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Panel activo: entra/sale por abajo con fade — spring(0.35, 0.78) en iOS.
            // lastPanel conserva el contenido durante la animación de salida.
            var lastPanel by remember { mutableStateOf(ActivePanel.EDGE) }
            if (activePanel != ActivePanel.NONE) lastPanel = activePanel
            androidx.compose.animation.AnimatedVisibility(
                visible = activePanel != ActivePanel.NONE,
                enter = androidx.compose.animation.slideInVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 320f),
                ) { it } + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.78f, stiffness = 320f),
                ) { it } + androidx.compose.animation.fadeOut(),
            ) {
                when (lastPanel) {
                    ActivePanel.EDGE -> EdgePanel(canvas) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    ActivePanel.BORDER -> BorderPanel(canvas, scope) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                    ActivePanel.LAYOUT -> LayoutPanel(canvas) {
                        haptic.performHapticFeedbackType()
                        activePanel = ActivePanel.NONE
                    }
                    ActivePanel.NONE -> {}
                }
            }

            // Píldora principal: ← | Cámara | Galería | Guardar | →
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillNav(Icons.Filled.ChevronLeft, enabled = book.currentPageIndex > 0) {
                    activePanel = ActivePanel.NONE; book.navigateToPrevious()
                }
                PillDivider()
                PillAction(Icons.Filled.PhotoCamera, "Cámara") {
                    activePanel = ActivePanel.NONE; launchCamera()
                }
                PillDivider()
                PillAction(Icons.Filled.AddPhotoAlternate, "Galería") {
                    activePanel = ActivePanel.NONE
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                PillDivider()
                PillAction(Icons.Filled.CheckCircle, "Guardar", tint = BuddyColor.Brand) {
                    activePanel = ActivePanel.NONE; book.exitEdit(pageW, pageH)
                }
                PillDivider()
                PillNav(Icons.Filled.ChevronRight, enabled = book.currentPageIndex < book.pages.size - 1) {
                    activePanel = ActivePanel.NONE; book.navigateToNext()
                }
            }
        }

        // ── Overlays — dims exactos de iOS (0.45/28/20 vs 0.55/32/22) ───────
        if (canvas.isProcessing) ProcessingOverlay("Recortando sujeto…", scrim = 0.45f, boxPadding = 28.dp, corner = 20.dp)
        if (book.isLoadingPage) ProcessingOverlay("Cargando portada…", scrim = 0.55f, boxPadding = 32.dp, corner = 22.dp)

        canvas.errorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { canvas.errorMessage = null },
                title = { Text("Error") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { canvas.errorMessage = null }) { Text("OK") }
                },
            )
        }
    }

    // ── FreeCrop fullscreen ──────────────────────────────────────────────────
    if (showFreeCrop) {
        val sel = canvas.selectedItem
        if (sel != null && !sel.isSticker) {
            FreeCropScreen(
                image = sel.bitmap,
                original = sel.originalBitmap,
                onCancel = { showFreeCrop = false },
                onApply = { cropped ->
                    canvas.setCroppedImage(sel.id, cropped)
                    showFreeCrop = false
                },
            )
        } else showFreeCrop = false
    }
}

// Pequeño helper para no repetir el import del tipo de háptica en LayoutPanel
private fun androidx.compose.ui.hapticfeedback.HapticFeedback.performHapticFeedbackType() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

// ── Item del lienzo ──────────────────────────────────────────────────────────

/**
 * Port de CanvasItemView: base 180dp de ancho, escala/rotación con
 * graphicsLayer, drag+pinch+rotate simultáneos, botón X a tamaño constante.
 * El pan llega en coordenadas locales del layer: se rota y escala para
 * convertirlo al espacio del lienzo antes del snap.
 */
@Composable
private fun CanvasItemView(
    item: CollageItem,
    isSelected: Boolean,
    stackIndex: Int,
    canvas: CanvasState,
    onGestureEnd: () -> Unit,
    onSnap: () -> Unit,
) {
    val density = LocalDensity.current
    val displayBmp = if (item.isSticker) (item.cachedBorderedBitmap ?: item.bitmap) else item.bitmap
    val wDp = CanvasState.BASE_WIDTH_DP
    val hDp = wDp * displayBmp.height / max(displayBmp.width, 1)
    val rotationDeg = Math.toDegrees(item.rotationRad.toDouble()).toFloat()

    Box(
        Modifier
            .zIndex(stackIndex.toFloat())
            .offset {
                IntOffset(
                    with(density) { (item.x - wDp / 2).dp.roundToPx() },
                    with(density) { (item.y - hDp / 2).dp.roundToPx() },
                )
            }
            .width(wDp.dp)
            .height(hDp.dp)
            .graphicsLayer {
                scaleX = item.scale
                scaleY = item.scale
                rotationZ = rotationDeg
                transformOrigin = TransformOrigin.Center
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown()
                    // Espejo de iOS: DragGesture(minimumDistance: 2) — el drag no
                    // arranca hasta mover 2pt; un tap limpio selecciona y sube de
                    // capa (selectAndBringToFront); un drag solo selecciona al final.
                    val minDragPx = 2.dp.toPx()
                    var transforming = false
                    var pendingPan = Offset.Zero
                    // Posición CRUDA acumulada del gesto — como el dragDelta de
                    // iOS: el snap se calcula sobre esta, no sobre la snapeada.
                    var rawX = 0f
                    var rawY = 0f
                    var rawInit = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val rot = event.calculateRotation()
                        pendingPan += pan
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (!transforming &&
                            (multiTouch || zoom != 1f || rot != 0f || pendingPan.getDistance() > minDragPx)
                        ) {
                            transforming = true
                        }
                        if (transforming && (pendingPan != Offset.Zero || zoom != 1f || rot != 0f)) {
                            val current = canvas.items.firstOrNull { it.id == item.id } ?: break
                            if (!rawInit) { rawX = current.x; rawY = current.y; rawInit = true }
                            // pan local (px) → dp → espacio del lienzo (rotar + escalar)
                            val panDpX = pendingPan.x / density.density
                            val panDpY = pendingPan.y / density.density
                            val cosR = cos(current.rotationRad)
                            val sinR = sin(current.rotationRad)
                            rawX += (panDpX * cosR - panDpY * sinR) * current.scale
                            rawY += (panDpX * sinR + panDpY * cosR) * current.scale
                            pendingPan = Offset.Zero
                            val guidesBefore = canvas.snapGuides
                            canvas.applyTransform(item.id, rawX, rawY, zoom, Math.toRadians(rot.toDouble()).toFloat())
                            // Háptica al ENTRAR a una guía (rigid impact en iOS)
                            if (canvas.snapGuides != guidesBefore && canvas.snapGuides.isNotEmpty()) onSnap()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    }
                    if (transforming) {
                        canvas.selectedItemId = item.id   // drag end: solo selección
                        onGestureEnd()
                    } else {
                        canvas.selectAndBringToFront(item.id)   // tap: selección + al frente
                    }
                }
            },
    ) {
        val edge = if (item.isSticker) PhotoEdgeShape.none else item.edgeShape
        val shape = EdgeShape(edge)

        Image(
            bitmap = displayBmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape),
            contentScale = ContentScale.FillBounds,
        )
        // Borde de foto: stroke del mismo shape (los stickers usan pre-render)
        if (!item.isSticker && item.borderWidth > 0) {
            Canvas(Modifier.fillMaxSize()) {
                drawPath(edgePath(edge, Size(size.width, size.height)), Color(item.borderArgb), style = Stroke(item.borderWidth.dp.toPx()))
            }
        }
        if (isSelected) {
            // Stroke de selección — Color.accentColor del asset (#7A5C3A), 2.5pt
            Canvas(Modifier.fillMaxSize()) {
                drawPath(edgePath(edge, Size(size.width, size.height)), MemoirSelectionStroke, style = Stroke(2.5.dp.toPx()))
            }
            // Botón eliminar — xmark.circle.fill .title2 (22pt) blanco/rojo,
            // tamaño visual constante (contra-escala) y DENTRO de la esquina (-2, +2)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val inv = 1f / max(item.scale, 0.01f)
                        scaleX = inv; scaleY = inv
                        transformOrigin = TransformOrigin(1f, 0f)
                        translationX = with(density) { (-2).dp.toPx() } * inv
                        translationY = with(density) { 2.dp.toPx() } * inv
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { canvas.deleteItem(item.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Cancel, contentDescription = "Eliminar", tint = MemoirRed, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// ── Paneles ──────────────────────────────────────────────────────────────────

@Composable
private fun EdgePanel(canvas: CanvasState, onHaptic: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoEdgeShape.entries.forEach { shape ->
            val isActive = canvas.selectedItem?.edgeShape == shape
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) Color.White else Color.White.copy(alpha = 0.12f))
                    .clickable {
                        canvas.selectedItemId?.let { canvas.setEdgeShape(shape, it); onHaptic() }
                    }
                    .size(width = 56.dp, height = 56.dp)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(Modifier.size(width = 36.dp, height = 28.dp)) {
                    val p = edgePath(shape, Size(size.width, size.height))
                    drawPath(p, Color.White.copy(alpha = if (isActive) 0.9f else 0.5f))
                }
                Spacer(Modifier.height(4.dp))
                Text(shape.label, fontSize = 9.sp, color = if (isActive) Color.Black else Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun BorderPanel(canvas: CanvasState, scope: kotlinx.coroutines.CoroutineScope, onHaptic: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(0f, 7f, 14f, 25f).forEach { w ->
                val isActive = canvas.selectedItem?.borderWidth == w
                Box(
                    Modifier
                        .size(width = 52.dp, height = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color.White else Color.White.copy(alpha = 0.12f))
                        .clickable {
                            canvas.selectedItemId?.let { id ->
                                val col = canvas.selectedItem?.borderArgb ?: 0xFFFFFFFF
                                scope.launch { canvas.setBorder(w, col, id) }
                                onHaptic()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (w == 0f) "Off" else "${w.toInt()}pt",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = if (isActive) Color.Black else Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            borderPalette.forEach { (_, argb) ->
                val isActive = canvas.selectedItem?.borderArgb == argb &&
                    (canvas.selectedItem?.borderWidth ?: 0f) > 0f
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            if (isActive) 2.5.dp else 1.dp,
                            if (isActive) Color(0xFFC48A3A) else Color.White.copy(alpha = 0.3f),
                            CircleShape,
                        )
                        .clickable {
                            canvas.selectedItemId?.let { id ->
                                val w = max(7f, canvas.selectedItem?.borderWidth ?: 7f)
                                scope.launch { canvas.setBorder(w, argb, id) }
                                onHaptic()
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun LayoutPanel(canvas: CanvasState, onApplied: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            CanvasLayout.one to "1 foto", CanvasLayout.twoColumns to "2 vert.",
            CanvasLayout.twoRows to "2 horiz.", CanvasLayout.grid4 to "4 fotos",
        ).forEach { (layout, label) ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable { canvas.applyLayout(layout); onApplied() }
                    .size(60.dp)
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(Modifier.size(width = 34.dp, height = 30.dp)) {
                    val stroke = Stroke(1.5.dp.toPx())
                    drawRoundRect(
                        Color.White.copy(alpha = 0.85f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                        style = stroke,
                    )
                    when (layout) {
                        CanvasLayout.one -> {}
                        CanvasLayout.twoColumns ->
                            drawLine(Color.White.copy(alpha = 0.85f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5.dp.toPx())
                        CanvasLayout.twoRows ->
                            drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
                        CanvasLayout.grid4 -> {
                            drawLine(Color.White.copy(alpha = 0.85f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.85f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

// ── Piezas pequeñas ──────────────────────────────────────────────────────────

/** Espejo de IGToolBtn (iOS): zona de icono 46×46, icono 20, label 10 medium;
 *  activo → círculo blanco 0.18 y tinte Color.sand (#6E3B2D). */
@Composable
private fun ToolButton(
    icon: ImageVector, label: String,
    tint: Color = Color.White, active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) Color.White.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) MemoirSand else tint, modifier = Modifier.size(20.dp))
        }
        Text(
            label, fontSize = 10.sp, fontWeight = FontWeight.Medium,
            color = if (active) MemoirSand else tint.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun ToolDivider() {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .width(28.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.15f)),
    )
}

@Composable
private fun PillNav(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 60.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.25f))
    }
}

@Composable
private fun PillAction(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        Modifier
            .size(width = 68.dp, height = 60.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PillDivider() {
    Box(Modifier.width(0.5.dp).height(28.dp).background(Color.White.copy(alpha = 0.15f)))
}

@Composable
private fun ProcessingOverlay(
    label: String,
    scrim: Float = 0.45f,
    boxPadding: androidx.compose.ui.unit.Dp = 28.dp,
    corner: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrim)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(corner))
                .background(Color(0xFF222222).copy(alpha = 0.9f))
                .padding(boxPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
