package com.buddy.app.features.trips.memoir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max

/**
 * Persistencia del Trip Book — espejo de MemoirPersistence.swift.
 *
 * Layout por journey (filesDir):
 *   memoirs/{journeyId}/book.json
 *   memoirs/{journeyId}/images/
 *   memoirs/{journeyId}/thumbs/
 *   memoirs/{journeyId}/backgrounds/
 * Global: memoir_backgrounds/ (strips compartidos entre trips)
 */
class MemoirPersistence(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun root(journeyId: String) = File(context.filesDir, "memoirs/$journeyId")
    private fun imagesDir(journeyId: String) = File(root(journeyId), "images").apply { mkdirs() }
    private fun thumbsDir(journeyId: String) = File(root(journeyId), "thumbs").apply { mkdirs() }
    private fun bgDir(journeyId: String) = File(root(journeyId), "backgrounds").apply { mkdirs() }
    private fun bookFile(journeyId: String) = File(root(journeyId).apply { mkdirs() }, "book.json")
    private val globalBgDir get() = File(context.filesDir, "memoir_backgrounds").apply { mkdirs() }

    // ── Save / Load páginas ──────────────────────────────────────────────────

    fun save(pages: List<CollagePage>, journeyId: String) {
        runCatching { bookFile(journeyId).writeText(json.encodeToString(pages)) }
            .onFailure { Log.e(TAG, "save failed", it) }
    }

    fun load(journeyId: String): List<CollagePage> {
        val f = bookFile(journeyId)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<CollagePage>>(f.readText()) }
            .getOrDefault(emptyList())
    }

    // ── CanvasState ↔ CollagePage ────────────────────────────────────────────

    /** Espejo de snapshot(from:existing:): escribe imágenes y arma la página. */
    fun snapshot(items: List<CollageItem>, backgroundRGBA: List<Double>, existing: CollagePage, journeyId: String): CollagePage {
        return existing.copy(
            backgroundRGBA = backgroundRGBA,
            itemSnapshots = items.map { item ->
                val imgFile = writeImage(item.bitmap, "${item.id}_img", journeyId, force = true)
                val origFile = writeImage(item.originalBitmap, "${item.id}_orig", journeyId, force = false)
                val borderedFile = item.cachedBorderedBitmap?.let {
                    writeImage(it, "${item.id}_bordered", journeyId, force = true)
                }
                CollageItemSnapshot(
                    id = item.id,
                    imageFile = imgFile,
                    originalImageFile = origFile,
                    cachedBorderedFile = borderedFile,
                    isSticker = item.isSticker,
                    x = item.x.toDouble(), y = item.y.toDouble(),
                    scale = item.scale.toDouble(),
                    rotationRadians = item.rotationRad.toDouble(),
                    zIndex = item.zIndex,
                    edgeShape = item.edgeShape,
                    borderWidth = item.borderWidth.toDouble(),
                    borderRGBA = argbToRgba(item.borderArgb),
                )
            },
        )
    }

    fun buildItems(page: CollagePage, journeyId: String): List<CollageItem> =
        page.itemSnapshots.mapNotNull { snap ->
            val img = readImage(snap.imageFile, journeyId) ?: return@mapNotNull null
            val orig = readImage(snap.originalImageFile, journeyId) ?: img
            CollageItem(
                id = snap.id,
                bitmap = img,
                originalBitmap = orig,
                isSticker = snap.isSticker,
                x = snap.x.toFloat(), y = snap.y.toFloat(),
                scale = snap.scale.toFloat(),
                rotationRad = snap.rotationRadians.toFloat(),
                zIndex = snap.zIndex,
                edgeShape = snap.edgeShape,
                borderWidth = snap.borderWidth.toFloat(),
                borderArgb = rgbaToArgb(snap.borderRGBA),
                cachedBorderedBitmap = snap.cachedBorderedFile?.let { readImage(it, journeyId) },
            )
        }

    // ── Thumbnails ───────────────────────────────────────────────────────────

    /**
     * Espejo de generateThumbnail: renderiza la página a JPEG 2x.
     * `canvasW/H` en dp; las coordenadas de los items están en ese espacio.
     */
    fun generateThumbnail(
        items: List<CollageItem>,
        backgroundBitmap: Bitmap?,
        backgroundRGBA: List<Double>,
        canvasW: Float, canvasH: Float,
        pageId: String, journeyId: String,
    ): String? {
        if (canvasW <= 0f || canvasH <= 0f) return null
        val scale = 2f
        val bmp = Bitmap.createBitmap((canvasW * scale).toInt(), (canvasH * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)

        // Fondo: strip + velo blanco 55% o color sólido (como iOS)
        if (backgroundBitmap != null) {
            drawCover(canvas, backgroundBitmap, canvasW, canvasH)
            canvas.drawColor(Color.argb((0.55f * 255).toInt(), 255, 255, 255))
        } else {
            canvas.drawColor(rgbaToArgb(backgroundRGBA).toInt())
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (item in items.sortedBy { it.zIndex }) {
            val img = item.cachedBorderedBitmap ?: item.bitmap
            val w = 180f * item.scale
            val h = w * img.height / max(img.width, 1)
            canvas.save()
            canvas.rotate(Math.toDegrees(item.rotationRad.toDouble()).toFloat(), item.x, item.y)
            canvas.translate(item.x - w / 2, item.y - h / 2)
            val path = edgePath(
                if (item.isSticker) PhotoEdgeShape.none else item.edgeShape,
                Size(w, h),
            ).asAndroidPath()
            canvas.save()
            if (!item.isSticker && item.edgeShape != PhotoEdgeShape.none) canvas.clipPath(path)
            val m = Matrix().apply { setScale(w / img.width, h / img.height) }
            canvas.drawBitmap(img, m, paint)
            canvas.restore()
            if (item.borderWidth > 0 && !item.isSticker) {
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = item.borderWidth * item.scale
                    color = item.borderArgb.toInt()
                }
                canvas.drawPath(path, stroke)
            }
            canvas.restore()
        }

        val filename = "${pageId}_thumb.jpg"
        return runCatching {
            File(thumbsDir(journeyId), filename).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, it)
            }
            filename
        }.onFailure { Log.e(TAG, "thumbnail failed", it) }.getOrNull()
    }

    private fun drawCover(canvas: Canvas, bmp: Bitmap, w: Float, h: Float) {
        val scale = max(w / bmp.width, h / bmp.height)
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - dw) / 2, (h - dh) / 2)
        }
        canvas.drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun loadThumbnail(filename: String, journeyId: String): Bitmap? =
        decode(File(thumbsDir(journeyId), filename))

    fun thumbnailFile(filename: String, journeyId: String): File =
        File(thumbsDir(journeyId), filename)

    // ── Background strips ────────────────────────────────────────────────────

    fun loadBackground(filename: String, journeyId: String): Bitmap? =
        decode(File(bgDir(journeyId), filename)) ?: decode(File(globalBgDir, filename))

    fun backgroundStripExists(filename: String): Boolean = File(globalBgDir, filename).exists()

    // ── Image helpers ────────────────────────────────────────────────────────

    private fun writeImage(bmp: Bitmap, name: String, journeyId: String, force: Boolean): String {
        val ext = if (bmp.hasAlpha()) "png" else "jpg"
        val filename = "$name.$ext"
        val f = File(imagesDir(journeyId), filename)
        if (force || !f.exists()) {
            runCatching {
                f.outputStream().use {
                    if (bmp.hasAlpha()) bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    else bmp.compress(Bitmap.CompressFormat.JPEG, 82, it)
                }
            }.onFailure { Log.e(TAG, "writeImage failed", it) }
        }
        return filename
    }

    private fun readImage(filename: String, journeyId: String): Bitmap? =
        decode(File(imagesDir(journeyId), filename))

    private fun decode(f: File): Bitmap? =
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null

    // ── Color helpers ────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "MemoirPersistence"

        fun rgbaToArgb(rgba: List<Double>): Long {
            val r = ((rgba.getOrElse(0) { 1.0 }) * 255).toInt().coerceIn(0, 255)
            val g = ((rgba.getOrElse(1) { 1.0 }) * 255).toInt().coerceIn(0, 255)
            val b = ((rgba.getOrElse(2) { 1.0 }) * 255).toInt().coerceIn(0, 255)
            val a = ((rgba.getOrElse(3) { 1.0 }) * 255).toInt().coerceIn(0, 255)
            return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
        }

        fun argbToRgba(argb: Long): List<Double> = listOf(
            ((argb shr 16) and 0xFF) / 255.0,
            ((argb shr 8) and 0xFF) / 255.0,
            (argb and 0xFF) / 255.0,
            ((argb shr 24) and 0xFF) / 255.0,
        )
    }
}
