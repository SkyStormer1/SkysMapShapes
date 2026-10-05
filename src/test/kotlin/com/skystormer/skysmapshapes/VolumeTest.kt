package com.skystormer.skysmapshapes

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The space MiniHUD's shapes take up in the world, for keeping light levels to inside them. */
class VolumeTest {

    private fun volume(json: String): Volume {
        val parsed = JsonParser.parseString(json).asJsonObject
        return MiniHudGeometry.volumeOf(parsed.get("type").asString, parsed)!!
    }

    @Test
    fun sphereIsRoundInEveryDirection() {
        val sphere = volume("""{"type":"sphere_blocky","snap":"center","center":[0.2,64.7,0.9],"radius":10.0}""")
        assertTrue(sphere.contains(0, 64, 0))
        assertTrue(sphere.contains(10, 64, 0))
        assertTrue(sphere.contains(0, 74, 0))
        assertFalse(sphere.contains(0, 75, 0))
        assertFalse(sphere.contains(8, 72, 0)) // inside the column, outside the ball
    }

    @Test
    fun spawnSphereMarginAddsToItsRadius() {
        val sphere = volume("""{"type":"despawn_sphere","snap":"center","center":[0.5,64.0,0.5],"radius":128.0,"margin":3.0}""")
        assertTrue(sphere.contains(130, 64, 0))
        assertFalse(sphere.contains(132, 64, 0))
    }

    @Test
    fun uprightPrismRunsUpFromItsCentreBlock() {
        val prism = volume("""{"type":"square","snap":"center","center":[0.5,10.0,0.5],"main_axis":"UP","radius":3.0,"height":5}""")
        assertTrue(prism.contains(3, 10, -3))
        assertTrue(prism.contains(0, 14, 0))
        assertFalse(prism.contains(0, 15, 0))
        assertFalse(prism.contains(0, 9, 0))
        assertFalse(prism.contains(4, 12, 0))
    }

    @Test
    fun prismLyingDownRunsAlongItsAxis() {
        val prism = volume("""{"type":"circle","snap":"center","center":[0.5,64.0,0.5],"main_axis":"WEST","radius":2.0,"height":4}""")
        assertTrue(prism.contains(-3, 64, 0))
        assertFalse(prism.contains(-4, 64, 0))
        assertFalse(prism.contains(1, 64, 0))
        assertTrue(prism.contains(-1, 66, 0))
    }

    @Test
    fun coneNarrowsToItsTop() {
        val cone = volume("""{"type":"cone","origin_x":0.0,"origin_y":0.0,"origin_z":0.0,"bottom_radius":10,"top_radius":0,"height":11,"direction":"UP"}""")
        assertTrue(cone.contains(10, 0, 0))
        assertFalse(cone.contains(10, 5, 0))
        assertTrue(cone.contains(0, 10, 0))
        assertFalse(cone.contains(1, 10, 0))
    }

    @Test
    fun boxIsEverythingBetweenItsCorners() {
        val box = volume("""{"type":"box","corner1":[10.0,70.0,0.0],"corner2":[0.0,60.0,10.0]}""")
        assertTrue(box.contains(0, 60, 0))
        assertTrue(box.contains(9, 69, 9))
        assertFalse(box.contains(10, 65, 5))
        assertFalse(box.contains(5, 59, 5))
    }

    @Test
    fun aLineHasNoInside() {
        val line = JsonParser.parseString("""{"type":"block_line","start":[0.0,64.0,0.0],"end":[10.0,64.0,0.0]}""").asJsonObject
        assertNull(MiniHudGeometry.volumeOf("block_line", line))
    }

    @Test
    fun ownShapesStandAtEveryHeight() {
        val column = Volume.Column(Geometry.Circle(0.5, 0.5, 5.0))
        assertTrue(column.contains(5, -60, 0))
        assertTrue(column.contains(5, 300, 0))
        assertFalse(column.contains(6, 64, 0))
    }
}
