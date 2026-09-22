package com.skystormer.skysmapshapes

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Sharing a shape through a line of chat everyone can read. */
class ShareTest {

    private fun shape(label: String = "AFK spot") = Shape(
        label = label, dimension = "minecraft:the_nether", type = Shape.Type.CIRCLE,
        x = 250, z = -96, radius = 128.0, colour = Colours.ORANGE.argb, fill = true, lineWidth = 3f,
    )

    private fun readBack(sent: MapShape): ShapeShare.Shared = ShapeShare.readLine(ShapeShare.message(sent))!!.second

    @Test
    fun theLineIsPlainWords() {
        val line = ShapeShare.message(shape())
        assertEquals("Map shape AFK spot: circle r128 at 250 -96 (Nether) · orange · filled · 3px", line)
        // Nothing in it a reader cannot make sense of.
        assertFalse(line.contains("SMS1:"))
        assertTrue(line.length <= 256)
    }

    @Test
    fun aSharedShapeComesBackTheSame() {
        val sent = shape()
        val got = readBack(sent).shape
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
                radius = 24.0, width = 18.5, length = 6.0,
                anchor = Shape.Anchor.CORNER, colour = Colours.AQUA.argb,
            )
            val got = readBack(sent).shape
            assertEquals(sent.type, got.type)
            assertEquals(sent.bounds.minX, got.bounds.minX, 1e-9)
            assertEquals(sent.bounds.maxZ, got.bounds.maxZ, 1e-9)
            assertEquals("", got.label) // a shape named only for its kind stays unnamed
        }
    }

    @Test
    fun aColourSetByHandIsSentAsItsCode() {
        val sent = shape().copy(colour = Colours.parse("#123456")!!)
        assertTrue(ShapeShare.message(sent).contains("· #123456"))
        assertEquals(sent.colour, readBack(sent).shape.colour)
    }

    @Test
    fun whoSaidItIsKeptAndTheExtrasAreNotShown() {
        val (shown, shared) = ShapeShare.readLine("<Steve> " + ShapeShare.message(shape()))!!
        assertEquals("<Steve> Map shape AFK spot: circle r128 at 250 -96 (Nether)", shown)
        assertEquals(128.0, shared.shape.radius)
    }

    @Test
    fun aPrivateMessageIsReadToo() {
        val (_, shared) = ShapeShare.readLine("Steve whispers to you: " + ShapeShare.message(shape()))!!
        assertEquals("AFK spot", shared.shape.label)
    }

    @Test
    fun aSharedMiniHudShapeCarriesItsKindAndHeight() {
        val fromMiniHud = MiniHudShapes.parse(
            JsonParser.parseString(
                """{"type":"despawn_sphere","enabled":true,"display_name":"Gold farm","center":[64.5,42.0,-128.5],"radius":128.0}"""
            ).asJsonObject,
            "minecraft:the_nether",
        )!!
        val line = ShapeShare.message(fromMiniHud)
        assertTrue(line.endsWith("· MiniHUD despawn_sphere y 42"), line)
        val got = ShapeShare.readLine(line)!!.second
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
    fun theAddButtonsCodeRoundTrips() {
        val shared = ShapeShare.Shared(shape(), "despawn_sphere", 70)
        val back = ShapeShare.decode(ShapeShare.encode(shared))!!
        assertEquals("despawn_sphere", back.miniHudType)
        assertEquals(70, back.y)
        assertEquals(shape().radius, back.shape.radius)
    }

    @Test
    fun linesFromOlderVersionsAreStillUnderstood() {
        val code = ShapeShare.encode(ShapeShare.Shared(shape(), null, null))
        val old = "<Steve> Map shape: AFK spot · Circle, radius 128 · Nether · SMS1:$code"
        assertNull(ShapeShare.readLine(old))
        assertNotNull(ShapeShare.decode(ShapeShare.codeIn(old)!!))
    }

    @Test
    fun ordinaryChatIsLeftAlone() {
        assertNull(ShapeShare.readLine("hey, look at the map shape by the gold farm"))
        assertNull(ShapeShare.codeIn("hey, look at the map shape by the gold farm"))
        assertTrue(ShapeShare.onChat("hey, look at the map shape by the gold farm"))
        assertNull(ShapeShare.decode("not-a-code"))
    }
}
