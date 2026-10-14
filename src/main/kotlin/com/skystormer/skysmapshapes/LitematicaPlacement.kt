package com.skystormer.skysmapshapes

import fi.dy.masa.litematica.config.Configs
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.schematic.LitematicaSchematic
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement
import fi.dy.masa.litematica.selection.AreaSelection
import fi.dy.masa.litematica.selection.Box
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

/**
 * [SpawnProofing]'s blocks as a Litematica placement, which Easy Place then puts down. Built in
 * memory and never saved: it goes when you leave, and Litematica does not keep it in its list of
 * placements to load next time. Only touched when Litematica is installed.
 */
internal object LitematicaPlacement {

    private var placement: SchematicPlacement? = null

    /** Shows [state] at every one of [spots] (packed block positions), replacing what was shown. */
    fun show(spots: LongArray, state: BlockState) {
        clear()
        if (spots.isEmpty()) return
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for (spot in spots) {
            val x = BlockPos.getX(spot); val y = BlockPos.getY(spot); val z = BlockPos.getZ(spot)
            if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z
            if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z
        }
        val origin = BlockPos(minX, minY, minZ)
        val area = AreaSelection()
        area.setName(NAME)
        area.addSubRegionBox(Box(origin, BlockPos(maxX, maxY, maxZ), REGION), false)
        val schematic = LitematicaSchematic.createEmptySchematic(area, AUTHOR)
            ?: return Log.warn("Litematica would not make an empty schematic for spawn proofing")
        val container = schematic.getSubRegionContainer(REGION)
            ?: return Log.warn("Litematica's spawn proofing schematic has no region")
        for (spot in spots) {
            container.set(BlockPos.getX(spot) - minX, BlockPos.getY(spot) - minY, BlockPos.getZ(spot) - minZ, state)
        }
        val manager = DataManager.getSchematicPlacementManager()
        // Adding a placement selects it; your own selection is put back.
        val selected = manager.selectedSchematicPlacement
        val made = SchematicPlacement.createFor(schematic, origin, NAME, true, true)
        made.setShouldBeSaved(false)
        if (made.shouldRenderEnclosingBox()) made.toggleRenderEnclosingBox()
        manager.addSchematicPlacement(made, false)
        manager.setSelectedSchematicPlacement(selected)
        placement = made
    }

    /** Whether Litematica's Easy Place is on. */
    fun easyPlaceOn(): Boolean = Configs.Generic.EASY_PLACE_MODE.booleanValue

    /** Litematica's own overlay through blocks setting, as it was before spawn proofing turned it on. */
    private var savedThrough: Boolean? = null

    /**
     * Turns Litematica's schematic overlay through blocks on while spawn proofing shows ([on]), and
     * back to how you had it after. It is Litematica's one setting, so other placements show
     * through blocks too meanwhile.
     */
    fun renderThrough(on: Boolean) {
        val option = Configs.Visuals.SCHEMATIC_OVERLAY_RENDER_THROUGH
        if (on) {
            if (savedThrough == null) {
                savedThrough = option.booleanValue
                if (!option.booleanValue) option.booleanValue = true
            }
        } else {
            savedThrough?.let { option.booleanValue = it }
            savedThrough = null
        }
    }

    fun clear() {
        val old = placement ?: return
        placement = null
        DataManager.getSchematicPlacementManager().removeSchematicPlacement(old, false)
    }

    private const val NAME = "Spawn proofing (Sky's Map Shapes)"
    private const val REGION = "spawn proofing"
    private const val AUTHOR = "Sky's Map Shapes"
}
