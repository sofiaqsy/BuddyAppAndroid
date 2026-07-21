package com.buddy.app.features.trips.memoir

import android.graphics.Bitmap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** Espejo de PhotoEdgeShape (iOS) — nombres idénticos para el JSON. */
enum class PhotoEdgeShape {
    none, tornBottom, tornTop, tornRight, tornLeft, diagonalTR, diagonalBL;

    val label: String
        get() = when (this) {
            none -> "Normal"
            tornBottom -> "Abajo"
            tornTop -> "Arriba"
            tornRight -> "Derecha"
            tornLeft -> "Izquierda"
            diagonalTR -> "Diag ↗"
            diagonalBL -> "Diag ↙"
        }
}

/**
 * Espejo de CollageItem (iOS). Inmutable: las mutaciones reemplazan la entrada
 * en la lista para que Compose recomponga solo lo que cambió.
 * Coordenadas en dp dentro del lienzo (como los pt de iOS). El ancho base de
 * todo item es 180dp — escala/rotación se aplican sobre esa base.
 */
data class CollageItem(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    /** Siempre la foto completa — permite revertir sticker/recortes. */
    val originalBitmap: Bitmap,
    val isSticker: Boolean = false,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotationRad: Float = 0f,
    val zIndex: Double = 0.0,
    val edgeShape: PhotoEdgeShape = PhotoEdgeShape.none,
    /** dp — mismo espacio visual que los pt de iOS. */
    val borderWidth: Float = 0f,
    val borderArgb: Long = 0xFFFFFFFF,
    /** Pre-render del borde de sticker (dilatación), como en iOS. */
    val cachedBorderedBitmap: Bitmap? = null,
)

/** Snapshot serializable de una página — espejo de CollagePage (iOS). */
@Serializable
data class CollagePage(
    val id: String = UUID.randomUUID().toString(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val backgroundRGBA: List<Double> = listOf(1.0, 1.0, 1.0, 1.0),
    val itemSnapshots: List<CollageItemSnapshot> = emptyList(),
    val thumbnailFileName: String? = null,
    /** Se incrementa en cada guardado — invalida cachés de thumbnails. */
    val editVersion: Int = 0,
    val backgroundImageFile: String? = null,
)

@Serializable
data class CollageItemSnapshot(
    val id: String,
    val imageFile: String,
    val originalImageFile: String,
    val cachedBorderedFile: String? = null,
    val isSticker: Boolean,
    val x: Double, val y: Double,
    val scale: Double,
    val rotationRadians: Double,
    @SerialName("zIndex") val zIndex: Double,
    val edgeShape: PhotoEdgeShape = PhotoEdgeShape.none,
    val borderWidth: Double = 0.0,
    val borderRGBA: List<Double> = listOf(1.0, 1.0, 1.0, 1.0),
)
