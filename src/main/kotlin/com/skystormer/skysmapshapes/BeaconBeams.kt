package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3

/**
 * Experimental: a beam drawn straight up from every beacon in the chunks loaded around you (up to
 * [Config.beaconLimit], nearest first), lit or not ([Config.beaconBeams]), and the blocks in its way
 * outlined in red ([Config.beaconOutlines]), each switched on its own. The outlines show through
 * walls if [Config.beaconThroughWalls], so you know what to break to light it. Only what is between
 * [Config.beaconMinY] and [Config.beaconMaxY] is drawn.
 *
 * A block is in the way the way the game decides it: anything that blocks light completely (a
 * full solid cube, or tinted glass), except bedrock. Glass, leaves, slabs, anvils, chains, honey
 * and anything else that does not fill its block let the beam through.
 */
object BeaconBeams {

    /**
     * One beacon's column: where it stands, how high the beam goes, and the runs of blocks in its
     * way, each as its lowest and highest y. A run is outlined as one box: a beam under a hundred
     * blocks of netherrack is one box, not a hundred, which is what lets a thousand beacons show.
     */
    private class Beam(val pos: BlockPos, val top: Int, val blocked: List<IntRange>)

    @Volatile
    private var beams: List<Beam> = emptyList()

    private var ticks = 0
    private var broken = false

    /** Every second, finds the beacons around you and what blocks each one. Main thread. */
    fun tick() {
        if (broken) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = minecraft.player
        if (!Config.enabled || !(Config.beaconBeams || Config.beaconOutlines) || level == null || player == null) {
            beams = emptyList()
            return
        }
        if (ticks++ % REFRESH_TICKS != 0) return
        try {
            val middleX = player.blockPosition().x shr 4
            val middleZ = player.blockPosition().z shr 4
            // Every chunk the game has loaded around you.
            val chunks = minecraft.options.effectiveRenderDistance.coerceIn(2, MAX_CHUNKS)
            val found = ArrayList<Beam>()
            for (x in middleX - chunks..middleX + chunks) {
                for (z in middleZ - chunks..middleZ + chunks) {
                    val chunk = level.chunkSource.getChunk(x, z, false) ?: continue
                    for (pos in beaconsIn(chunk)) found.add(beamOf(level, pos))
                }
            }
            // Nearest first, so a huge beacon wall still shows the ones around you.
            val here = player.blockPosition()
            beams = found.sortedBy { it.pos.distSqr(here) }.take(Config.beaconLimit)
        } catch (e: Throwable) {
            broken = true
            beams = emptyList()
            Log.error("Could not find the beacons around you; beacon beams are off until the game restarts", e)
        }
    }

    /**
     * Every beacon block in a chunk. Looked for by block rather than by block entity, which a server
     * need not send with the chunk; each section's palette rules out almost all of them at once.
     */
    private fun beaconsIn(chunk: LevelChunk): List<BlockPos> {
        var found: ArrayList<BlockPos>? = null
        val sections = chunk.sections
        for (index in sections.indices) {
            val section = sections[index]
            if (section.hasOnlyAir() || !section.maybeHas { it.`is`(Blocks.BEACON) }) continue
            val baseY = chunk.getSectionYFromSectionIndex(index) shl 4
            for (y in 0 until 16) for (z in 0 until 16) for (x in 0 until 16) {
                if (section.getBlockState(x, y, z).`is`(Blocks.BEACON)) {
                    (found ?: ArrayList<BlockPos>().also { found = it })
                        .add(BlockPos(chunk.pos.minBlockX + x, baseY + y, chunk.pos.minBlockZ + z))
                }
            }
        }
        return found ?: emptyList()
    }

    private fun beamOf(level: Level, beacon: BlockPos): Beam {
        val top = level.maxY
        val runs = ArrayList<IntRange>()
        val cursor = BlockPos.MutableBlockPos()
        var start = Int.MIN_VALUE
        for (y in beacon.y + 1..top + 1) {
            val blocks = y <= top && blocksBeam(level.getBlockState(cursor.set(beacon.x, y, beacon.z)))
            if (blocks && start == Int.MIN_VALUE) start = y
            if (!blocks && start != Int.MIN_VALUE) {
                runs.add(start until y)
                start = Int.MIN_VALUE
            }
        }
        return Beam(beacon, top, runs)
    }

    /** What stops a beacon's beam in the game: a block no light gets through, other than bedrock. */
    private fun blocksBeam(state: BlockState): Boolean = state.lightDampening >= 15 && !state.`is`(Blocks.BEDROCK)

    /** Draws the beams and outlines; called as each frame is gathered. */
    fun draw() {
        if (broken) return
        val all = beams
        if (all.isEmpty()) return
        try {
            val beam = GizmoStyle.fill(BEAM_COLOUR)
            val blocked = GizmoStyle.fill(BLOCKER_FILL)
            val onTop = Config.beaconThroughWalls
            val low = Config.beaconMinY
            val high = Config.beaconMaxY
            for (b in all) {
                val x = b.pos.x + 0.5
                val z = b.pos.z + 0.5
                // Only the part of the beam inside the heights chosen.
                val from = maxOf(b.pos.y + 1, low)
                val to = minOf(b.top, high)
                if (Config.beaconBeams && from <= to) column(x - BEAM_HALF, from.toDouble(), z - BEAM_HALF, x + BEAM_HALF, to + 1.0, z + BEAM_HALF, beam)
                if (Config.beaconOutlines) for (run in b.blocked) {
                    val y1 = maxOf(run.first, low)
                    val y2 = minOf(run.last, high)
                    if (y1 > y2) continue
                    val x1 = b.pos.x - GROW
                    val z1 = b.pos.z - GROW
                    val x2 = b.pos.x + 1 + GROW
                    val z2 = b.pos.z + 1 + GROW
                    column(x1, y1 - GROW, z1, x2, y2 + 1 + GROW, z2, blocked)
                    outline(x1, y1 - GROW, z1, x2, y2 + 1 + GROW, z2, onTop)
                }
            }
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not draw beacon beams; they are off until the game restarts", e)
        }
    }

    /** The four sides of a box, filled. */
    private fun column(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, style: GizmoStyle) {
        Gizmos.rect(Vec3(x1, y1, z1), Vec3(x2, y1, z1), Vec3(x2, y2, z1), Vec3(x1, y2, z1), style)
        Gizmos.rect(Vec3(x2, y1, z1), Vec3(x2, y1, z2), Vec3(x2, y2, z2), Vec3(x2, y2, z1), style)
        Gizmos.rect(Vec3(x2, y1, z2), Vec3(x1, y1, z2), Vec3(x1, y2, z2), Vec3(x2, y2, z2), style)
        Gizmos.rect(Vec3(x1, y1, z2), Vec3(x1, y1, z1), Vec3(x1, y2, z1), Vec3(x1, y2, z2), style)
    }

    /** The twelve edges of a box, seen through anything in front of them when [onTop]. */
    private fun outline(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, onTop: Boolean) {
        for (y in doubleArrayOf(y1, y2)) {
            edge(x1, y, z1, x2, y, z1, onTop); edge(x2, y, z1, x2, y, z2, onTop); edge(x2, y, z2, x1, y, z2, onTop); edge(x1, y, z2, x1, y, z1, onTop)
        }
        edge(x1, y1, z1, x1, y2, z1, onTop); edge(x2, y1, z1, x2, y2, z1, onTop); edge(x2, y1, z2, x2, y2, z2, onTop); edge(x1, y1, z2, x1, y2, z2, onTop)
    }

    private fun edge(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, onTop: Boolean) {
        val line = Gizmos.line(Vec3(x1, y1, z1), Vec3(x2, y2, z2), BLOCKER_OUTLINE, OUTLINE_WIDTH)
        if (onTop) line.setAlwaysOnTop()
    }

    /** The farthest out beacons are looked for, in chunks, whatever the render distance. */
    private const val MAX_CHUNKS = 32
    private const val REFRESH_TICKS = 20

    private const val BEAM_HALF = 0.2
    private const val GROW = 0.005
    private const val BEAM_COLOUR = 0x60FFFFFF
    private const val BLOCKER_FILL = 0x50FF3030
    private const val BLOCKER_OUTLINE = 0xFFFF3030.toInt()
    private const val OUTLINE_WIDTH = 2.5f
}
