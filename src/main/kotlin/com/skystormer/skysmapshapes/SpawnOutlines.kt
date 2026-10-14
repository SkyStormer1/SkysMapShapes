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
