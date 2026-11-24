package com.buddy.app.features.home.inicio

import com.buddy.app.core.data.model.ApiPlaceCard
import java.text.Normalizer
import java.util.Locale

/**
 * Lógica pura del Inicio en filas, copiada 1:1 de iOS (InicioView.swift:
 * HomeRowsView, PlaceSearch.swift, CategoryIcon). Sin Compose ni red: se prueba
 * con los mismos casos que BuddyAppTests (PlaceSearchTests).
 */
object InicioRowsLogic {
    const val NEARBY_ROUTE = "Cerca de ti"
    /** Lugares de otra ciudad no son "para descubrir" aquí. */
    const val LOCAL_RADIUS_METERS = 30_000

    /** Sin tildes ni mayúsculas (iOS: folding diacritic + case insensitive). */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)

    fun words(query: String): List<String> = fold(query.trim()).split(" ").filter { it.isNotEmpty() }

    /** Primero por nombre o categoría (todas las palabras); luego los que solo
     *  coinciden por un producto. Sin duplicados. */
    fun <T> searchResults(items: List<T>, query: String, text: (T) -> String, hasProductHit: (T) -> Boolean): List<T> {
        val w = words(query)
        if (w.isEmpty()) return emptyList()
        val byName = items.filter { item -> val t = fold(text(item)); w.all { t.contains(it) } }
        val byProduct = items.filter { hasProductHit(it) && it !in byName }
        return byName + byProduct
    }

    fun cardText(c: ApiPlaceCard) = "${c.name} ${c.category ?: ""}"

    /** Lugares de la zona; si ninguno está cerca (otra ciudad, sin GPS), todos. */
    fun local(cards: List<ApiPlaceCard>): List<ApiPlaceCard> {
        val near = cards.filter { (it.distanceMeters ?: 0) <= LOCAL_RADIUS_METERS }
        return near.ifEmpty { cards }
    }

    fun nearby(local: List<ApiPlaceCard>): List<ApiPlaceCard> =
        local.filter { it.distanceMeters != null }.sortedBy { it.distanceMeters ?: Int.MAX_VALUE }.take(10)

    fun categoryName(c: ApiPlaceCard): String {
        val cat = c.category?.trim().orEmpty()
        return cat.ifEmpty { "Otros lugares" }
    }

    /** Orden neutral, estable con el mismo token (sin popularidad). */
    fun rotated(items: List<ApiPlaceCard>, token: Int): List<ApiPlaceCard> =
        items.sortedBy { mix(it.id.hashCode(), token) }

    /** "430 m", "1.2 km" (iOS distanceText sin el "A "). */
    fun distanceText(m: Int?): String? {
        m ?: return null
        if (m < 1000) return "$m m"
        return String.format(Locale.US, "%.1f km", m / 1000.0)
    }

    /** Clave del ícono de categoría (iOS CategoryIcon; el orden importa). */
    fun iconKey(category: String): String {
        val n = fold(category)
        val rules = listOf(
            listOf("finca") to "tree", listOf("tostad") to "flame", listOf("cafe") to "cup",
            listOf("restaur", "comida") to "fork", listOf("hosped", "aloja", "hotel", "hostal") to "bed",
            listOf("tienda", "producto") to "bag", listOf("transport", "taxi", "alquiler", "vehicul") to "car",
            listOf("actividad", "guia", "tour", "aventura") to "hiking",
            listOf("natur", "lago", "laguna", "catarat", "mirador", "sendero") to "leaf",
            listOf("recreat", "jardin", "parque") to "sun", listOf("industria", "maquin") to "gear",
            listOf("evento", "festiv") to "calendar",
            listOf("cultur", "artes", "plaza", "comunidad", "museo") to "museum",
        )
        return rules.firstOrNull { (keys, _) -> keys.any { it in n } }?.second ?: "more"
    }

    fun productLine(name: String, price: Double?): String =
        "Tiene: " + name + (price?.let { String.format(Locale.US, " · S/ %.2f", it) } ?: "")

    /**
     * Intercala por tienda (Keyla, 23 Nov 2026): una de cada tienda por vuelta,
     * para que dos productos de la misma tienda no salgan seguidos. Si solo queda
     * una tienda con productos, la fila termina. Máximo [max].
     */
    fun <T> interleaveByStore(perStore: List<List<T>>, storeOf: (T) -> String, max: Int = 20): List<T> {
        val out = mutableListOf<T>()
        var round = 0
        while (out.size < max) {
            val batch = perStore.mapNotNull { it.getOrNull(round) }
            if (batch.isEmpty()) break
            if (batch.size == 1 && out.lastOrNull()?.let(storeOf) == storeOf(batch[0])) break
            out += batch
            round++
        }
        return out.take(max)
    }

    /** Orden neutral de cualquier lista por su id y el token de Inicio. */
    fun <T> shuffled(items: List<T>, token: Int, id: (T) -> String): List<T> =
        items.sortedBy { mix(id(it).hashCode(), token) }

    /** Mezcla id y token (Int.hashCode es la identidad: sin esto el orden
     *  nunca cambiaba al tocar Inicio otra vez). */
    private fun mix(h: Int, token: Int): Int {
        var x = h xor (token * -0x61c88647)
        x = (x xor (x ushr 16)) * -0x7a143595
        x = (x xor (x ushr 13)) * -0x3d4d51cb
        return x xor (x ushr 16)
    }
}
