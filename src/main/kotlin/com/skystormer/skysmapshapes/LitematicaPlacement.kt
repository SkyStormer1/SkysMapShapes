package com.skystormer.skysmapshapes

import fi.dy.masa.litematica.config.Configs
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.schematic.LitematicaSchematic
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.selection.AreaSelection
import fi.dy.masa.litematica.selection.Box
import fi.dy.masa.litematica.world.SchematicWorldHandler
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

/**
 * [SpawnProofing]'s blocks as a Litematica placement, which Easy Place then puts down. Built in
 * memory and never saved: it goes when you leave, and Litematica does not keep it in its list of
 * placements to load next time. Only touched when Litematica is installed.
 *
 * The placement covers the spots with room to spare ([MARGIN] blocks each way, [MARGIN_Y] up and
 * down), and while the spots stay inside it, it is changed in place: the spots that come and go
 * are written straight into Litematica's schematic world and only their chunks are drawn again,
 * the old picture showing until the new one is ready. Making a new placement clears and redraws
 * every chunk it touches, which made the blocks blink each time you moved; now that only happens
 * when you walk out of the box.
 */
internal object LitematicaPlacement {

    private var placement: SchematicPlacement? = null
    private var container: LitematicaBlockStateContainer? = null
    private var shownState: BlockState? = null
    private val shown = LongOpenHashSet()

    /** The placement's box: min x, y, z, then max x, y, z; null when there is none. */
    var box: IntArray? = null
        private set

    /** Shows [state] at every one of [spots] (packed block positions), replacing what was shown. */
    fun show(spots: LongArray, state: BlockState) {
        if (spots.isEmpty()) return clear()
        if (state == shownState && changeInPlace(spots)) return
        rebuild(spots, state)
    }

    /**
     * Swaps the spots that changed, in the placement and in Litematica's schematic world, when
     * every one of [spots] fits the current box. False when it cannot, and a new placement is needed.
     */
    private fun changeInPlace(spots: LongArray): Boolean {
        val box = box ?: return false
        val container = container ?: return false
        val world = SchematicWorldHandler.getSchematicWorld() ?: return false
        if (spots.any { !inside(box, it) }) return false
        val wanted = LongOpenHashSet(spots)
        val state = shownState ?: return false
        val chunks = LongOpenHashSet()
        val pos = BlockPos.MutableBlockPos()
        val gone = shown.longIterator()
        while (gone.hasNext()) {
            val spot = gone.nextLong()
            if (spot in wanted) continue
            set(container, world, box, pos.set(spot), Blocks.AIR.defaultBlockState(), chunks)
            gone.remove()
        }
        for (spot in spots) {
            if (shown.add(spot)) set(container, world, box, pos.set(spot), state, chunks)
        }
        val each = chunks.longIterator()
        while (each.hasNext()) {
            val chunk = each.nextLong()
            world.scheduleChunkRenders(chunkX(chunk), chunkZ(chunk))
        }
        return true
    }

    private fun set(
        container: LitematicaBlockStateContainer, world: fi.dy.masa.litematica.world.WorldSchematic,
        box: IntArray, pos: BlockPos, state: BlockState, chunks: LongOpenHashSet,
    ) {
        container.set(pos.x - box[0], pos.y - box[1], pos.z - box[2], state)
        world.setBlock(pos, state, 0)
        chunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
    }

    /** A new placement around [spots], with room to spare. */
    private fun rebuild(spots: LongArray, state: BlockState) {
        clear()
        val level = Minecraft.getInstance().level ?: return
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for (spot in spots) {
            val x = BlockPos.getX(spot); val y = BlockPos.getY(spot); val z = BlockPos.getZ(spot)
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
        }
        minX -= MARGIN; minZ -= MARGIN; maxX += MARGIN; maxZ += MARGIN
        minY = maxOf(minY - MARGIN_Y, level.minY); maxY = minOf(maxY + MARGIN_Y, level.maxY)
        val origin = BlockPos(minX, minY, minZ)
        val area = AreaSelection()
        area.setName(NAME)
        area.addSubRegionBox(Box(origin, BlockPos(maxX, maxY, maxZ), REGION), false)
        val schematic = LitematicaSchematic.createEmptySchematic(area, AUTHOR)
            ?: return Log.warn("Litematica would not make an empty schematic for spawn proofing")
        val made = schematic.getSubRegionContainer(REGION)
            ?: return Log.warn("Litematica's spawn proofing schematic has no region")
        for (spot in spots) {
            made.set(BlockPos.getX(spot) - minX, BlockPos.getY(spot) - minY, BlockPos.getZ(spot) - minZ, state)
        }
        val manager = DataManager.getSchematicPlacementManager()
        // Adding a placement selects it; your own selection is put back.
        val selected = manager.selectedSchematicPlacement
        val added = SchematicPlacement.createFor(schematic, origin, NAME, true, true)
        added.setShouldBeSaved(false)
        if (added.shouldRenderEnclosingBox()) added.toggleRenderEnclosingBox()
        manager.addSchematicPlacement(added, false)
        manager.setSelectedSchematicPlacement(selected)
        placement = added
        container = made
        shownState = state
        shown.clear()
        shown.addAll(spots.asList())
        box = intArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /** Draws every chunk of the placement again, after a setting changed how Litematica draws it. */
    fun redraw() {
        val box = box ?: return
        val world = SchematicWorldHandler.getSchematicWorld() ?: return
        for (cx in (box[0] shr 4)..(box[3] shr 4)) {
            for (cz in (box[2] shr 4)..(box[5] shr 4)) world.scheduleChunkRenders(cx, cz)
        }
    }

    /** Whether Litematica's Easy Place is on. */
    fun easyPlaceOn(): Boolean = Configs.Generic.EASY_PLACE_MODE.booleanValue

    fun clear() {
        val old = placement ?: return
        placement = null
        container = null
        shownState = null
        shown.clear()
        box = null
        DataManager.getSchematicPlacementManager().removeSchematicPlacement(old, false)
    }

    private fun inside(box: IntArray, spot: Long): Boolean {
        val x = BlockPos.getX(spot); val y = BlockPos.getY(spot); val z = BlockPos.getZ(spot)
        return x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5]
    }

    private fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    private fun chunkX(key: Long): Int = (key shr 32).toInt()
    private fun chunkZ(key: Long): Int = key.toInt()

    /** Room to spare around the spots, so walking about changes the placement in place. */
    private const val MARGIN = 16
    private const val MARGIN_Y = 6

    private const val NAME = "Spawn proofing (Sky's Map Shapes)"
    private const val REGION = "spawn proofing"
    private const val AUTHOR = "Sky's Map Shapes"
}
