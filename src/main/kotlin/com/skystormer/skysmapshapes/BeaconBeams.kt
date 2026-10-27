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
 * Experimental: a beam drawn straight up from every beacon within [Config.beaconDistance] chunks of
 * you (up to [Config.beaconLimit], nearest first), lit or not ([Config.beaconBeams]), and the blocks in its way
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

    /** The level everything below was read from; a different one (another dimension) starts it all again. */
    private var scannedLevel: Level? = null

    /** The beacons found in each chunk in range, by chunk key. A chunk with none is kept as an empty list, so it is not scanned again. */
    private val chunkBeacons = HashMap<Long, List<BlockPos>>()

    /** The beacons standing in each block column (x and z), so a changed block finds the beams it can affect. */
    private val columns = HashMap<Long, ArrayList<BlockPos>>()

    /** Each beacon's beam as last read, by packed position; one is dropped when a block in its column changes. */
    private val made = HashMap<Long, Beam>()

    /** Chunks to scan, nearest first: ones in range not yet scanned, and ones where a beacon was placed or broken. */
    private val pending = ArrayDeque<Long>()
    private val rescan = HashSet<Long>()

    /** Whether [beams] must be worked out again from [chunkBeacons] and [made]. */
    private var rebuild = true

    /** The heights the beams in [made] were read between; moving either slider reads them again. */
    private var seenLow = Int.MIN_VALUE
    private var seenHigh = Int.MIN_VALUE

    /** Finds the beacons around you and what blocks each one, a little each tick. Main thread. */
    fun tick() {
        if (broken) return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level
        val player = minecraft.player
        if (!Config.enabled || !(Config.beaconBeams || Config.beaconOutlines) || level == null || player == null) {
            if (scannedLevel != null || beams.isNotEmpty()) forgetAll()
            return
        }
        try {
            if (level !== scannedLevel) {
                forgetAll()
                scannedLevel = level
            }
            // The beam is only read between the two heights, so moving either slider reads it again.
            if (Config.beaconMinY != seenLow || Config.beaconMaxY != seenHigh) {
                seenLow = Config.beaconMinY
                seenHigh = Config.beaconMaxY
                made.clear()
                rebuild = true
            }
            val here = player.blockPosition()
            if (ticks % REFRESH_TICKS == 0) {
                plan(level, here, minOf(Config.beaconDistance, minecraft.options.effectiveRenderDistance))
                rebuild = true
            }
            // Anything that changed without a block update being seen (a chunk sent again, say) is caught here.
            if (ticks % SAFETY_NET_TICKS == SAFETY_NET_TICKS - 1) {
                rescan.addAll(chunkBeacons.keys)
                pending.addAll(chunkBeacons.keys)
                made.clear()
            }
            ticks++
            scanSome(level)
            if (rebuild) rebuildBeams(level, here)
        } catch (e: Throwable) {
            broken = true
            forgetAll()
            Log.error("Could not find the beacons around you; beacon beams are off until the game restarts", e)
        }
    }

    private fun forgetAll() {
        scannedLevel = null
        chunkBeacons.clear()
        columns.clear()
        made.clear()
        pending.clear()
        rescan.clear()
        beams = emptyList()
        rebuild = true
        seenLow = Int.MIN_VALUE
        seenHigh = Int.MIN_VALUE
    }

    private fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    /**
     * Works out which chunks are in range, as a circle round you: forgets the ones that fell out,
     * and lines up the ones still to be scanned, nearest first.
     */
    private fun plan(level: Level, here: BlockPos, chunks: Int) {
        val middleX = here.x shr 4
        val middleZ = here.z shr 4
        val wanted = HashMap<Long, Int>()
        for (x in middleX - chunks..middleX + chunks) {
            for (z in middleZ - chunks..middleZ + chunks) {
                val dx = x - middleX
                val dz = z - middleZ
                val distance = dx * dx + dz * dz
                if (distance <= chunks * chunks) wanted[chunkKey(x, z)] = distance
            }
        }
        for (key in chunkBeacons.keys.filter { it !in wanted }) forgetChunk(key)
        rescan.retainAll(wanted.keys)
        pending.clear()
        pending.addAll(wanted.keys.filter { it !in chunkBeacons || it in rescan }.sortedBy { wanted[it] })
    }

    /** At most [SCANS_PER_TICK] chunks off the line, so a big first scan is spread over a few ticks. */
    private fun scanSome(level: Level) {
        var scans = 0
        while (scans < SCANS_PER_TICK) {
            val key = pending.removeFirstOrNull() ?: return
            if (key in chunkBeacons && key !in rescan) continue
            // Not loaded yet: left alone, and tried again at the next plan.
            val chunk = level.chunkSource.getChunk((key shr 32).toInt(), key.toInt(), false) ?: continue
            scans++
            forgetChunk(key)
            val found = beaconsIn(chunk)
            chunkBeacons[key] = found
            for (beacon in found) columns.getOrPut(chunkKey(beacon.x, beacon.z)) { ArrayList() }.add(beacon)
            rescan.remove(key)
            rebuild = true
        }
    }

    private fun forgetChunk(key: Long) {
        val old = chunkBeacons.remove(key) ?: return
        for (beacon in old) {
            made.remove(beacon.asLong())
            val column = chunkKey(beacon.x, beacon.z)
            columns[column]?.let { it.remove(beacon); if (it.isEmpty()) columns.remove(column) }
        }
        rebuild = true
    }

    /**
     * The nearest [Config.beaconLimit] beacons' beams. Only a beacon whose column has not been read
     * is read now, [BEAMS_PER_TICK] at a time; the rest follow on the next ticks.
     */
    private fun rebuildBeams(level: Level, here: BlockPos) {
        rebuild = false
        val found = ArrayList<BlockPos>()
        for (list in chunkBeacons.values) found.addAll(list)
        // Nearest first, so a huge beacon wall still shows the ones around you.
        val picked = found.sortedBy { it.distSqr(here) }.take(Config.beaconLimit)
        var budget = BEAMS_PER_TICK
        val out = ArrayList<Beam>(picked.size)
        for (beacon in picked) {
            val key = beacon.asLong()
            var beam = made[key]
            if (beam == null) {
                if (budget == 0) { rebuild = true; continue }
                budget--
                beam = beamOf(level, beacon)
                made[key] = beam
            }
            out.add(beam)
        }
        beams = out
    }

    /**
     * Called as each block changes (from `ClientLevelMixin`). A beacon placed or broken has its chunk
     * scanned again; a block in a beacon's column above it has that beam read again.
     */
    @JvmStatic
    fun blockChanged(pos: BlockPos, old: BlockState, new: BlockState) {
        if (scannedLevel == null) return
        if (old.`is`(Blocks.BEACON) || new.`is`(Blocks.BEACON)) {
            val key = chunkKey(pos.x shr 4, pos.z shr 4)
            if (key in chunkBeacons && rescan.add(key)) pending.addFirst(key)
        }
        columns[chunkKey(pos.x, pos.z)]?.let { beacons ->
            for (beacon in beacons) {
                if (pos.y > beacon.y && made.remove(beacon.asLong()) != null) rebuild = true
            }
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

    /** Only the part of the column the height sliders can show is read, from just above the beacon. */
    private fun beamOf(level: Level, beacon: BlockPos): Beam {
        val top = level.maxY
        val first = maxOf(beacon.y + 1, Config.beaconMinY)
        val last = minOf(top, Config.beaconMaxY)
        val runs = ArrayList<IntRange>()
        val cursor = BlockPos.MutableBlockPos()
        var start = Int.MIN_VALUE
        for (y in first..last + 1) {
            val blocks = y <= last && blocksBeam(level.getBlockState(cursor.set(beacon.x, y, beacon.z)))
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

    /** Ticks between working out which chunks are in range and which beacons are the nearest: a second. */
    private const val REFRESH_TICKS = 20

    /** Ticks between scanning every chunk in range again, in case a change was never seen. */
    private const val SAFETY_NET_TICKS = 600

    /** Chunks scanned for beacons in one tick, so a big first scan is spread over a few ticks. */
    private const val SCANS_PER_TICK = 64

    /** Beacon columns read in one tick. */
    private const val BEAMS_PER_TICK = 32

    private const val BEAM_HALF = 0.2
    private const val GROW = 0.005
    private const val BEAM_COLOUR = 0x60FFFFFF
    private const val BLOCKER_FILL = 0x50FF3030
    private const val BLOCKER_OUTLINE = 0xFFFF3030.toInt()
    private const val OUTLINE_WIDTH = 2.5f
}
