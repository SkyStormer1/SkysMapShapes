package com.skystormer.skysmapshapes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShapeDraftTest {

    @Test
    fun aTypedSizeIsReadTheSameInBothEditors() {
        assertEquals(128.0, ShapeDraft.size(" 128 "))
        assertEquals(0.5, ShapeDraft.size("0.5"))
        assertEquals(Shape.MAX_TYPED_SIZE, ShapeDraft.size(Shape.MAX_TYPED_SIZE.toString()))
    }

    @Test
    fun aSizeThatCannotBeTypedIsRefused() {
        assertNull(ShapeDraft.size("0"))
        assertNull(ShapeDraft.size("-4"))
        assertNull(ShapeDraft.size("ten"))
        assertNull(ShapeDraft.size((Shape.MAX_TYPED_SIZE + 1).toString()))
    }
}
