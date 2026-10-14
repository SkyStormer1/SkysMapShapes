package com.skystormer.skysmapshapes

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpawnOutlinesTest {

    private fun spots(vararg xz: Pair<Int, Int>, y: Int = 64) = xz.map { BlockPos.asLong(it.first, y, it.second) }.toLongArray()

    @Test
    fun `one spot is four edges`() {
        assertEquals(4, SpawnOutlines.edges(spots(0 to 0)).size)
    }

    @Test
    fun `a filled square is four lines however big`() {
        val square = (0 until 10).flatMap { x -> (0 until 10).map { z -> x to z } }.toTypedArray()
        val edges = SpawnOutlines.edges(spots(*square))
        assertEquals(
            setOf(
                SpawnOutlines.Edge(64, 0, 0, 10, 0), SpawnOutlines.Edge(64, 0, 10, 10, 10),
                SpawnOutlines.Edge(64, 0, 0, 0, 10), SpawnOutlines.Edge(64, 10, 0, 10, 10),
            ),
            edges.toSet(),
        )
    }

    @Test
    fun `a hole gets its own outline`() {
        val ring = (0 until 3).flatMap { x -> (0 until 3).map { z -> x to z } }.filter { it != 1 to 1 }.toTypedArray()
        assertEquals(8, SpawnOutlines.edges(spots(*ring)).size)
    }

    @Test
    fun `patches at different heights are kept apart`() {
        val edges = SpawnOutlines.edges(spots(0 to 0) + spots(1 to 0, y = 65))
        assertEquals(8, edges.size)
    }

    @Test
    fun `an L shape is six lines`() {
        assertEquals(6, SpawnOutlines.edges(spots(0 to 0, 1 to 0, 0 to 1)).size)
    }
}
