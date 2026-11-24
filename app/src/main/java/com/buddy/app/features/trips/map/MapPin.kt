package com.buddy.app.features.trips.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Pin de un lugar recomendado — espejo de RecommendationPin (iOS).
 *
 * POR QUÉ EL ELEGIDO ES MÁS GRANDE
 *
 * Con todos los pines iguales, elegir uno no se veía en el mapa: la ficha de
 * abajo cambiaba y el mapa seguía diciendo lo mismo, así que había que
 * reconstruir a ojo cuál de los puntos era el que se estaba leyendo. El tamaño
 * es la señal más barata de leer —se nota sin mirar de frente— y no gasta
 * color, que aquí ya significa otra cosa.
 *
 * El nombre solo aparece en el elegido: con las etiquetas siempre puestas, seis
 * lugares cercanos se solapan y el mapa deja de verse.
 *
 * Se dibuja a Canvas y no con un vector: el pin cambia de tamaño, de grosor de
 * borde y de contenido según el estado, y son cuatro primitivas.
 */
fun pinDeLugar(
    contexto: Context,
    nombre: String,
    seleccionado: Boolean,
    /** Por defecto solo el elegido lleva nombre (mapa del destino). El selector
     *  de "Buscar en el mapa" lo pide siempre: ahí el nombre ES la pista para
     *  saber si el lugar ya existe. */
    conNombre: Boolean = seleccionado,
    /** Categoría del lugar: dibuja su icono, como iOS (Keyla, 24 Nov 2026). */
    categoria: String? = null,
): Drawable {
    val d = contexto.resources.displayMetrics.density
    fun px(dp: Float) = dp * d

    val diametro = px(if (seleccionado) 40f else 33f)
    val borde = px(if (seleccionado) 2f else 1.25f)
    val sombra = px(if (seleccionado) 8f else 3f)

    val tinta = Paint(Paint.ANTI_ALIAS_FLAG)
    val etiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = px(11f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = TINTA
    }

    // El texto se recorta antes de medir el lienzo: un nombre largo estiraría el
    // bitmap a lo ancho y el pin quedaría descentrado sobre su punto.
    val texto = if (conNombre) recortar(nombre, etiqueta, px(if (seleccionado) 150f else 110f)) else null
    val anchoTexto = texto?.let { etiqueta.measureText(it) } ?: 0f
    val altoEtiqueta = if (texto != null) px(19f) else 0f
    val separacion = if (texto != null) px(3f) else 0f

    val ancho = maxOf(diametro + sombra * 2, anchoTexto + px(14f))
    val alto = diametro + sombra * 2 + separacion + altoEtiqueta
    val bitmap = Bitmap.createBitmap(ancho.toInt(), alto.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = ancho / 2f
    val cy = sombra + diametro / 2f
    val radio = diametro / 2f - borde / 2f

    // Círculo blanco con aro de color: el mismo pin del catálogo de iOS. El
    // blanco lo despega del mapa, que aquí es claro y con mucho detalle.
    tinta.setShadowLayer(sombra, 0f, px(1.5f), 0x30000000)
    tinta.color = BLANCO
    tinta.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, radio, tinta)
    tinta.clearShadowLayer()

    // Elegido: aro terracota grueso, como iOS. Los demás: aro tenue.
    tinta.color = if (seleccionado) TERRACOTA else BORDE_TENUE
    tinta.style = Paint.Style.STROKE
    tinta.strokeWidth = if (seleccionado) px(3f) else borde
    canvas.drawCircle(cx, cy, radio, tinta)

    // Icono de la categoría (los mismos de Inicio). Sin categoría, un punto.
    if (categoria != null) {
        dibujarIcono(canvas, com.buddy.app.features.home.inicio.categoryIcon(categoria),
            cx, cy, px(if (seleccionado) 20f else 17f), BOSQUE)
    } else {
        tinta.color = ACENTO
        tinta.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, px(if (seleccionado) 5.5f else 4.5f), tinta)
    }

    if (texto != null) {
        val arriba = cy + diametro / 2f + separacion
        val cápsula = RectF(
            cx - anchoTexto / 2f - px(7f), arriba,
            cx + anchoTexto / 2f + px(7f), arriba + altoEtiqueta,
        )
        tinta.color = BLANCO
        tinta.style = Paint.Style.FILL   // venía en STROKE del aro: la etiqueta salía hueca
        tinta.setShadowLayer(px(3f), 0f, px(1f), 0x28000000)
        canvas.drawRoundRect(cápsula, altoEtiqueta / 2f, altoEtiqueta / 2f, tinta)
        tinta.clearShadowLayer()

        etiqueta.textAlign = Paint.Align.CENTER
        val base = cápsula.centerY() - (etiqueta.descent() + etiqueta.ascent()) / 2f
        canvas.drawText(texto, cx, base, etiqueta)
    }

    return BitmapDrawable(contexto.resources, bitmap)
}

/** Dónde cae el punto real dentro del bitmap: el centro del círculo, no el del
 *  lienzo — con etiqueta debajo, anclar al centro dejaría el pin flotando por
 *  encima de su propia coordenada. */
fun anclaVerticalDelPin(contexto: Context, seleccionado: Boolean, conNombre: Boolean = seleccionado): Float {
    val d = contexto.resources.displayMetrics.density
    val diametro = (if (seleccionado) 40f else 33f) * d
    val sombra = (if (seleccionado) 8f else 3f) * d
    val alto = if (conNombre) diametro + sombra * 2 + 3f * d + 19f * d else diametro + sombra * 2
    return (sombra + diametro / 2f) / alto
}

private fun recortar(texto: String, pintura: Paint, maxAncho: Float): String {
    if (pintura.measureText(texto) <= maxAncho) return texto
    var corte = texto.length
    while (corte > 1 && pintura.measureText(texto.take(corte) + "…") > maxAncho) corte--
    return texto.take(corte) + "…"
}

private const val BLANCO = 0xFFFFFFFF.toInt()
private const val TERRACOTA = 0xFFC87950.toInt()
private const val BOSQUE = 0xFF2F4A3E.toInt()
private const val BORDE_TENUE = 0x22000000
private const val TINTA = 0xFF2B1C18.toInt()
private const val ACENTO = 0xFF6F9885.toInt()
private const val ACENTO_TENUE = 0x4D6F9885

/** Pinta un icono de Material (ImageVector) en un Canvas de Android. */
private fun dibujarIcono(canvas: Canvas, icono: androidx.compose.ui.graphics.vector.ImageVector,
                         cx: Float, cy: Float, lado: Float, color: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val escala = lado / icono.viewportWidth
    canvas.save()
    canvas.translate(cx - lado / 2f, cy - lado / 2f)
    canvas.scale(escala, escala)
    fun recorrer(g: androidx.compose.ui.graphics.vector.VectorGroup) {
        for (n in g) when (n) {
            is androidx.compose.ui.graphics.vector.VectorPath -> {
                val path = androidx.compose.ui.graphics.vector.PathParser().addPathNodes(n.pathData).toPath()
                    .let { (it as androidx.compose.ui.graphics.AndroidPath).internalPath }
                path.fillType = if (n.pathFillType == androidx.compose.ui.graphics.PathFillType.EvenOdd)
                    android.graphics.Path.FillType.EVEN_ODD else android.graphics.Path.FillType.WINDING
                canvas.drawPath(path, paint)
            }
            is androidx.compose.ui.graphics.vector.VectorGroup -> recorrer(n)
        }
    }
    recorrer(icono.root)
    canvas.restore()
}
