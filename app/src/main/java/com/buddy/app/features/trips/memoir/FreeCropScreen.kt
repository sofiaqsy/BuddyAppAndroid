package com.buddy.app.features.trips.memoir

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class CropHandle { TL, TR, BL, BR, MOVE }

/**
 * Recorte libre — port de FreeCropView (iOS): velo oscuro, tercios,
 * agarres de esquina (radio 36dp), mover el área, lado mínimo 60,
 * "Restaurar original" y apply → recorte en píxeles.
 */
@Composable
fun FreeCropScreen(
    image: Bitmap,
    original: Bitmap,
    onCancel: () -> Unit,
    onApply: (Bitmap) -> Unit,
) {
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var fittedRect by remember { mutableStateOf(Rect.Zero) }
    var activeHandle by remember { mutableStateOf<CropHandle?>(null) }
    val minSide = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }
    val handleRadius = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }

    fun clampf(v: Float, a: Float, b: Float): Float {
        val lo = minOf(a, b); val hi = maxOf(a, b)
        return v.coerceIn(lo, hi)
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            // Padding top 64 como iOS; "Aplicar" en Color.sand (= brand #6E3B2D)
            Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cancelar", color = Color.White, fontSize = 15.sp, modifier = Modifier.clickable(onClick = onCancel))
            Spacer(Modifier.weight(1f))
            Text("Recortar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "Aplicar", color = Color(0xFF6E3B2D), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    if (cropRect.width > 0 && fittedRect.width > 0) {
                        val pxPerPt = image.width / fittedRect.width
                        val x = ((cropRect.left - fittedRect.left) * pxPerPt).roundToInt().coerceIn(0, image.width - 1)
                        val y = ((cropRect.top - fittedRect.top) * pxPerPt).roundToInt().coerceIn(0, image.height - 1)
                        val w = (cropRect.width * pxPerPt).roundToInt().coerceIn(1, image.width - x)
                        val h = (cropRect.height * pxPerPt).roundToInt().coerceIn(1, image.height - y)
                        onApply(Bitmap.createBitmap(image, x, y, w, h))
                    } else onCancel()
                },
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { size ->
                    val scale = minOf(
                        size.width / maxOf(image.width.toFloat(), 1f),
                        size.height / maxOf(image.height.toFloat(), 1f),
                    )
                    val w = image.width * scale; val h = image.height * scale
                    fittedRect = Rect(
                        Offset((size.width - w) / 2, (size.height - h) / 2),
                        Size(w, h),
                    )
                    if (cropRect == Rect.Zero) cropRect = fittedRect
                }
                .pointerInput(fittedRect) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val r = cropRect
                            fun near(c: Offset) = hypot(start.x - c.x, start.y - c.y) < handleRadius
                            activeHandle = when {
                                near(Offset(r.left, r.top)) -> CropHandle.TL
                                near(Offset(r.right, r.top)) -> CropHandle.TR
                                near(Offset(r.left, r.bottom)) -> CropHandle.BL
                                near(Offset(r.right, r.bottom)) -> CropHandle.BR
                                r.contains(start) -> CropHandle.MOVE
                                else -> null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val h = activeHandle ?: return@detectDragGestures
                            val fitted = fittedRect
                            val r = cropRect
                            val dx = dragAmount.x; val dy = dragAmount.y
                            cropRect = when (h) {
                                CropHandle.MOVE -> Rect(
                                    Offset(
                                        clampf(r.left + dx, fitted.left, fitted.right - r.width),
                                        clampf(r.top + dy, fitted.top, fitted.bottom - r.height),
                                    ),
                                    Size(r.width, r.height),
                                )
                                CropHandle.TL -> Rect(
                                    clampf(r.left + dx, fitted.left, r.right - minSide),
                                    clampf(r.top + dy, fitted.top, r.bottom - minSide),
                                    r.right, r.bottom,
                                )
                                CropHandle.TR -> Rect(
                                    r.left,
                                    clampf(r.top + dy, fitted.top, r.bottom - minSide),
                                    clampf(r.right + dx, r.left + minSide, fitted.right),
                                    r.bottom,
                                )
                                CropHandle.BL -> Rect(
                                    clampf(r.left + dx, fitted.left, r.right - minSide),
                                    r.top, r.right,
                                    clampf(r.bottom + dy, r.top + minSide, fitted.bottom),
                                )
                                CropHandle.BR -> Rect(
                                    r.left, r.top,
                                    clampf(r.right + dx, r.left + minSide, fitted.right),
                                    clampf(r.bottom + dy, r.top + minSide, fitted.bottom),
                                )
                            }
                        },
                        onDragEnd = { activeHandle = null },
                        onDragCancel = { activeHandle = null },
                    )
                },
        ) {
            if (fittedRect.width > 0) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(),
                    alignment = Alignment.Center,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                if (cropRect == Rect.Zero) return@Canvas
                // Velo oscuro fuera del área (even-odd)
                val veil = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRect(cropRect)
                }
                drawPath(veil, Color.Black.copy(alpha = 0.6f))

                // Marco
                drawRect(
                    Color.White, topLeft = cropRect.topLeft, size = cropRect.size,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                // Tercios
                for (i in 1..2) {
                    val fx = cropRect.left + cropRect.width * i / 3
                    val fy = cropRect.top + cropRect.height * i / 3
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(fx, cropRect.top), Offset(fx, cropRect.bottom), 0.5.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(cropRect.left, fy), Offset(cropRect.right, fy), 0.5.dp.toPx())
                }
                // Agarres de esquina
                listOf(
                    Offset(cropRect.left, cropRect.top), Offset(cropRect.right, cropRect.top),
                    Offset(cropRect.left, cropRect.bottom), Offset(cropRect.right, cropRect.bottom),
                ).forEach { drawCircle(Color.White, radius = 7.dp.toPx(), center = it) }
            }
        }

        Text(
            "Restaurar original",
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 18.dp)
                .clickable { onApply(original) },
        )
    }
}
