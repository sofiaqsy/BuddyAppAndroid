package com.buddy.app.core.data.model

import kotlinx.serialization.Serializable

/** Producto de un negocio (iOS FincasProduct). Todo opcional salvo id y nombre. */
@Serializable
data class FincasProduct(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String? = null,
    val imageUrl: String? = null,
    val photoUrl: String? = null,
    val presentations: List<Presentation>? = null,
) {
    @Serializable data class Photo(val id: String? = null, val url: String)

    @Serializable
    data class Presentation(
        val id: String,
        val packSize: Double? = null,
        val unit: String? = null,
        val price: Double? = null,
        val stock: Int? = null,
        val visible: Boolean? = null,
        val photos: List<Photo>? = null,
        val photoUrl: String? = null,
    ) {
        /** "250 g", "1 kg". */
        val label: String get() {
            val size = packSize?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
            return listOf(size, unit.orEmpty()).filter { it.isNotEmpty() }.joinToString(" ")
        }
        val shown: Boolean get() = visible != false
        val ownPhoto: String? get() = (photos?.firstOrNull()?.url ?: photoUrl)?.takeIf { it.isNotEmpty() }
    }

    /** La foto del producto o la primera de sus presentaciones. */
    val coverUrl: String? get() {
        (imageUrl ?: photoUrl)?.takeIf { it.isNotEmpty() }?.let { return it }
        return presentations.orEmpty().firstNotNullOfOrNull { it.ownPhoto }
    }

    fun coverFor(pres: Presentation?): String? = pres?.ownPhoto ?: coverUrl

    /** Una entrada por presentación visible (iOS: "show each presentation"). */
    fun entries(): List<Presentation?> {
        val shown = presentations.orEmpty().filter { it.shown }
        return if (shown.isEmpty()) listOf(null) else shown
    }
}

@Serializable
data class SpotProductsPage(
    val items: List<FincasProduct> = emptyList(),
    val total: Int? = null,
    val hasMore: Boolean? = null,
    val linked: Boolean? = null,
)

/** "S/ 20" si es entero, "S/ 25.90" si no. */
fun soles(v: Double): String =
    if (v % 1.0 == 0.0) "S/ ${v.toLong()}" else String.format(java.util.Locale.US, "S/ %.2f", v)
