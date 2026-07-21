package com.buddy.app.features.trips.memoir

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Bordes rasgados/diagonales — port 1:1 de PhotoEdgeShape.swift.
 * Los arrays de puntos (normalizados 0–1) son copiados tal cual de iOS,
 * incluyendo el jitter horizontal de las curvas cuadráticas.
 */
fun edgePath(shape: PhotoEdgeShape, size: Size): Path {
    val w = size.width
    val h = size.height
    val p = Path()
    when (shape) {
        PhotoEdgeShape.none -> p.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))

        PhotoEdgeShape.tornBottom -> {
            val pts = listOf(
                0.00f to 0.79f, 0.05f to 0.76f, 0.10f to 0.82f, 0.15f to 0.74f,
                0.21f to 0.80f, 0.27f to 0.72f, 0.32f to 0.78f, 0.38f to 0.71f,
                0.43f to 0.77f, 0.48f to 0.83f, 0.53f to 0.74f, 0.58f to 0.80f,
                0.63f to 0.70f, 0.68f to 0.76f, 0.73f to 0.82f, 0.78f to 0.73f,
                0.83f to 0.79f, 0.88f to 0.69f, 0.93f to 0.76f, 1.00f to 0.80f,
            )
            p.moveTo(0f, 0f)
            p.lineTo(w, 0f)
            p.lineTo(w, pts.last().second * h)
            for (i in pts.size - 2 downTo 0) {
                val curr = pts[i]; val next = pts[i + 1]
                val cpx = (curr.first + next.first) / 2 * w + (if (i % 2 == 0) 7f else -7f)
                val cpy = (curr.second + next.second) / 2 * h
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(0f, 0f)
            p.close()
        }

        PhotoEdgeShape.tornTop -> {
            val pts = listOf(
                0.00f to 0.21f, 0.05f to 0.25f, 0.11f to 0.18f, 0.17f to 0.28f,
                0.23f to 0.19f, 0.29f to 0.27f, 0.35f to 0.17f, 0.41f to 0.24f,
                0.47f to 0.16f, 0.53f to 0.23f, 0.58f to 0.18f, 0.64f to 0.26f,
                0.70f to 0.17f, 0.76f to 0.24f, 0.82f to 0.19f, 0.88f to 0.27f,
                0.93f to 0.20f, 1.00f to 0.22f,
            )
            p.moveTo(0f, pts[0].second * h)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]; val curr = pts[i]
                val cpx = (prev.first + curr.first) / 2 * w + (if (i % 2 == 0) 7f else -7f)
                val cpy = (prev.second + curr.second) / 2 * h
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(w, h)
            p.lineTo(0f, h)
            p.close()
        }

        PhotoEdgeShape.tornRight -> {
            val pts = listOf(
                0.79f to 0.00f, 0.83f to 0.06f, 0.76f to 0.12f, 0.82f to 0.18f,
                0.74f to 0.24f, 0.80f to 0.31f, 0.73f to 0.38f, 0.79f to 0.45f,
                0.71f to 0.52f, 0.77f to 0.59f, 0.82f to 0.65f, 0.75f to 0.72f,
                0.81f to 0.78f, 0.74f to 0.85f, 0.79f to 0.91f, 0.76f to 1.00f,
            )
            p.moveTo(0f, 0f)
            p.lineTo(pts[0].first * w, 0f)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]; val curr = pts[i]
                val cpy = (prev.second + curr.second) / 2 * h
                val cpx = maxOf(prev.first, curr.first) * w + 4f
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(0f, h)
            p.close()
        }

        PhotoEdgeShape.tornLeft -> {
            val pts = listOf(
                0.21f to 0.00f, 0.17f to 0.07f, 0.24f to 0.14f, 0.18f to 0.21f,
                0.26f to 0.28f, 0.19f to 0.35f, 0.25f to 0.42f, 0.17f to 0.50f,
                0.23f to 0.57f, 0.28f to 0.63f, 0.20f to 0.70f, 0.26f to 0.77f,
                0.19f to 0.84f, 0.24f to 0.91f, 0.21f to 1.00f,
            )
            p.moveTo(pts[0].first * w, 0f)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]; val curr = pts[i]
                val cpy = (prev.second + curr.second) / 2 * h
                val cpx = minOf(prev.first, curr.first) * w - 4f
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(w, h)
            p.lineTo(w, 0f)
            p.close()
        }

        PhotoEdgeShape.diagonalTR -> {
            val pts = listOf(
                1.00f to 0.05f,
                0.91f to 0.14f, 0.97f to 0.22f, 0.88f to 0.30f,
                0.79f to 0.38f, 0.85f to 0.46f, 0.76f to 0.53f,
                0.67f to 0.61f, 0.73f to 0.68f, 0.62f to 0.76f,
                0.53f to 0.83f, 0.59f to 0.90f, 0.48f to 0.97f,
                0.40f to 1.00f,
            )
            p.moveTo(0f, 0f)
            p.lineTo(w, 0f)
            p.lineTo(pts[0].first * w, pts[0].second * h)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]; val curr = pts[i]
                val cpx = (prev.first + curr.first) / 2 * w + (if (i % 2 == 0) 8f else -8f)
                val cpy = (prev.second + curr.second) / 2 * h
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(0f, h)
            p.lineTo(0f, 0f)
            p.close()
        }

        PhotoEdgeShape.diagonalBL -> {
            val pts = listOf(
                0.00f to 0.95f,
                0.09f to 0.86f, 0.03f to 0.78f, 0.12f to 0.70f,
                0.21f to 0.62f, 0.15f to 0.54f, 0.24f to 0.47f,
                0.33f to 0.39f, 0.27f to 0.32f, 0.38f to 0.24f,
                0.47f to 0.17f, 0.41f to 0.10f, 0.52f to 0.03f,
                0.60f to 0.00f,
            )
            p.moveTo(0f, 0f)
            p.lineTo(pts.last().first * w, 0f)
            for (i in pts.size - 2 downTo 0) {
                val next = pts[i + 1]; val curr = pts[i]
                val cpx = (next.first + curr.first) / 2 * w + (if (i % 2 == 0) -8f else 8f)
                val cpy = (next.second + curr.second) / 2 * h
                p.quadraticBezierTo(cpx, cpy, curr.first * w, curr.second * h)
            }
            p.lineTo(0f, h)
            p.close()
        }
    }
    return p
}

/** Shape de Compose para clip/stroke — espejo de edgeClipShape (iOS). */
class EdgeShape(private val shape: PhotoEdgeShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(edgePath(shape, size))
}
