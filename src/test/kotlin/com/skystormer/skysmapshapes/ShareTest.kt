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
    fun aMiniHudShapeCarriesItsOwnSizes() {
        // A cylinder 32 high and a cone pointing down: neither fits in a flat outline, and both
        // came out wrong on the other end before (256 high; a box from bedrock to the sky).
        val cases = listOf(
            "circle" to """{"type":"circle","center":[10.5,70.0,-19.5],"radius":40.0,"height":32,"main_axis":"UP"}""",
            "cone" to """{"type":"cone","origin_x":10,"origin_y":60,"origin_z":-19,"bottom_radius":40,"top_radius":0,"height":32,"direction":"down"}""",
            "centered_box" to """{"type":"centered_box","center":[10.5,70.0,-19.5],"width":16.0,"height":32.0,"depth":16.0,"corner1":[2.5,54.0,-27.5],"corner2":[18.5,86.0,-11.5]}""",
            "despawn_sphere" to """{"type":"despawn_sphere","center":[10.5,70.0,-19.5],"radius":40.0,"margin":1.5,"snap":"center"}""",
        )
        for ((typeId, text) in cases) {
            val json = JsonParser.parseString(text).asJsonObject
            val sent = MiniHudShapes.parse(json, "minecraft:overworld")!!
            val got = readBack(sent)
            assertEquals(typeId, got.miniHudType)
            assertEquals(MiniHudGeometry.sizes(json), got.miniHudSizes, typeId)
            // And through the add button's code.
            assertEquals(MiniHudGeometry.sizes(json), ShapeShare.decode(ShapeShare.encode(got))!!.miniHudSizes, typeId)
            // Made again from them, MiniHUD is asked for the same sizes as the shape shared.
            val made = MiniHudGeometry.forMiniHud(got.shape, got.y!!, got.miniHudType, null, got.miniHudSizes)!!
            assertEquals(typeId, made.get("type").asString)
            assertEquals(MiniHudGeometry.sizes(json), MiniHudGeometry.sizes(made), typeId)
        }
    }

    @Test
    fun aLineFromBeforeSizesWereSharedIsStillRead() {
        val line = "Map shape Circle: circle r128 at -321 179 (Overworld) · red · MiniHUD sphere_blocky y 8"
        val got = ShapeShare.readLine(line)!!.second
        assertEquals("sphere_blocky", got.miniHudType)
        assertEquals(8, got.y)
        assertNull(got.miniHudSizes)
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
        assertTrue(line.endsWith("· MiniHUD despawn_sphere y 42 center=64.5,42.0,-128.5 radius=128.0"), line)
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

    /**
     * The message shown in place of a shared line reads as a shared shape itself, so the chat
     * hook has to stop there rather than replacing its own message for ever (it crashed the game).
     */
    @Test
    fun theMessageShownInsteadStillReadsAsTheSameShape() {
        val (shown, _) = ShapeShare.readLine("<Steve> " + ShapeShare.message(shape()))!!
        val again = ShapeShare.readLine(shown)
        assertNotNull(again, "the tidy message parses too, so onChat must not act on its own output")
        assertEquals(shape().radius, again!!.second.shape.radius)
    }

    /** Chat carries anything at all, and reading it must never take a noticeable amount of time. */
    @Test
    fun aLongLineThatIsNotOneOfOursIsTurnedDownQuickly() {
        val awkward = "Map shape " + "a: circle ".repeat(40) + " and then some words"
        val started = System.nanoTime()
        ShapeShare.readLine(awkward)
        ShapeShare.readLine("Map shape " + "x".repeat(400))
        ShapeShare.readLine("Map shape : circle r at at at (((")
        val tookMillis = (System.nanoTime() - started) / 1_000_000
        assertTrue(tookMillis < 100, "reading three awkward lines took $tookMillis ms")
    }

    @Test
    fun ordinaryChatIsLeftAlone() {
        assertNull(ShapeShare.readLine("hey, look at the map shape by the gold farm"))
        assertTrue(ShapeShare.onChat("hey, look at the map shape by the gold farm"))
        assertNull(ShapeShare.decode("not-a-code"))
    }

    /**
     * A shared line's MiniHUD sizes are never shown, so a small circle could ask MiniHUD for a
     * sphere big enough to crash the game of whoever adds it. The outline alone looks fine; the
     * shape MiniHUD would really be asked for is what gets refused.
     */
    @Test
    fun hiddenMiniHudSizesBiggerThanTheOutlineAreRefused() {
        val line = "<Steve> Map shape Trap: circle r5 at 0 0 (Overworld) · red · MiniHUD sphere_blocky y 64 radius=4000 max_radius=4002"
        val got = ShapeShare.readLine(line)!!.second
        assertNull(MiniHudGeometry.whyTooBig(got.shape))
        val json = MiniHudGeometry.forMiniHud(got.shape, got.y!!, got.miniHudType, null, got.miniHudSizes)!!
        assertNotNull(MiniHudGeometry.whyTooBig(json))
        // The same through the add button's code.
        val back = ShapeShare.decode(ShapeShare.encode(got))!!
        assertNotNull(MiniHudGeometry.whyTooBig(MiniHudGeometry.forMiniHud(back.shape, back.y!!, back.miniHudType, null, back.miniHudSizes)!!))
    }

    @Test
    fun sharedSizesWithinMiniHudsLimitsStillGoThrough() {
        val line = "Map shape Farm: circle r40 at 0 0 (Overworld) · red · MiniHUD despawn_sphere y 64 center=0.5,64,0.5 radius=40"
        val got = ShapeShare.readLine(line)!!.second
        val json = MiniHudGeometry.forMiniHud(got.shape, got.y!!, got.miniHudType, null, got.miniHudSizes)!!
        assertNull(MiniHudGeometry.whyTooBig(json))
        assertNull(MiniHudGeometry.lagWarning(json))
        // Big but legal: warned about, not refused.
        val big = JsonParser.parseString("""{"type":"sphere_blocky","center":[0,64,0],"radius":250,"max_radius":251}""").asJsonObject
        assertNull(MiniHudGeometry.whyTooBig(big))
        assertNotNull(MiniHudGeometry.lagWarning(big))
    }

    /** The finished JSON is counted the same way as the outline it was made from, so both checks agree. */
    @Test
    fun blocksCountedFromTheJsonMatchTheOutline() {
        for (type in Shape.Type.entries) for (form in MiniHudGeometry.forms(type)) {
            val shape = Shape(label = "", dimension = "minecraft:overworld", type = type, x = 0, z = 0,
                radius = 100.0, width = 80.0, length = 60.0, colour = Colours.RED.argb)
            val fromShape = MiniHudGeometry.blocks(shape, form)
            val fromJson = MiniHudGeometry.blocksOf(MiniHudGeometry.toMiniHudJson(shape, 64, form)!!)
            if (fromShape == null) assertNull(fromJson, "$type $form")
            else assertEquals(fromShape, fromJson!!, fromShape * 0.01, "$type $form")
        }
    }

    /** A server can list a "player" called @a; a private share to it would go to everyone. */
    @Test
    fun privateSharesOnlyGoToRealPlayerNames() {
        ShapeShare.clear()
        try {
            assertEquals(0, ShapeShare.queue("line", listOf("@a", "a b", "x;y", "abcdefghijklmnopq", "")))
            assertEquals(0, ShapeShare.waitingCount)
            assertEquals(2, ShapeShare.queue("line", listOf("Steve", "Sky_Stormer", "@p")))
            assertEquals(2, ShapeShare.waitingCount)
        } finally {
            ShapeShare.clear()
        }
    }
}
