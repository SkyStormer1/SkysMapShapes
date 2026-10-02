package com.skystormer.skysmapshapes

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Shapes from other players' chat lines are checked before they are kept, drawn or written anywhere. */
class UntrustedTest {

    private fun circle(radius: Double = 64.0, dimension: String = Dimensions.OVERWORLD, x: Int = 0) =
        Shape(label = "", dimension = dimension, type = Shape.Type.CIRCLE, x = x, z = 0, radius = radius, colour = Colours.RED.argb)

    @Test
    fun ordinaryShapesAreValid() {
        assertTrue(circle().isValid())
        assertTrue(circle(dimension = "somemod:sky_islands").isValid())
    }

    @Test
    fun sizesNoWorldHasAreNot() {
        assertFalse(circle(radius = Double.NaN).isValid())
        assertFalse(circle(radius = Double.POSITIVE_INFINITY).isValid())
        assertFalse(circle(radius = 1e300).isValid())
        assertFalse(circle(radius = -5.0).isValid())
        assertFalse(circle(x = Int.MAX_VALUE).isValid())
    }

    @Test
    fun aDimensionThatCouldLeaveMiniHudsFolderIsNot() {
        assertFalse(circle(dimension = "x:../../../mods/evil").isValid())
        assertFalse(circle(dimension = "x:a/b").isValid())
        assertFalse(circle(dimension = "no colon").isValid())
    }

    @Test
    fun suchLinesFromChatAreIgnored() {
        assertNull(ShapeShare.readLine("<Steve> Map shape: circle rNaN at 0 0 (Overworld)"))
        assertNull(ShapeShare.readLine("<Steve> Map shape: circle r1e999 at 0 0 (Overworld)"))
        assertNull(ShapeShare.readLine("<Steve> Map shape: circle r5 at 0 0 (x:../../../mods/evil)"))
    }
}
