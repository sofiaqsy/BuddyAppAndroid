package com.buddy.app.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mismos casos que StoryPagesTests (iOS): publicaciones seguidas en un solo visor. */
class ViewerPagesTest {
    private fun pages(counts: List<Int>) =
        ViewerPages.make(counts.mapIndexed { g, n -> List(n) { p -> "j${g}p$p" } }) { "cover$it" }

    @Test fun photosOfAllPublicationsInOrder() {
        val r = pages(listOf(2, 3))
        assertEquals(listOf("j0p0", "j0p1", "j1p0", "j1p1", "j1p2"), r.map { it.url })
        assertEquals(1, r[2].group); assertEquals(0, r[2].index)
    }
    @Test fun publicationWithoutPhotosShowsItsCover() {
        val r = pages(listOf(1, 0, 2))
        assertEquals(4, r.size); assertEquals("cover1", r[1].url)
    }
    @Test fun singlePublicationUnchanged() = assertEquals(listOf(0, 1, 2), pages(listOf(3)).map { it.index })
    @Test fun openAtTappedPublication() {
        val r = pages(listOf(2, 3, 1))
        assertEquals(2, ViewerPages.firstOf(r, 1)); assertEquals(5, ViewerPages.firstOf(r, 2)); assertEquals(-1, ViewerPages.firstOf(r, 3))
    }
}
