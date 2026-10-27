package com.skystormer.skysmapshapes

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotSectionsTest {

    private fun spots(vararg xyz: Int): LongArray =
        LongArray(xyz.size / 3) { BlockPos.asLong(xyz[it * 3], xyz[it * 3 + 1], xyz[it * 3 + 2]) }

    @Test
    fun spotsInOneSectionMakeOneKey() {
        val sections = SpotSections.of(spots(0, 64, 0, 15, 79, 15, 7, 70, 3))
        assertEquals(1, sections.size)
    }

    @Test
    fun aBoxFindsTheSpotsSectionAndNoOther() {
        val sections = SpotSections.of(spots(100, 64, -200))
        // The whole chunk column the spot is in, as Litematica asks about it.
        assertTrue(SpotSections.anyIn(sections, 96, -64, -208, 111, 319, -193))
        // The next chunk along, and the same column cut off above the spot's section.
        assertFalse(SpotSections.anyIn(sections, 112, -64, -208, 127, 319, -193))
        assertFalse(SpotSections.anyIn(sections, 96, 80, -208, 111, 319, -193))
    }

    @Test
    fun negativeAndFarCoordinatesKeepApart() {
        val sections = SpotSections.of(spots(-1, -1, -1, 29_999_999, 300, -29_999_999))
        assertEquals(2, sections.size)
        assertTrue(SpotSections.anyIn(sections, -16, -16, -16, -1, -1, -1))
        assertFalse(SpotSections.anyIn(sections, 0, 0, 0, 15, 15, 15))
        assertTrue(SpotSections.anyIn(sections, 29_999_990, 300, -30_000_000, 30_000_000, 300, -29_999_990))
    }

    @Test
    fun aBigBoxGoesThroughTheSectionsInstead() {
        val sections = SpotSections.of(spots(5_000, 10, 5_000))
        // Far more sections in the box than spots: answered from the spots' sections, the same way.
        assertTrue(SpotSections.anyIn(sections, -10_000, -64, -10_000, 10_000, 319, 10_000))
        assertFalse(SpotSections.anyIn(sections, -10_000, -64, -10_000, 4_000, 319, 10_000))
    }

    @Test
    fun noSpotsOrAnEmptyBoxFindNothing() {
        assertFalse(SpotSections.anyIn(SpotSections.of(LongArray(0)), 0, 0, 0, 15, 15, 15))
        assertFalse(SpotSections.anyIn(SpotSections.of(spots(0, 0, 0)), 15, 15, 15, 0, 0, 0))
    }
}
