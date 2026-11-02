package com.skystormer.skysmapshapes

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
        assertNull(ShapeShare.readLine("<Steve> Map shape Evil: circle rNaN at 0 0 (Overworld)"))
        assertNull(ShapeShare.readLine("<Steve> Map shape Evil: circle r1e999 at 0 0 (Overworld)"))
        assertNull(ShapeShare.readLine("<Steve> Map shape Evil: circle r5 at 0 0 (x:../../../mods/evil)"))
    }

    private val start = "<Steve> Map shape Farm: circle r40 at 0 0 (Overworld) · red · "

    /** Nothing from chat reaches MiniHUD unchecked: unknown kinds and odd values are dropped. */
    @Test
    fun onlyKnownMiniHudKindsAndValuesComeFromChat() {
        val unknown = ShapeShare.readLine(start + "MiniHUD rm_rf y 64 radius=40")!!.second
        assertNull(unknown.miniHudType)
        assertNull(unknown.miniHudSizes)
        assertEquals(40.0, unknown.shape.radius) // still offered as a map shape

        for (bad in listOf("main_axis=sideways", "radius=NaN", "radius=1e99", "snap=everywhere", "center=1e99,0,0", "height=-5", "colour=red")) {
            val got = ShapeShare.readLine(start + "MiniHUD circle y 64 $bad")!!.second
            assertEquals("circle", got.miniHudType, bad)
            val key = bad.substringBefore('=')
            assertFalse(got.miniHudSizes?.has(key) ?: false, bad)
        }
        val good = ShapeShare.readLine(start + "MiniHUD circle y 64 main_axis=north direction=up snap=center radius=40")!!.second
        assertEquals(setOf("main_axis", "direction", "snap", "radius"), good.miniHudSizes!!.keySet())
    }

    /** The add button's code gets the same checks, since a server can send a click that runs it directly. */
    @Test
    fun theAddCodeIsCheckedTheSameWay() {
        val sizes = JsonObject().apply { addProperty("radius", 1e99); addProperty("main_axis", "sideways"); addProperty("height", 32) }
        val sent = ShapeShare.Shared(circle(), "circle", 64, sizes)
        val got = ShapeShare.decode(ShapeShare.encode(sent))!!
        assertEquals(setOf("height"), got.miniHudSizes!!.keySet())
        val unknown = ShapeShare.decode(ShapeShare.encode(ShapeShare.Shared(circle(), "rm_rf", 64, sizes)))!!
        assertNull(unknown.miniHudType)
        assertNull(unknown.miniHudSizes)
    }

    /** Shared shapes are no bigger than the add screens take, so one that opens can be saved. */
    @Test
    fun sharedShapesAreNoBiggerThanTypedOnes() {
        assertNull(ShapeShare.readLine("Map shape Big: circle r2000000 at 0 0 (Overworld)"))
        assertNotNull(ShapeShare.readLine("Map shape Big: circle r1000000 at 0 0 (Overworld)"))
        assertNull(ShapeShare.readLine("Map shape Big: rectangle 2000000x5 at 0 0 (Overworld)"))
        assertNull(ShapeShare.decode(ShapeShare.encode(ShapeShare.Shared(circle(radius = 2_000_000.0), null, null))))
    }

    @Test
    fun presetsOutsideWhatCanBeTypedAreNot() {
        assertTrue(Config.validPreset("Instant despawn", 128.0))
        assertFalse(Config.validPreset("x", -1.0))
        assertFalse(Config.validPreset("x", Double.NaN))
        assertFalse(Config.validPreset("x", 2_000_000.0))
        assertFalse(Config.validPreset("x".repeat(33), 10.0))
    }
}
