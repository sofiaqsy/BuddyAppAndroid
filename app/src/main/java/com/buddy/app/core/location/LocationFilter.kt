package com.buddy.app.core.location

import com.buddy.app.core.data.model.ApiPlaceCard
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Espejo de LocationFilter.swift (iOS). Todo son funciones puras: la UI no
// sabe nada de precisión ni de ruido del GPS, y se pueden probar sin GPS.

/** Decide qué fixes son lo bastante buenos para mostrar distancias. */
object LocationFilter {
    /** Peor precisión aceptable. Con ±16-19 m el GPS "movía" al viajero
     *  10-14 m por fix estando casi quieto. */
    const val MAX_ACCURACY_M = 20f

    /** Acepta fixes de ±20 m o mejores. Mientras no haya NINGUNO aceptado se
     *  acepta cualquiera: en interiores la precisión puede no bajar nunca de
     *  20 m y es peor no mostrar distancias que mostrarlas algo toscas.
     *  [accuracy] nulo = el fix no trae precisión. */
    fun accept(accuracy: Float?, hasStable: Boolean): Boolean {
        if (accuracy == null || accuracy < 0f) return !hasStable
        return accuracy <= MAX_ACCURACY_M || !hasStable
    }
}

/** Convierte ubicación + lugar en lo que ve el viajero, con estabilidad. */
object DistanceResolver {
    /** "Estás aquí" se enciende a 30 m y solo se apaga pasados 45 m. */
    const val HERE_ENTER_M = 30.0
    const val HERE_EXIT_M = 45.0

    /** Un lugar solo le quita el puesto de "más cercano" al actual si está al
     *  menos 10 m más cerca: El encanto y Cafetería Rosal están a ~40 m. */
    const val NEAREST_MARGIN_M = 10.0

    /** Haversine en metros. */
    fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    fun distance(lat: Double?, lng: Double?, place: ApiPlaceCard): Double? {
        if (lat == null || lng == null) return null
        val pLat = place.lat ?: return null
        val pLng = place.lng ?: return null
        return meters(lat, lng, pLat, pLng)
    }

    /** Cambio mínimo para actualizar la etiqueta: 15 % con piso de 5 m y techo
     *  de 50 m. Un umbral fijo de 20 m se sentía lento a 25-30 m del lugar. */
    fun minChange(shown: Double): Double = min(50.0, max(5.0, shown * 0.15))

    fun shouldUpdate(shown: Double?, new: Double): Boolean =
        shown == null || abs(new - shown) >= minChange(shown)

    fun isHere(wasHere: Boolean, distance: Double?, isNearest: Boolean): Boolean {
        if (!isNearest || distance == null) return false
        return if (wasHere) distance <= HERE_EXIT_M else distance <= HERE_ENTER_M
    }

    fun nearest(current: String?, candidates: List<Pair<String, Double>>): String? {
        val best = candidates.minByOrNull { it.second } ?: return null
        val actual = candidates.firstOrNull { it.first == current } ?: return best.first
        return if (best.second < actual.second - NEAREST_MARGIN_M) best.first else current
    }

    /** "120 m", "3,1 km", "236 km". "Estás aquí" lo decide [isHere]. */
    fun label(d: Double): String = when {
        d < 1000 -> "${max(10, (d / 10).roundToIntSafe() * 10)} m"
        d < 10_000 -> "${String.format(Locale.US, "%.1f", (d / 100).roundToIntSafe() / 10.0).replace('.', ',')} km"
        else -> "${(d / 1000).roundToIntSafe()} km"
    }

    /** Orden por cercanía que no baila con el ruido: tramos de 10 m y, dentro
     *  del mismo tramo, el orden que ya había. Sin coordenadas, al final. */
    fun stableOrder(cards: List<ApiPlaceCard>, lat: Double, lng: Double): List<ApiPlaceCard> =
        cards.withIndex()
            .sortedWith(compareBy(
                { distance(lat, lng, it.value)?.let { d -> floor(d / 10).toInt() } ?: Int.MAX_VALUE },
                { it.index },
            ))
            .map { it.value }

    /** Distancias reales siempre son finitas; esto solo evita sorpresas. */
    private fun Double.roundToIntSafe(): Int =
        if (isNaN() || isInfinite()) 0 else Math.round(this).toInt()
}
