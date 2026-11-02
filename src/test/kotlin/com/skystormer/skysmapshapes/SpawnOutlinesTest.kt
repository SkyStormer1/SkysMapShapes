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

    /** Every spot is in exactly one rectangle, and no rectangle covers anything else. */
    private fun assertCoversExactly(spots: LongArray, rects: List<SpawnOutlines.Rect>) {
        val covered = HashMap<Long, Int>()
        for (r in rects) for (x in r.x1 until r.x2) for (z in r.z1 until r.z2) {
            val key = net.minecraft.core.BlockPos.asLong(x, r.y, z)
            covered[key] = (covered[key] ?: 0) + 1
        }
        assertEquals(spots.toSet(), covered.keys)
        assertEquals(setOf(1), covered.values.toSet())
    }

    @Test
    fun `a filled square is one rectangle`() {
        val square = spots(*(0 until 10).flatMap { x -> (0 until 10).map { z -> x to z } }.toTypedArray())
        val rects = SpawnOutlines.rectangles(square)
        assertEquals(listOf(SpawnOutlines.Rect(64, 0, 0, 10, 10)), rects)
    }

    @Test
    fun `odd shapes are covered exactly once`() {
        val ring = spots(*(0 until 5).flatMap { x -> (0 until 5).map { z -> x to z } }.filter { it != 2 to 2 }.toTypedArray())
        assertCoversExactly(ring, SpawnOutlines.rectangles(ring))
        val l = spots(0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2, 5 to 5)
        assertCoversExactly(l, SpawnOutlines.rectangles(l))
        val gap = spots(0 to 0, 1 to 0, 0 to 2, 1 to 2)
        val rects = SpawnOutlines.rectangles(gap)
        assertCoversExactly(gap, rects)
        assertEquals(2, rects.size)
        val layers = spots(0 to 0, 1 to 0) + spots(0 to 0, 1 to 0, y = 65)
        assertCoversExactly(layers, SpawnOutlines.rectangles(layers))
    }

    @Test
    fun `random patches are covered exactly once`() {
        val random = java.util.Random(7)
        repeat(50) {
            val set = (0 until 120).map { random.nextInt(12) to random.nextInt(12) }.distinct().toTypedArray()
            val s = spots(*set)
            assertCoversExactly(s, SpawnOutlines.rectangles(s))
        }
    }
}
