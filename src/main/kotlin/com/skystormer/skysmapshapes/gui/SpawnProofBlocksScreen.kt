package com.skystormer.skysmapshapes.gui

import com.mojang.blaze3d.platform.InputConstants
import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.SpawnProofBlocks
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import kotlin.math.roundToInt

/**
 * The accepted blocks for spawn proofing: every slab, trapdoor, button, carpet, pressure plate
 * and rail as a picture, like the creative inventory. Accepted ones have a green outline; a click
 * switches one, and dragging from it switches every block passed over the same way. Search by
 * name or kind ("slab", "oak"); All and None act on what the search shows. Done saves.
 */
class SpawnProofBlocksScreen(private val parent: Screen?) : Screen(Component.literal("Spawn proofing blocks")) {

    private val accepted: MutableSet<String> = HashSet(Config.spawnProofBlocks ?: SpawnProofBlocks.defaults())
    private var query = ""
    private var shown: List<Block> = emptyList()
    private var scroll = 0
    private var draggingBar = false

    /** While dragging across the grid: true to accept what is passed over, false to take it out. */
    private var painting: Boolean? = null

    private var gridLeft = 0
    private var gridTop = 0
    private var columns = 1
    private var visibleRows = 1

    override fun init() {
        val width = minOf(MAX_WIDTH, this.width - 16)
        val left = this.width / 2 - width / 2
        var y = 8 + font.lineHeight + 6

        val third = (width - GAP * 2) / 3
        val search = EditBox(font, left, y, third, ROW, Component.literal("Search"))
        search.setHint(Component.literal("Search blocks…"))
        search.setMaxLength(64)
        search.value = query
        search.setResponder { query = it; filter() }
        addRenderableWidget(search)
        addRenderableWidget(Button.builder(Component.literal("All")) {
            shown.forEach { accepted += SpawnProofBlocks.id(it) }
        }.bounds(left + third + GAP, y, third, ROW)
            .tooltip(Tooltip.create(Component.literal("Accept every block the search shows."))).build())
        addRenderableWidget(Button.builder(Component.literal("None")) {
            shown.forEach { accepted -= SpawnProofBlocks.id(it) }
        }.bounds(left + (third + GAP) * 2, y, width - (third + GAP) * 2, ROW)
            .tooltip(Tooltip.create(Component.literal("Accept none of the blocks the search shows."))).build())
        y += ROW + GAP * 3

        columns = maxOf(1, (width - BAR - GAP) / TILE)
        gridLeft = left + (width - BAR - GAP - columns * TILE) / 2
        gridTop = y
        val footer = ROW + GAP * 4
        visibleRows = maxOf(1, (this.height - gridTop - footer) / TILE)

        val half = (width - GAP) / 2
        val bottom = gridTop + visibleRows * TILE + GAP * 2
        addRenderableWidget(Button.builder(Component.literal("Every slab")) {
            accepted.clear()
            accepted += SpawnProofBlocks.defaults()
        }.bounds(left, bottom, half, ROW)
            .tooltip(Tooltip.create(Component.literal("Back to how the mod comes: every slab accepted, nothing else."))).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(left + half + GAP, bottom, width - half - GAP, ROW).build())
        filter()
        setInitialFocus(search)
    }

    private fun filter() {
        val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        shown = SpawnProofBlocks.candidates().filter { block ->
            val text = (block.name.string + " " + SpawnProofBlocks.kindOf(block)!!.title + " " + SpawnProofBlocks.id(block)).lowercase()
            words.all { text.contains(it) }
        }
        scroll = scroll.coerceIn(0, maxScroll())
    }

    private fun rows(): Int = (shown.size + columns - 1) / columns
    private fun maxScroll(): Int = maxOf(0, rows() - visibleRows)

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        graphics.centeredText(font, Component.literal("Accepted blocks: ${accepted.size}  (green = accepted, click to switch)"),
            width / 2, 8, 0xFFFFFFFF.toInt())

        val gridRight = gridLeft + columns * TILE
        val gridBottom = gridTop + visibleRows * TILE
        graphics.fill(gridLeft - 1, gridTop - 1, gridRight + 1, gridBottom + 1, 0x80000000.toInt())
        var hovered: Block? = null
        for (row in 0 until visibleRows) {
            for (column in 0 until columns) {
                val block = shown.getOrNull((row + scroll) * columns + column) ?: break
                val x = gridLeft + column * TILE
                val y = gridTop + row * TILE
                val on = SpawnProofBlocks.id(block) in accepted
                val over = mouseX in x until x + TILE && mouseY in y until y + TILE
                if (over) hovered = block
                graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, when {
                    over -> 0x60FFFFFF
                    on -> 0x4033CC33
                    else -> 0x30FFFFFF
                })
                if (on) graphics.outline(x, y, TILE, TILE, ACCEPTED)
                graphics.item(ItemStack(block), x + (TILE - 16) / 2, y + (TILE - 16) / 2)
            }
        }
        if (shown.isEmpty()) {
            graphics.centeredText(font, Component.literal("No blocks match \"$query\"."), (gridLeft + gridRight) / 2, gridTop + 8, 0xFFBBBBBB.toInt())
        }

        // The scroll bar.
        if (maxScroll() > 0) {
            val left = gridRight + GAP
            graphics.fill(left, gridTop, left + BAR, gridBottom, 0x40FFFFFF)
            graphics.fill(left, thumbTop(), left + BAR, thumbTop() + thumbHeight(), 0xC0FFFFFF.toInt())
        }

        hovered?.let { block ->
            val on = SpawnProofBlocks.id(block) in accepted
            graphics.setComponentTooltipForNextFrame(font, listOf(
                block.name,
                Component.literal(if (on) "Accepted: click to remove" else "Not accepted: click to accept")
                    .withStyle { it.withColor(if (on) ACCEPTED and 0xFFFFFF else 0x999999) },
            ), mouseX, mouseY)
        }
    }

    private fun trackHeight(): Int = visibleRows * TILE
    private fun thumbHeight(): Int = (trackHeight() * visibleRows / maxOf(1, rows())).coerceIn(minOf(16, trackHeight()), trackHeight())
    private fun thumbTop(): Int = gridTop + (trackHeight() - thumbHeight()) * scroll / maxOf(1, maxScroll())
    private fun barLeft(): Int = gridLeft + columns * TILE + GAP

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val x = event.x()
        val y = event.y()
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (maxScroll() > 0 && x >= barLeft() && x < barLeft() + BAR && y >= gridTop && y < gridTop + trackHeight()) {
                draggingBar = true
                dragBarTo(y)
                return true
            }
            val block = blockAt(x, y)
            if (block != null) {
                val accept = SpawnProofBlocks.id(block) !in accepted
                painting = accept
                paint(block, accept)
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (draggingBar) {
            dragBarTo(event.y())
            return true
        }
        painting?.let { accept ->
            blockAt(event.x(), event.y())?.let { paint(it, accept) }
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (painting != null) {
            painting = null
            return true
        }
        if (draggingBar) {
            draggingBar = false
            return true
        }
        return super.mouseReleased(event)
    }

    /** The block in the grid under x, y, or null. */
    private fun blockAt(x: Double, y: Double): Block? {
        if (x < gridLeft || y < gridTop) return null
        val column = ((x - gridLeft) / TILE).toInt()
        val row = ((y - gridTop) / TILE).toInt()
        if (column !in 0 until columns || row !in 0 until visibleRows) return null
        return shown.getOrNull((row + scroll) * columns + column)
    }

    private fun paint(block: Block, accept: Boolean) {
        val id = SpawnProofBlocks.id(block)
        if (accept) accepted += id else accepted -= id
    }

    private fun dragBarTo(mouseY: Double) {
        val room = maxOf(1, trackHeight() - thumbHeight())
        scroll = ((mouseY - gridTop - thumbHeight() / 2.0) / room * maxScroll()).roundToInt().coerceIn(0, maxScroll())
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0 && maxScroll() > 0) {
            scroll = (scroll - scrollY.roundToInt().coerceIn(-3, 3).let { if (it == 0) (if (scrollY > 0) 1 else -1) else it })
                .coerceIn(0, maxScroll())
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    override fun onClose() {
        Config.spawnProofBlocks = if (accepted == SpawnProofBlocks.defaults()) null else accepted.toSet()
        Config.save()
        minecraft.gui.setScreen(parent)
    }

    private companion object {
        const val MAX_WIDTH = 360
        const val ROW = 20
        const val GAP = 2
        const val TILE = 20
        const val BAR = 4
        const val ACCEPTED = 0xFF55FF55.toInt()
    }
}
