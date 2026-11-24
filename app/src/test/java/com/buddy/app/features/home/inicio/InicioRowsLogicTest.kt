package com.buddy.app.features.home.inicio

import com.buddy.app.core.data.model.ApiPlaceCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismos casos que BuddyAppTests/PlaceSearchTests (iOS) más las reglas de filas. */
class InicioRowsLogicTest {
    private data class P(val name: String, val category: String, val product: Boolean = false)
    private val places = listOf(
        P("Tienda Rosal", "Tienda"), P("Café Mirador", "Cafetería"), P("El Puente", "Restaurante", product = true),
    )
    private fun search(q: String) =
        InicioRowsLogic.searchResults(places, q, { "${it.name} ${it.category}" }, { it.product }).map { it.name }

    private fun card(id: String, cat: String? = null, d: Int? = null) =
        ApiPlaceCard(id = id, name = id, category = cat, distanceMeters = d)

    @Test fun emptyQuery_returnsNothing() { assertEquals(emptyList<String>(), search("")); assertEquals(emptyList<String>(), search("   ")) }
    @Test fun ignoresAccentsAndCase() { assertEquals("Café Mirador", search("CAFE").first()); assertEquals("Café Mirador", search("cafeteria").first()) }
    @Test fun allWordsMustMatch() { assertEquals("Tienda Rosal", search("tienda rosal").first()); assertTrue("Tienda Rosal" !in search("tienda mirador")) }
    @Test fun matchesCategory() = assertEquals(listOf("El Puente"), search("restaurante"))
    @Test fun productHitsAfterNameHits_noDuplicates() {
        assertEquals(listOf("Tienda Rosal", "El Puente"), search("rosal"))
        assertEquals(listOf("El Puente"), search("puente"))
    }
    @Test fun words_trimAndSplit() = assertEquals(listOf("cafe", "rosal"), InicioRowsLogic.words("  Café  Rosal "))

    @Test fun distanceText() {
        assertEquals("430 m", InicioRowsLogic.distanceText(430))
        assertEquals("1.2 km", InicioRowsLogic.distanceText(1234))
        assertNull(InicioRowsLogic.distanceText(null))
    }
    @Test fun local_keepsNearOrAllWhenNoneNear() {
        val near = card("a", d = 500); val far = card("b", d = 240_000); val unknown = card("c")
        assertEquals(listOf(near, unknown), InicioRowsLogic.local(listOf(near, far, unknown)))
        assertEquals(listOf(far), InicioRowsLogic.local(listOf(far)))
    }
    @Test fun nearby_onlyWithDistance_sorted_max10() {
        val cards = (1..12).map { card("p$it", d = 1000 - it) } + card("x")
        val r = InicioRowsLogic.nearby(cards)
        assertEquals(10, r.size); assertEquals("p12", r.first().id)
    }
    @Test fun iconKey_orderMatters() {
        assertEquals("tree", InicioRowsLogic.iconKey("Fincas cafetaleras"))
        assertEquals("flame", InicioRowsLogic.iconKey("Tostadurías"))
        assertEquals("cup", InicioRowsLogic.iconKey("Cafeterías"))
        assertEquals("bag", InicioRowsLogic.iconKey("Productos locales"))
        assertEquals("more", InicioRowsLogic.iconKey("Otro"))
    }
    @Test fun rotated_stableForSameToken_sameItems() {
        val c = (1..6).map { card("p$it") }
        assertEquals(InicioRowsLogic.rotated(c, 1), InicioRowsLogic.rotated(c, 1))
        assertEquals(c.toSet(), InicioRowsLogic.rotated(c, 2).toSet())
    }
    @Test fun productLine() {
        assertEquals("Tiene: Cafe Tostado · S/ 10.00", InicioRowsLogic.productLine("Cafe Tostado", 10.0))
        assertEquals("Tiene: Miel", InicioRowsLogic.productLine("Miel", null))
    }
    // Productos locales: dos productos de la misma tienda nunca seguidos.
    @Test fun interleave_alternatesStores() {
        val a = listOf("a1", "a2", "a3"); val b = listOf("b1", "b2")
        assertEquals(listOf("a1", "b1", "a2", "b2", "a3"), InicioRowsLogic.interleaveByStore(listOf(a, b), { it.take(1) }))
    }
    @Test fun interleave_singleStoreShowsOne() {
        assertEquals(listOf("a1"), InicioRowsLogic.interleaveByStore(listOf(listOf("a1", "a2")), { it.take(1) }))
    }
    @Test fun interleave_capsAtMax() {
        val stores = (1..5).map { s -> (1..10).map { "$s-$it" } }
        assertEquals(20, InicioRowsLogic.interleaveByStore(stores, { it.substringBefore("-") }).size)
    }
    @Test fun shuffled_changesWithToken() {
        val items = (1..12).map { "p$it" }
        val one = InicioRowsLogic.shuffled(items, 1) { it }
        assertEquals(one, InicioRowsLogic.shuffled(items, 1) { it })
        org.junit.Assert.assertNotEquals(one, InicioRowsLogic.shuffled(items, 2) { it })
    }
}
