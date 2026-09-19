package com.skystormer.skysmapshapes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShapeTest {

    private fun circle(radius: Double) = Shape(label = "", dimension = "minecraft:overworld", type = Shape.Type.CIRCLE, x = 100, z = -40, radius = radius, colour = 0)

    @Test
    fun circleIsCentredOnTheMiddleOfItsBlock() {
        val b = circle(128.0).bounds
        assertEquals(100.5 - 128, b.minX)
        assertEquals(100.5 + 128, b.maxX)
        assertEquals(-39.5 - 128, b.minZ)
    }

    @Test
    fun circleContainsBlocksWithinItsRadius() {
        val c = circle(32.0)
        assertTrue(c.contains(100, -40))
        assertTrue(c.contains(132, -40))
        assertFalse(c.contains(133, -40))
        assertFalse(c.contains(124, -16)) // inside the square around it, outside the circle
    }

    @Test
    fun cornerRectangleStartsAtItsBlockEdge() {
        val r = Shape(label = "", dimension = "d", type = Shape.Type.RECTANGLE, x = 16, z = 32, width = 48.0, length = 16.0, anchor = Shape.Anchor.CORNER, colour = 0)
        val b = r.bounds
        assertEquals(16.0, b.minX)
        assertEquals(64.0, b.maxX)
        assertEquals(48.0, b.maxZ)
        assertTrue(r.contains(63, 47))
        assertFalse(r.contains(64, 47))
    }

    @Test
    fun circleOutlinePointsAreOnTheRadius() {
        val points = circle(10.0).geometry.points(1f)
        for (i in 0 until points.size / 2) {
            val dx = points[i * 2] - 100.5
            val dz = points[i * 2 + 1] + 39.5
            assertEquals(10.0, Math.sqrt(dx * dx + dz * dz), 1e-9)
        }
    }

    @Test
    fun diamondOctagonAndEllipse() {
        fun of(type: Shape.Type, radius: Double = 0.0, w: Double = 0.0, l: Double = 0.0) =
            Shape(label = "", dimension = "d", type = type, x = 0, z = 0, radius = radius, width = w, length = l, colour = 0)
        val diamond = of(Shape.Type.RHOMBUS, radius = 10.0)
        assertTrue(diamond.contains(0, 9))
        assertFalse(diamond.contains(6, 6)) // inside its square, outside the diamond
        assertEquals(200.0, diamond.area, 1e-9)
        val octagon = of(Shape.Type.OCTAGON, radius = 10.0)
        assertEquals(10.0, octagon.bounds.maxX - 0.5, 1e-9)
        assertFalse(octagon.contains(9, 9))
        val ellipse = of(Shape.Type.ELLIPSE, w = 40.0, l = 10.0)
        assertTrue(ellipse.contains(19, 0))
        assertFalse(ellipse.contains(0, 6))
    }

    @Test
    fun distanceToOutlineIsMeasuredFromTheNearestEdge() {
        val c = circle(32.0)
        assertEquals(0.0, c.distanceToOutline(132.5, -39.5), 1e-9)
        assertEquals(32.0, c.distanceToOutline(100.5, -39.5), 1e-9)
        val r = Shape(label = "", dimension = "d", type = Shape.Type.RECTANGLE, x = 0, z = 0, width = 10.0, length = 10.0, anchor = Shape.Anchor.CORNER, colour = 0)
        assertEquals(2.0, r.distanceToOutline(2.0, 5.0), 1e-9) // inside, 2 from the west edge
        assertEquals(5.0, r.distanceToOutline(13.0, 14.0), 1e-9) // outside the south-east corner
    }

    @Test
    fun areaOrdersLargestFirst() {
        val big = circle(128.0)
        val small = circle(32.0)
        val shapes = listOf(small, big).sortedByDescending { it.area }
        assertEquals(listOf(big, small), shapes)
    }

    @Test
    fun globalScaleMultipliesEachShapesOwnThickness() {
        val thick = circle(10.0).copy(lineWidth = 4f)
        val thin = circle(10.0).copy(lineWidth = 1f)
        Config.thicknessScale = 1.5f
        try {
            assertEquals(6f, thick.effectiveLineWidth)
            assertEquals(1.5f, thin.effectiveLineWidth)
        } finally {
            Config.thicknessScale = 1f
        }
    }

    @Test
    fun newShapesAreNotFilled() {
        assertFalse(circle(10.0).fill)
    }

    @Test
    fun coloursRoundTrip() {
        assertEquals(Colours.RED.argb, Colours.parse(Colours.format(Colours.RED.argb)))
        assertEquals("Red", Colours.name(Colours.RED.argb))
        assertEquals("#123456", Colours.name(Colours.parse("#123456")!!))
    }
}
