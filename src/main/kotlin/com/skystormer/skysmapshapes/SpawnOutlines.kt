package com.skystormer.skysmapshapes

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos

/**
 * One outline around each patch of touching spawn proofing spots, instead of a box on every
 * spot: only the edges of a spot that no other spot at the same height shares, with edges in a
 * straight line joined into one. A 10 by 10 patch is four lines, not a hundred boxes.
 */
object SpawnOutlines {

    /** A straight edge on the floor of layer [y], from corner ([x1], [z1]) to ([x2], [z2]). */
    data class Edge(val y: Int, val x1: Int, val z1: Int, val x2: Int, val z2: Int)

    /** A rectangle of spots on the floor of layer [y], from corner ([x1], [z1]) to ([x2], [z2]), the far corner exclusive. */
    data class Rect(val y: Int, val x1: Int, val z1: Int, val x2: Int, val z2: Int)

    /**
     * [spots] (packed block positions) covered by as few rectangles as a simple sweep finds: each
     * row of touching spots along x becomes a strip, and a strip exactly under the one in the row
     * before grows that one instead. A 10 by 10 patch is one rectangle, not a hundred.
     */
    fun rectangles(spots: LongArray): List<Rect> {
        // Strips along x, by layer and row: (y, z) to the sorted x of each spot.
        val rows = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (spot in spots) rows.getOrPut(BlockPos.getY(spot) to BlockPos.getZ(spot)) { ArrayList() } += BlockPos.getX(spot)
        val out = ArrayList<Rect>()
        // Open rectangles by (y, x1, x2), each with the row it has reached so far.
        val open = HashMap<Triple<Int, Int, Int>, Rect>()
        for ((key, xs) in rows.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))) {
            val (y, z) = key
            runs(xs) { from, to ->
                val id = Triple(y, from, to)
                val above = open[id]
                open[id] = if (above != null && above.z2 == z) above.copy(z2 = z + 1) else {
                    above?.let(out::add)
                    Rect(y, from, z, to, z + 1)
                }
            }
        }
        out += open.values
        return out
    }

    /** The outer edges of [spots] (packed block positions), joined into the fewest straight lines. */
    fun edges(spots: LongArray): List<Edge> {
        val set = LongOpenHashSet(spots)
        // Edges running along x, keyed by (y, the z of the line); and along z, by (y, the x of the line).
        val alongX = HashMap<Pair<Int, Int>, MutableList<Int>>()
        val alongZ = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (spot in spots) {
            val x = BlockPos.getX(spot)
            val y = BlockPos.getY(spot)
            val z = BlockPos.getZ(spot)
            if (!set.contains(BlockPos.asLong(x, y, z - 1))) alongX.getOrPut(y to z) { ArrayList() } += x
            if (!set.contains(BlockPos.asLong(x, y, z + 1))) alongX.getOrPut(y to z + 1) { ArrayList() } += x
            if (!set.contains(BlockPos.asLong(x - 1, y, z))) alongZ.getOrPut(y to x) { ArrayList() } += z
            if (!set.contains(BlockPos.asLong(x + 1, y, z))) alongZ.getOrPut(y to x + 1) { ArrayList() } += z
        }
        val out = ArrayList<Edge>()
        for ((key, starts) in alongX) runs(starts) { from, to -> out += Edge(key.first, from, key.second, to, key.second) }
        for ((key, starts) in alongZ) runs(starts) { from, to -> out += Edge(key.first, key.second, from, key.second, to) }
        return out
    }

    /**
     * Joins one-block pieces starting at [starts] into runs: [run] gets each run's first and
     * last corner. A piece shared by two spots (a north edge of one, a south edge of the other)
     * cannot be here, as it is not an outer edge, so a duplicate only comes from a single spot.
     */
    private inline fun runs(starts: MutableList<Int>, run: (Int, Int) -> Unit) {
        starts.sort()
        var from = starts[0]
        var to = from + 1
        for (i in 1 until starts.size) {
            val next = starts[i]
            if (next < to) continue
            if (next == to) {
                to++
                continue
            }
            run(from, to)
            from = next
            to = next + 1
        }
        run(from, to)
    }
}
