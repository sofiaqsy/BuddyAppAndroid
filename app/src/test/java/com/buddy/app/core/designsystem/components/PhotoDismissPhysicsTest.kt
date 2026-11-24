package com.buddy.app.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mismos casos que PhotoDismissPhysicsTests (iOS): cerrar arrastrando hacia abajo. */
class PhotoDismissPhysicsTest {
    @Test fun scale_shrinks_neverBelowMin() {
        assertEquals(1f, PhotoDismissPhysics.scale(0f))
        assertTrue(PhotoDismissPhysics.scale(200f) < 1f)
        assertEquals(PhotoDismissPhysics.MIN_SCALE, PhotoDismissPhysics.scale(5000f))
        assertEquals(1f, PhotoDismissPhysics.scale(-100f))
    }
    @Test fun backdrop_fadesProgressively() {
        assertEquals(1f, PhotoDismissPhysics.backdrop(0f))
        val mid = PhotoDismissPhysics.backdrop(200f); assertTrue(mid > 0f && mid < 1f)
        assertEquals(0f, PhotoDismissPhysics.backdrop(900f))
    }
    @Test fun dismiss_byDistance_orFastSwipe_elseReturns() {
        assertTrue(PhotoDismissPhysics.shouldDismiss(140f, 0f))
        assertTrue(PhotoDismissPhysics.shouldDismiss(60f, 1500f))
        assertFalse(PhotoDismissPhysics.shouldDismiss(60f, 200f))
        assertFalse(PhotoDismissPhysics.shouldDismiss(10f, 3000f))
    }
}
