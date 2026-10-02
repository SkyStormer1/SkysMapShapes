package com.skystormer.skysmapshapes

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** MiniHUD shapes in the JSON format MiniHUD 0.40.7 saves them in, turned into map outlines. */
class MiniHudTest {

    private fun parse(json: String) = MiniHudShapes.parse(JsonParser.parseString(json).asJsonObject, "minecraft:the_nether")

    @Test
    fun despawnSphereIsItsWidestCircle() {
        val shape = parse("""{"type":"despawn_sphere","enabled":true,"display_name":"Farm sphere","center":[120.25,38.5,-64.75],"radius":128.0,"margin":3.0,"color":1613803696}""")!!
        val g = shape.geometry as Geometry.Circle
        assertEquals(120.25, g.cx, 1e-9)
        assertEquals(-64.75, g.cz, 1e-9)
        assertEquals(128.0, g.radius)
        assertEquals("Farm sphere", shape.name)
        assertEquals(0xFF, shape.colour ushr 24) // drawn opaque whatever MiniHUD's alpha
    }

    @Test
    fun uprightSquarePrismIsASquare() {
        val shape = parse("""{"type":"square","enabled":true,"display_name":"Prism","center":[120.5,0.0,-64.5],"main_axis":"UP","radius":131.0,"height":256}""")!!
        val b = shape.bounds
        assertEquals(120.5 - 131, b.minX, 1e-9)
        assertEquals(-64.5 + 131, b.maxZ, 1e-9)
    }

    @Test
    fun boxIsItsCornersRectangle() {
        val shape = parse("""{"type":"box","enabled":true,"corner1":[16.5,0.0,32.5],"corner2":[-26.5,256.0,75.5]}""")!!
        val b = shape.bounds
        assertEquals(-26.5, b.minX); assertEquals(16.5, b.maxX)
        assertEquals(32.5, b.minZ); assertEquals(75.5, b.maxZ)
    }

    @Test
    fun rhombusIsADiamond() {
        val shape = parse("""{"type":"rhombus","center":[0.5,64,0.5],"main_axis":"UP","radius":10.0,"height":1}""")!!
        assertTrue(shape.geometry.contains(0.5, 10.0))
        assertFalse(shape.geometry.contains(7.0, 7.0))
    }

    @Test
    fun sidewaysCircleIsARectangleAlongItsAxis() {
        val shape = parse("""{"type":"circle","center":[0.0,64,0.0],"main_axis":"EAST","radius":5.0,"height":20}""")!!
        val b = shape.bounds
        assertEquals(0.0, b.minX); assertEquals(20.0, b.maxX)
        assertEquals(-5.0, b.minZ); assertEquals(5.0, b.maxZ)
    }

    @Test
    fun uprightPyramidIsItsBase() {
        val shape = parse("""{"type":"pyramid","origin_x":0.0,"origin_y":64.0,"origin_z":0.0,"bottom_radius":8.0,"top_radius":0.0,"direction":"UP","height":8}""")!!
        assertEquals(256.0, shape.area, 1e-9)
    }

    @Test
    fun blockLineIsAnOpenLine() {
        val shape = parse("""{"type":"block_line","start":[0,64,0],"end":[10,64,0]}""")!!
        assertFalse(shape.geometry.closed)
        assertEquals(0.0, shape.geometry.distanceTo(5.5, 0.5), 1e-9)
    }

    @Test
    fun shapesSwitchedOffInMiniHudAreListedButNotDrawn() {
        val json = """{"type":"despawn_sphere","enabled":false,"center":[0,0,0],"radius":128.0}"""
        val shape = parse(json)
        assertNotNull(shape) // still listed, so it can be switched back on from the map
        assertFalse(shape!!.visible)
        assertFalse(shape.enabledInMiniHud)
        Config.miniHudIncludeDisabled = true
        try {
            assertTrue(parse(json)!!.visible)
        } finally {
            Config.miniHudIncludeDisabled = false
        }
    }

    @Test
    fun hidingAShapeMiniHudCannotReachHidesItOnTheMapOnly() {
        // No live MiniHUD object (another dimension), so MiniHUD is left alone.
        val shape = parse("""{"type":"despawn_sphere","enabled":true,"center":[7,0,7],"radius":16.0}""")!!
        assertTrue(shape.visible)
        try {
            ShapeStore.setVisible(shape, false)
            assertFalse(shape.visible)
            assertTrue(shape.enabledInMiniHud)
        } finally {
            ShapeStore.setVisible(shape, true)
        }
        assertTrue(shape.visible)
    }

    @Test
    fun ownShapesBecomeMiniHudShapesOfTheRightType() {
        fun json(type: Shape.Type, radius: Double = 0.0, w: Double = 0.0, l: Double = 0.0, form: MiniHudGeometry.Form? = null) =
            MiniHudGeometry.toMiniHudJson(
                Shape(label = "Test", dimension = "d", type = type, x = 10, z = -20, radius = radius, width = w, length = l, colour = Colours.RED.argb),
                70,
                form,
            )!!

        // A circle stands up as a cylinder unless a sphere is asked for.
        assertEquals("circle", json(Shape.Type.CIRCLE, radius = 128.0).get("type").asString)
        val sphere = json(Shape.Type.CIRCLE, radius = 128.0, form = MiniHudGeometry.Form.SPHERE)
        assertEquals("sphere_blocky", sphere.get("type").asString)
        assertEquals(128.0, sphere.get("radius").asDouble)
        assertEquals(10.5, sphere.getAsJsonArray("center")[0].asDouble)
        assertEquals(70.0, sphere.getAsJsonArray("center")[1].asDouble)

        assertEquals("square", json(Shape.Type.SQUARE, radius = 16.0).get("type").asString)
        assertEquals("rhombus", json(Shape.Type.RHOMBUS, radius = 16.0).get("type").asString)
        assertEquals("ellipsoid_spawn", json(Shape.Type.ELLIPSE, w = 20.0, l = 10.0).get("type").asString)

        // A pyramid with the same radius top and bottom is the octagon prism MiniHUD has no type for.
        val octagon = json(Shape.Type.OCTAGON, radius = 8.0)
        assertEquals("octagon_pyramid", octagon.get("type").asString)
        assertEquals(octagon.get("bottom_radius").asDouble, octagon.get("top_radius").asDouble)

        val box = json(Shape.Type.RECTANGLE, w = 10.0, l = 4.0)
        assertEquals("box", box.get("type").asString)
        assertEquals(5.5, box.getAsJsonArray("corner1")[0].asDouble)   // 10.5 - 10/2
        assertEquals(-17.5, box.getAsJsonArray("corner2")[2].asDouble) // -19.5 + 4/2
    }

    /** What is made in MiniHUD reads back as the same outline on the map. */
    @Test
    fun aShapeMadeInMiniHudComesBackTheSameSize() {
        val ours = Shape(label = "Ring", dimension = "d", type = Shape.Type.CIRCLE, x = 10, z = -20, radius = 96.0, colour = Colours.RED.argb)
        val back = MiniHudShapes.parse(MiniHudGeometry.toMiniHudJson(ours, 64)!!, "d")!!
        val circle = back.geometry as Geometry.Circle
        assertEquals(ours.bounds.centreX, circle.cx)
        assertEquals(ours.bounds.centreZ, circle.cz)
        assertEquals(96.0, circle.radius)
        assertEquals("Ring", back.name)
    }

    @Test
    fun notesAboutHiddenShapesThatAreGoneArePrunedAway() {
        val here = parse("""{"type":"despawn_sphere","enabled":true,"center":[1.5,2,3.5],"radius":32.0}""")!!
        val gone = parse("""{"type":"despawn_sphere","enabled":true,"center":[9.5,2,9.5],"radius":32.0}""")!!
        ShapeStore.setVisible(here, false)
        ShapeStore.setVisible(gone, false)
        try {
            assertFalse(here.visible)
            assertFalse(gone.visible)
            ShapeStore.pruneHidden(setOf(here.id))
            assertFalse(here.visible) // still hidden
            assertTrue(gone.visible)  // its note was dropped
        } finally {
            ShapeStore.setVisible(here, true)
            ShapeStore.setVisible(gone, true)
        }
    }

    @Test
    fun theSameShapeKeepsItsIdAcrossReads() {
        val json = """{"type":"despawn_sphere","enabled":true,"display_name":"A","center":[1.5,2,3.5],"radius":128.0}"""
        assertEquals(parse(json)!!.id, parse(json.replace("\"A\"", "\"Renamed\""))!!.id)
    }
}
