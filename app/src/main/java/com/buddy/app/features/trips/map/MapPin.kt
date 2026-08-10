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
fun pinDeLugar(contexto: Context, nombre: String, seleccionado: Boolean): Drawable {
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
    val texto = if (seleccionado) recortar(nombre, etiqueta, px(150f)) else null
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

    tinta.color = if (seleccionado) ACENTO else ACENTO_TENUE
    tinta.style = Paint.Style.STROKE
    tinta.strokeWidth = borde
    canvas.drawCircle(cx, cy, radio, tinta)

    // Punto central en vez del glifo de categoría de iOS: Android no tiene ese
    // catálogo de iconos todavía, y un icono equivocado dice algo falso del
    // lugar mientras que un punto no dice nada.
    tinta.color = ACENTO
    tinta.style = Paint.Style.FILL
    canvas.drawCircle(cx, cy, px(if (seleccionado) 5.5f else 4.5f), tinta)

    if (texto != null) {
        val arriba = cy + diametro / 2f + separacion
        val cápsula = RectF(
            cx - anchoTexto / 2f - px(7f), arriba,
            cx + anchoTexto / 2f + px(7f), arriba + altoEtiqueta,
        )
        tinta.color = BLANCO
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
fun anclaVerticalDelPin(contexto: Context, seleccionado: Boolean): Float {
    val d = contexto.resources.displayMetrics.density
    val diametro = (if (seleccionado) 40f else 33f) * d
    val sombra = (if (seleccionado) 8f else 3f) * d
    val alto = if (seleccionado) diametro + sombra * 2 + 3f * d + 19f * d else diametro + sombra * 2
    return (sombra + diametro / 2f) / alto
}

private fun recortar(texto: String, pintura: Paint, maxAncho: Float): String {
    if (pintura.measureText(texto) <= maxAncho) return texto
    var corte = texto.length
    while (corte > 1 && pintura.measureText(texto.take(corte) + "…") > maxAncho) corte--
    return texto.take(corte) + "…"
}

private const val BLANCO = 0xFFFFFFFF.toInt()
private const val TINTA = 0xFF2B1C18.toInt()
private const val ACENTO = 0xFF6F9885.toInt()
private const val ACENTO_TENUE = 0x4D6F9885
