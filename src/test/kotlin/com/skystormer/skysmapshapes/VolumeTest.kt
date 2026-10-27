package com.skystormer.skysmapshapes

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
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
        // Snapped to the middle of its block across, but to its bottom up and down, as MiniHUD snaps it.
        val sphere = volume("""{"type":"sphere_blocky","snap":"center","center":[0.2,64.7,0.9],"radius":10.0}""")
        assertTrue(sphere.contains(0, 64, 0))
        assertTrue(sphere.contains(9, 64, 0))
        assertFalse(sphere.contains(10, 64, 0)) // its middle is 10 across and half a block up: just out
        assertTrue(sphere.contains(0, 73, 0))
        assertFalse(sphere.contains(0, 74, 0))
        assertTrue(sphere.contains(0, 54, 0))
        assertFalse(sphere.contains(0, 53, 0))
        assertFalse(sphere.contains(8, 72, 0)) // inside the column, outside the ball
    }

    @Test
    fun spawnSphereIsMeasuredFromItsMarginsSquare() {
        // MiniHUD measures from the top of each block to a square the margin wide around the
        // centre: not a sphere the margin bigger, which reached a block or two too high.
        val sphere = volume("""{"type":"despawn_sphere","snap":"center","center":[0.5,64.0,0.5],"radius":128.0,"margin":3.0}""")
        assertTrue(sphere.contains(130, 64, 0))
        assertFalse(sphere.contains(132, 64, 0))
        assertTrue(sphere.contains(0, 190, 0))
        assertFalse(sphere.contains(0, 192, 0))
    }

    @Test
    fun clippedSpawnSphereIsTrimmedTopAndBottom() {
        val sphere = volume("""{"type":"clipped_spawn_sphere_y","snap":"center","center":[0.5,64.0,0.5],"radius":24.0,"margin":0.0,"top_trim":3.0,"bottom_trim":5.0}""")
        assertTrue(sphere.contains(0, 84, 0))
        assertFalse(sphere.contains(0, 85, 0)) // above 64 + 24 - 3
        assertTrue(sphere.contains(0, 45, 0))
        assertFalse(sphere.contains(0, 44, 0)) // below 64 - 24 + 5
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
        // Across it, measured from the centre snapped to its block's bottom, as MiniHUD snaps it.
        assertTrue(prism.contains(-1, 65, 0))
        assertFalse(prism.contains(-1, 66, 0))
        assertTrue(prism.contains(-1, 62, 0))
        assertFalse(prism.contains(-1, 61, 0))
    }

    @Test
    fun coneNarrowsToItsTop() {
        val cone = volume("""{"type":"cone","origin_x":0.0,"origin_y":0.0,"origin_z":0.0,"bottom_radius":10,"top_radius":0,"height":11,"direction":"UP"}""")
        assertTrue(cone.contains(10, 0, 0))
        assertFalse(cone.contains(10, 5, 0))
        assertTrue(cone.contains(0, 10, 0))
        assertFalse(cone.contains(1, 10, 0))
        // MiniHUD lays one layer more than its height, down to the top radius.
        assertTrue(cone.contains(0, 11, 0))
        assertFalse(cone.contains(0, 12, 0))
    }

    @Test
    fun octagonPyramidCutsItsCornersAtHalfAsMuchAgain() {
        val octagon = volume("""{"type":"octagon_pyramid","origin_x":0,"origin_y":0,"origin_z":0,"bottom_radius":10,"top_radius":10,"height":2,"direction":"up"}""")
        assertTrue(octagon.contains(10, 0, 5))
        assertFalse(octagon.contains(10, 0, 6))
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

    @Test
    fun aDirectionReadsTheSameInEitherCase() {
        fun cone(direction: String) = volume(
            """{"type":"cone","origin_x":0,"origin_y":64,"origin_z":0,"bottom_radius":4,"top_radius":0,"height":6,"direction":"$direction"}"""
        )
        fun prism(axis: String) = volume("""{"type":"square","center":[0.5,64,0.5],"main_axis":"$axis","radius":2.0,"height":5}""")
        for ((small, big) in listOf(cone("north") to cone("NORTH"), prism("west") to prism("WEST"))) {
            for (x in -8..8) for (y in 56..72) for (z in -8..8) assertEquals(big.contains(x, y, z), small.contains(x, y, z))
        }
        // North runs to smaller z: the cone's tip side, and nothing behind its base.
        val north = cone("north")
        assertTrue(north.contains(0, 64, -5))
        assertFalse(north.contains(0, 64, 2))
    }
}
