package com.buddy.app.features.trips.memoir

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Dimensiones de la página — espejo de CanvasViewModel.pageSize (iOS:
 * UIScreen.width × 480, retrato en iPhone). En pantallas anchas (tablets) el
 * ancho se limita a 390dp (ancho lógico de iPhone) para conservar el formato
 * retrato: el lienzo nunca se vuelve apaisado.
 */
object MemoirPage {
    const val HEIGHT_DP = 480f
    const val MAX_WIDTH_DP = 390f

    fun width(screenWidthDp: Float): Float = minOf(screenWidthDp, MAX_WIDTH_DP)
    fun aspect(screenWidthDp: Float): Float = width(screenWidthDp) / HEIGHT_DP
}

/** Guías de snap activas durante un drag — espejo de SnapGuide (iOS). */
enum class SnapGuide { verticalCenter, horizontalCenter }

/** Plantillas rápidas — espejo de CanvasLayout (iOS). */
enum class CanvasLayout { one, twoColumns, twoRows, grid4 }

/**
 * Estado del lienzo de una página — port de CanvasViewModel.swift.
 * Coordenadas en dp; el lienzo publicado mide screenWidthDp × 480dp
 * (editor y publicación 1:1, como iOS).
 */
class CanvasState {
    val items = mutableStateListOf<CollageItem>()
    var selectedItemId by mutableStateOf<String?>(null)
    var isProcessing by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var backgroundRGBA by mutableStateOf(listOf(1.0, 1.0, 1.0, 1.0))
    var backgroundBitmap by mutableStateOf<Bitmap?>(null)
    var snapGuides by mutableStateOf<Set<SnapGuide>>(emptySet())

    /** dp — lo fija el editor con el tamaño real de la página. */
    var canvasW = 0f
    var canvasH = 0f

    val selectedItem: CollageItem? get() = items.firstOrNull { it.id == selectedItemId }
    val sortedItems: List<CollageItem> get() = items.sortedBy { it.zIndex }

    companion object {
        const val MIN_SCALE = 0.35f
        const val MAX_SCALE = 6f
        const val MAX_ITEMS = 10
        const val MAX_IMAGE_PX = 1200
        const val BASE_WIDTH_DP = 180f
        private const val SNAP_THRESHOLD = 10f
    }

    // ── Agregar ──────────────────────────────────────────────────────────────

    fun addPhoto(image: Bitmap) {
        if (items.size >= MAX_ITEMS) {
            errorMessage = "Máximo $MAX_ITEMS elementos por página. Elimina alguno o crea una página nueva."
            return
        }
        val resized = BitmapEffects.limitedToMaxDimension(image, MAX_IMAGE_PX)
        val pos = randomCenter()
        val item = CollageItem(
            bitmap = resized, originalBitmap = resized,
            x = pos.first, y = pos.second, zIndex = nextZ(),
        )
        items.add(item)
        selectedItemId = item.id

        // Primera foto de la página → llena todo el espacio (Diseño 1 foto)
        if (items.count { !it.isSticker } == 1 && canvasW > 0f) {
            applyLayout(CanvasLayout.one)
            selectedItemId = item.id
        }
    }

    // ── Sticker (subject lift) ───────────────────────────────────────────────

    suspend fun makeSticker(id: String) {
        val i = index(id) ?: return
        if (items[i].isSticker) return
        isProcessing = true
        try {
            val cutout = BitmapEffects.liftSubject(items[i].originalBitmap)
            val trimmed = BitmapEffects.trimTransparent(cutout)
            val j = index(id) ?: return
            items[j] = items[j].copy(bitmap = trimmed, isSticker = true)
        } catch (e: Exception) {
            errorMessage = e.message ?: "No se pudo recortar el sujeto."
        } finally {
            isProcessing = false
        }
    }

    fun revertToPhoto(id: String) {
        val i = index(id) ?: return
        if (!items[i].isSticker) return
        items[i] = items[i].copy(
            bitmap = items[i].originalBitmap, isSticker = false,
            cachedBorderedBitmap = null, borderWidth = 0f,
        )
    }

    // ── Layouts rápidos ──────────────────────────────────────────────────────

    /** Port de applyLayout: asignación por cercanía + recorte al aspecto de la celda. */
    fun applyLayout(layout: CanvasLayout) {
        if (canvasW <= 0f || canvasH <= 0f) return
        val photoIds = items.sortedBy { it.zIndex }.filter { !it.isSticker }.map { it.id }
        if (photoIds.isEmpty()) return

        val w = canvasW; val h = canvasH
        data class Slot(val x: Float, val y: Float, val w: Float, val h: Float)
        val slots = when (layout) {
            CanvasLayout.one -> listOf(Slot(0f, 0f, w, h))
            CanvasLayout.twoColumns -> listOf(Slot(0f, 0f, w / 2, h), Slot(w / 2, 0f, w / 2, h))
            CanvasLayout.twoRows -> listOf(Slot(0f, 0f, w, h / 2), Slot(0f, h / 2, w, h / 2))
            CanvasLayout.grid4 -> listOf(
                Slot(0f, 0f, w / 2, h / 2), Slot(w / 2, 0f, w / 2, h / 2),
                Slot(0f, h / 2, w / 2, h / 2), Slot(w / 2, h / 2, w / 2, h / 2),
            )
        }

        // Cada celda recibe la foto que quedó más cerca — respeta la intención
        val slotsLeft = slots.mapIndexed { idx, s -> idx to s }.toMutableList()
        val idsLeft = photoIds.toMutableList()
        val assignment = mutableListOf<Triple<Int, Slot, String>>()
        while (slotsLeft.isNotEmpty() && idsLeft.isNotEmpty()) {
            var best: Triple<Int, Int, Float>? = null
            for ((sPos, pair) in slotsLeft.withIndex()) {
                val cx = pair.second.x + pair.second.w / 2
                val cy = pair.second.y + pair.second.h / 2
                for ((iPos, pid) in idsLeft.withIndex()) {
                    val item = items[index(pid) ?: continue]
                    val d = hypot(item.x - cx, item.y - cy)
                    if (best == null || d < best!!.third) best = Triple(sPos, iPos, d)
                }
            }
            val b = best ?: break
            val (slotIdx, slot) = slotsLeft.removeAt(b.first)
            val pid = idsLeft.removeAt(b.second)
            assignment.add(Triple(slotIdx, slot, pid))
        }

        for ((slotIndex, slot, pid) in assignment) {
            cropPhotoToAspect(pid, slot.w / slot.h)
            val j = index(pid) ?: continue
            val item = items[j]
            // Giro completado al ángulo recto más cercano (0/90/180/270)
            val deg = Math.toDegrees(item.rotationRad.toDouble())
            val snappedRad = Math.toRadians((deg / 90).roundToInt() * 90.0).toFloat()
            // Escala de cobertura sobre la imagen recortada real
            val imgAspect = item.bitmap.height.toFloat() / maxOf(item.bitmap.width, 1)
            val wScale = slot.w / BASE_WIDTH_DP
            val hScale = slot.h / (BASE_WIDTH_DP * imgAspect)
            items[j] = items[j].copy(
                x = slot.x + slot.w / 2, y = slot.y + slot.h / 2,
                rotationRad = snappedRad,
                scale = maxOf(wScale, hScale) * 1.001f,
                zIndex = slotIndex.toDouble(),
                edgeShape = PhotoEdgeShape.none,
            )
        }
        selectedItemId = null
    }

    // ── Crop ─────────────────────────────────────────────────────────────────

    /** Recorta al aspecto (parte de la imagen ACTUAL, respeta recortes a mano). */
    fun cropPhotoToAspect(id: String, aspect: Float) {
        val i = index(id) ?: return
        if (items[i].isSticker) return
        items[i] = items[i].copy(bitmap = BitmapEffects.cropToAspect(items[i].bitmap, aspect))
    }

    /** Reemplaza por la versión recortada a mano (FreeCrop). */
    fun setCroppedImage(id: String, image: Bitmap) {
        val i = index(id) ?: return
        if (items[i].isSticker) return
        items[i] = items[i].copy(bitmap = image)
    }

    // ── Edge / borde ─────────────────────────────────────────────────────────

    fun setEdgeShape(shape: PhotoEdgeShape, id: String) {
        val i = index(id) ?: return
        items[i] = items[i].copy(edgeShape = shape)
    }

    suspend fun setBorder(width: Float, argb: Long, id: String) {
        val i = index(id) ?: return
        items[i] = items[i].copy(borderWidth = width, borderArgb = argb)
        // Pre-render solo para stickers — las fotos usan stroke del clip shape
        if (!items[i].isSticker) {
            items[i] = items[i].copy(cachedBorderedBitmap = null)
            return
        }
        if (width > 0) {
            val bordered = BitmapEffects.stickerBorder(items[i].bitmap, width, argb)
            val j = index(id) ?: return
            items[j] = items[j].copy(cachedBorderedBitmap = bordered)
        } else {
            items[i] = items[i].copy(cachedBorderedBitmap = null)
        }
    }

    // ── Snap ─────────────────────────────────────────────────────────────────

    /** Devuelve la posición snapeada y las guías activas — espejo de snapPosition. */
    fun snapPosition(rawX: Float, rawY: Float): Triple<Float, Float, Set<SnapGuide>> {
        if (canvasW <= 0f) return Triple(rawX, rawY, emptySet())
        var x = rawX; var y = rawY
        val guides = mutableSetOf<SnapGuide>()
        val cx = canvasW / 2; val cy = canvasH / 2
        if (kotlin.math.abs(x - cx) < SNAP_THRESHOLD) { x = cx; guides.add(SnapGuide.verticalCenter) }
        if (kotlin.math.abs(y - cy) < SNAP_THRESHOLD) { y = cy; guides.add(SnapGuide.horizontalCenter) }
        return Triple(x, y, guides)
    }

    // ── Transforms ───────────────────────────────────────────────────────────

    fun setPosition(id: String, x: Float, y: Float) {
        val i = index(id) ?: return
        items[i] = items[i].copy(x = x, y = y)
    }

    /**
     * Aplica el gesto usando la posición CRUDA acumulada del drag (rawX/rawY),
     * como iOS: el snap solo afecta lo que se muestra, nunca el acumulado —
     * si no, una vez imantado al centro los deltas por frame (< umbral) jamás
     * escapan y el item "se queda pegado".
     */
    fun applyTransform(id: String, rawX: Float, rawY: Float, zoom: Float, rotDeltaRad: Float) {
        val i = index(id) ?: return
        val item = items[i]
        val (sx, sy, guides) = snapPosition(rawX, rawY)
        snapGuides = guides
        items[i] = item.copy(
            x = sx, y = sy,
            scale = (item.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE),
            rotationRad = item.rotationRad + rotDeltaRad,
        )
    }

    /** Selección + subir de capa en una sola mutación — espejo de selectAndBringToFront. */
    fun selectAndBringToFront(id: String) {
        val i = index(id) ?: return
        if (!isBackgroundItem(items[i])) {
            items[i] = items[i].copy(zIndex = nextZ())
        }
        selectedItemId = id
    }

    /** Foto que cubre ≥40% del lienzo = fondo; no sube de capa al tocarla. */
    private fun isBackgroundItem(item: CollageItem): Boolean {
        if (canvasW <= 0f || item.isSticker) return false
        val imgAspect = item.bitmap.height.toFloat() / maxOf(item.bitmap.width, 1)
        val w = BASE_WIDTH_DP * item.scale
        val h = BASE_WIDTH_DP * imgAspect * item.scale
        return w * h >= canvasW * canvasH * 0.4f
    }

    fun deleteItem(id: String) {
        items.removeAll { it.id == id }
        if (selectedItemId == id) selectedItemId = null
    }

    fun duplicateItem(id: String) {
        if (items.size >= MAX_ITEMS) {
            errorMessage = "Máximo $MAX_ITEMS elementos por página."
            return
        }
        val i = index(id) ?: return
        val o = items[i]
        val copy = o.copy(
            id = java.util.UUID.randomUUID().toString(),
            x = o.x + 20f, y = o.y + 20f, zIndex = nextZ(),
        )
        items.add(copy)
        selectedItemId = copy.id
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun index(id: String): Int? =
        items.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    private fun nextZ(): Double = (items.maxOfOrNull { it.zIndex } ?: 0.0) + 1

    private fun randomCenter(): Pair<Float, Float> {
        val hPad = 100f; val topPad = 160f; val botPad = 220f
        val w = if (canvasW > 0f) canvasW else 360f
        val h = if (canvasH > 0f) canvasH else 480f
        val x = hPad + Random.nextFloat() * maxOf(w - 2 * hPad, 1f)
        val y = topPad + Random.nextFloat() * maxOf(h - topPad - botPad, 1f)
        return x to y
    }
}
