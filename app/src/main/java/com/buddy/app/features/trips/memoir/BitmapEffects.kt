package com.buddy.app.features.trips.memoir

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Efectos de imagen del Memoir — espejo de SubjectLiftService,
 * UIImage+Trim, UIImage+StickerBorder y UIImage+Resize (iOS).
 */
object BitmapEffects {

    /** Espejo de limitedToMaxDimension: reescala manteniendo aspecto. */
    fun limitedToMaxDimension(src: Bitmap, maxPx: Int = 1200): Bitmap {
        val maxDim = maxOf(src.width, src.height)
        if (maxDim <= maxPx) return src
        val ratio = maxPx.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).roundToInt().coerceAtLeast(1),
            (src.height * ratio).roundToInt().coerceAtLeast(1), true,
        )
    }

    /**
     * Espejo de SubjectLiftService.liftSubject — recorta el sujeto de la foto.
     * Usa ML Kit Subject Segmentation (equivalente Android de VisionKit).
     */
    suspend fun liftSubject(src: Bitmap): Bitmap {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build(),
        )
        try {
            val result = segmenter.process(InputImage.fromBitmap(src, 0)).await()
            return result.foregroundBitmap
                ?: throw IllegalStateException("No se detectó un sujeto en esta foto.")
        } finally {
            segmenter.close()
        }
    }

    /** Espejo de trimmingTransparentPixels: recorta márgenes transparentes. */
    suspend fun trimTransparent(src: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        // threshold alpha > 10 — mismo valor que trimmingTransparentPixels (iOS)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (pixels[row + x] ushr 24 > 10) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return@withContext src   // todo transparente
        Bitmap.createBitmap(src, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /**
     * Espejo de withStickerBorder: borde sólido pegado al alpha del sujeto,
     * construido componiendo la silueta en 20 desplazamientos angulares.
     * `widthDp` está en espacio de display (el item siempre mide 180dp de ancho).
     */
    suspend fun stickerBorder(src: Bitmap, widthDp: Float, argb: Long): Bitmap =
        withContext(Dispatchers.Default) {
            if (widthDp <= 0f) return@withContext src
            val pixelRadius = widthDp * (src.width / 180f)
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            // Silueta: máscara alpha pintada del color del borde
            val alpha = src.extractAlpha()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb.toInt() }
            val steps = 20
            for (i in 0 until steps) {
                val angle = i * (2 * Math.PI / steps)
                val dx = (cos(angle) * pixelRadius).toFloat()
                val dy = (sin(angle) * pixelRadius).toFloat()
                canvas.drawBitmap(alpha, dx, dy, paint)
            }
            alpha.recycle()
            canvas.drawBitmap(src, 0f, 0f, null)
            out
        }

    /** Recorte centrado al aspecto dado (ancho/alto) — espejo de cropPhoto (iOS). */
    fun cropToAspect(src: Bitmap, aspect: Float): Bitmap {
        val w = src.width.toFloat(); val h = src.height.toFloat()
        var cropW = w; var cropH = w / aspect
        if (cropH > h) { cropH = h; cropW = h * aspect }
        val x = ((w - cropW) / 2).roundToInt().coerceAtLeast(0)
        val y = ((h - cropH) / 2).roundToInt().coerceAtLeast(0)
        return Bitmap.createBitmap(
            src, x, y,
            cropW.roundToInt().coerceIn(1, src.width - x),
            cropH.roundToInt().coerceIn(1, src.height - y),
        )
    }
}
