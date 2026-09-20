package com.skystormer.skysmapshapes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Sharing a shape through a line of chat. */
class ShareTest {

    private fun shape(label: String = "AFK spot") = Shape(
        label = label, dimension = "minecraft:the_nether", type = Shape.Type.CIRCLE,
        x = 250, z = -96, radius = 128.0, colour = Colours.ORANGE.argb, fill = true, lineWidth = 3f,
    )

    @Test
    fun aSharedShapeComesBackTheSame() {
        val sent = shape()
        val got = ShapeShare.decode(ShapeShare.encode(sent))!!.shape
        assertEquals(sent.label, got.label)
        assertEquals(sent.dimension, got.dimension) // the sender's dimension, not the reader's
        assertEquals(sent.type, got.type)
        assertEquals(sent.x, got.x)
        assertEquals(sent.z, got.z)
        assertEquals(sent.radius, got.radius)
        assertEquals(sent.colour, got.colour)
        assertEquals(sent.fill, got.fill)
        assertEquals(sent.lineWidth, got.lineWidth)
    }

    @Test
    fun everyKindOfShapeSurvivesSharing() {
        for (type in Shape.Type.entries) {
            val sent = Shape(
                label = type.title, dimension = "minecraft:overworld", type = type, x = 7, z = -9,
                radius = 24.0, width = 18.0, length = 6.0,
                anchor = Shape.Anchor.CORNER, colour = Colours.AQUA.argb,
            )
            val got = ShapeShare.decode(ShapeShare.encode(sent))!!.shape
            assertEquals(sent.type, got.type)
            assertEquals(sent.bounds.minX, got.bounds.minX, 1e-9)
            assertEquals(sent.bounds.maxZ, got.bounds.maxZ, 1e-9)
        }
    }

    @Test
    fun theChatLineIsReadableAndFitsInOneMessage() {
        val line = ShapeShare.message(shape())
        assertTrue(line.startsWith("Map shape: AFK spot"))
        assertTrue(line.contains("Nether"))
        assertTrue(line.length <= 256, "chat line was ${line.length} characters")
        assertEquals(shape().radius, ShapeShare.decode(ShapeShare.codeIn(line)!!)!!.shape.radius)
    }

    @Test
    fun aSharedMiniHudShapeCarriesItsKindAndHeight() {
        val fromMiniHud = MiniHudShapes.parse(
            com.google.gson.JsonParser.parseString(
                """{"type":"despawn_sphere","enabled":true,"display_name":"Gold farm","center":[64.5,42.0,-128.5],"radius":128.0}"""
            ).asJsonObject,
            "minecraft:the_nether",
        )!!
        val line = ShapeShare.message(fromMiniHud)
        assertTrue(line.length <= 256, "chat line was ${line.length} characters")
        val got = ShapeShare.decode(ShapeShare.codeIn(line)!!)!!
        // Someone with MiniHUD can have the same kind of shape, at the same height, in their world.
        assertEquals("despawn_sphere", got.miniHudType)
        assertEquals(42, got.y)
        // Someone without it still gets the outline, in the sender's dimension.
        assertEquals(Shape.Type.CIRCLE, got.shape.type)
        assertEquals(128.0, got.shape.radius)
        assertEquals("minecraft:the_nether", got.shape.dimension)
        assertEquals(64, got.shape.x)
        assertEquals(-129, got.shape.z)
        assertEquals("Gold farm", got.shape.label)
    }

    @Test
    fun ordinaryChatIsLeftAlone() {
        assertNull(ShapeShare.codeIn("hey, look at the shape by the gold farm"))
        assertTrue(ShapeShare.onChat("hey, look at the shape by the gold farm"))
        assertNull(ShapeShare.decode("not-a-code"))
    }
}
