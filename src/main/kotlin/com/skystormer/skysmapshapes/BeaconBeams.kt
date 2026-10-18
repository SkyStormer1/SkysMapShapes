package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BeaconBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

/**
 * Experimental ([Config.beaconBeams]): a beam drawn straight up from every beacon near you, whether
 * it is lit or not, with every block in its way outlined in red through walls, so you know what to
 * break to light it.
 *
 * A block is in the way the way the game decides it: anything that blocks light completely, except
 * bedrock. Glass, stained glass, leaves, slabs and the like let the beam through.
 */
object BeaconBeams {

    /** One beacon's column: where it stands, how high the beam goes, and what stands in it. */
    private class Beam(val pos: BlockPos, val top: Int, val blockers: List<BlockPos>)

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
        if (!Config.enabled || !Config.beaconBeams || level == null || player == null) {
            beams = emptyList()
            return
        }
        if (ticks++ % REFRESH_TICKS != 0) return
        try {
            val middleX = player.blockPosition().x shr 4
            val middleZ = player.blockPosition().z shr 4
            val found = ArrayList<Beam>()
            for (x in middleX - CHUNKS..middleX + CHUNKS) {
                for (z in middleZ - CHUNKS..middleZ + CHUNKS) {
                    val chunk = level.chunkSource.getChunk(x, z, false) ?: continue
                    for (entity in chunk.blockEntities.values) {
                        if (entity is BeaconBlockEntity) found.add(beamOf(level, entity.blockPos))
                        if (found.size >= MAX_BEACONS) break
                    }
                }
            }
            beams = found
        } catch (e: Throwable) {
            broken = true
            beams = emptyList()
            Log.error("Could not find the beacons around you; beacon beams are off until the game restarts", e)
        }
    }

    private fun beamOf(level: Level, beacon: BlockPos): Beam {
        val top = level.maxY
        val blockers = ArrayList<BlockPos>()
        val cursor = BlockPos.MutableBlockPos()
        for (y in beacon.y + 1..top) {
            if (blocksBeam(level.getBlockState(cursor.set(beacon.x, y, beacon.z)))) blockers.add(cursor.immutable())
        }
        return Beam(beacon, top, blockers)
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
            for (b in all) {
                val x = b.pos.x + 0.5
                val z = b.pos.z + 0.5
                column(x - BEAM_HALF, b.pos.y + 1.0, z - BEAM_HALF, x + BEAM_HALF, b.top + 1.0, z + BEAM_HALF, beam)
                for (block in b.blockers) {
                    val x1 = block.x.toDouble()
                    val y1 = block.y.toDouble()
                    val z1 = block.z.toDouble()
                    column(x1 - GROW, y1 - GROW, z1 - GROW, x1 + 1 + GROW, y1 + 1 + GROW, z1 + 1 + GROW, blocked)
                    outline(x1 - GROW, y1 - GROW, z1 - GROW, x1 + 1 + GROW, y1 + 1 + GROW, z1 + 1 + GROW)
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

    /** The twelve edges of a box, seen through anything in front of them. */
    private fun outline(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
        for (y in doubleArrayOf(y1, y2)) {
            edge(x1, y, z1, x2, y, z1); edge(x2, y, z1, x2, y, z2); edge(x2, y, z2, x1, y, z2); edge(x1, y, z2, x1, y, z1)
        }
        edge(x1, y1, z1, x1, y2, z1); edge(x2, y1, z1, x2, y2, z1); edge(x2, y1, z2, x2, y2, z2); edge(x1, y1, z2, x1, y2, z2)
    }

    private fun edge(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
        Gizmos.line(Vec3(x1, y1, z1), Vec3(x2, y2, z2), BLOCKER_OUTLINE, OUTLINE_WIDTH).setAlwaysOnTop()
    }

    /** How far around you beacons are looked for, in chunks. */
    private const val CHUNKS = 12
    private const val MAX_BEACONS = 64
    private const val REFRESH_TICKS = 20

    private const val BEAM_HALF = 0.2
    private const val GROW = 0.005
    private const val BEAM_COLOUR = 0x60FFFFFF
    private const val BLOCKER_FILL = 0x50FF3030
    private const val BLOCKER_OUTLINE = 0xFFFF3030.toInt()
    private const val OUTLINE_WIDTH = 2.5f
}
