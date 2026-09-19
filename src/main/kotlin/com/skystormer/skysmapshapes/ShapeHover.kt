package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import xaero.map.gui.IRightClickableElement
import xaero.map.gui.dropdown.rightclick.RightClickOption

/**
 * Which shapes' outlines are under the mouse on the world map. Those outlines are drawn thicker,
 * a tooltip names them, and right-clicking there opens their menu instead of the map's.
 *
 * Outlines that lie on top of each other (the same shape added twice, or two squares sharing an
 * edge) are all under the mouse at once, so they are handled as a group: the tooltip lists each,
 * and the menu has Edit and Delete for each.
 */
object ShapeHover {

    /** The shapes whose outline is under the mouse, closest first. Set every frame. */
    @Volatile
    var hovered: List<MapShape> = emptyList()
        private set

    fun isHovered(shape: MapShape): Boolean = hovered.any { it.id == shape.id }

    /**
     * Works out [hovered] for the mouse at block ([mouseX], [mouseZ]), given how many blocks one
     * screen unit is. Anything within a few screen units of an outline counts, and never less
     * than a block, since the mouse position is only known to the block.
     */
    fun update(shapes: List<MapShape>, mouseX: Int, mouseZ: Int, blocksPerUnit: Float) {
        val px = mouseX + 0.5
        val pz = mouseZ + 0.5
        val near = shapes
            .map { it to it.geometry.distanceTo(px, pz) }
            .filter { (shape, distance) -> distance <= maxOf(1.0, ((shape.effectiveLineWidth / 2 + REACH) * blocksPerUnit).toDouble()) }
            .sortedBy { it.second }
        // The closest outline, plus any practically on top of it.
        val closest = near.firstOrNull()?.second
        hovered = if (closest == null) emptyList()
        else near.filter { it.second <= closest + maxOf(1.0, blocksPerUnit * 2.0) }.map { it.first }
    }

    fun clear() {
        hovered = emptyList()
    }

    /** The tooltip next to the mouse, while no Xaero element or menu is in the way. */
    fun drawTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val shapes = hovered
        if (shapes.isEmpty()) return
        val lines = ArrayList<Component>()
        for (shape in shapes) {
            lines.add(Component.literal(shape.name).withStyle { it.withColor(shape.colour and 0xFFFFFF) })
            lines.add(Component.literal(shape.describeSize()).withStyle { it.withColor(0xDDDDDD) })
        }
        lines.add(Component.literal(if (shapes.all { it.fromMiniHud }) "From MiniHUD · right-click to edit or hide" else "Right-click to edit or delete").withStyle { it.withColor(0xBBBBBB) })
        graphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, lines, mouseX, mouseY)
    }

    /** What a right-click on the map opens while outlines are hovered, or null for the map's own menu. */
    @JvmStatic
    fun rightClickTarget(): IRightClickableElement? {
        if (!Config.enabled) return null
        val shapes = hovered.takeIf { it.isNotEmpty() } ?: return null
        Log.info("Right-clicked the outline of {}", shapes.joinToString { it.name })
        return Target(shapes)
    }

    private class Target(private val shapes: List<MapShape>) : IRightClickableElement {
        override fun getRightClickOptions(): ArrayList<RightClickOption> {
            val options = ArrayList<RightClickOption>()
            if (shapes.size == 1) {
                val shape = shapes[0]
                options.add(title(shape.name, options.size))
                MapMenus.addShapeOptions(options, this, shape)
            } else {
                options.add(title("${shapes.size} shapes here", options.size))
                for (shape in shapes) {
                    val name = shape.name
                    if (shape is Shape) {
                        options.add(MapMenus.option("Edit: $name", options.size, this) { parent ->
                            ShapeStore.byId(shape.id)?.let { MapMenus.open(com.skystormer.skysmapshapes.gui.ShapeEditScreen.forExisting(parent, it)) }
                        })
                        options.add(MapMenus.option("Delete: $name", options.size, this) { parent -> MapMenus.confirmDelete(parent, shape) })
                    } else if (shape is MiniHudShape) {
                        options.add(MapMenus.option("Edit in MiniHUD: $name", options.size, this) { parent -> MiniHudShapes.openEditor(shape, parent) }
                            .setActive(shape.editable))
                        options.add(MapMenus.option("Hide MiniHUD's $name", options.size, this) { _ -> ShapeStore.setVisible(shape, false) })
                    }
                }
            }
            return options
        }

        override fun isRightClickValid(): Boolean = shapes.isNotEmpty()

        override fun getRightClickTitleBackgroundColor(): Int = shapes[0].colour

        private fun title(text: String, index: Int) = object : RightClickOption(text, index, this) {
            override fun onAction(screen: Screen) {}
        }
    }

    /** Extra screen units either side of an outline that still count as on it. */
    private const val REACH = 4f
}
