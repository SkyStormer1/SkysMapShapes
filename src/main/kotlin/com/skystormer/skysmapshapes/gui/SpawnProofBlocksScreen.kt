package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.SpawnProofBlocks
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Block

/**
 * Which blocks spawn proofing may put down: every slab, trapdoor, button, carpet, pressure plate
 * and rail, each switched on or off, with All and None for each kind. The one in your hand is used
 * when it is picked; otherwise the picked one you carry most of. Done saves.
 */
class SpawnProofBlocksScreen(private val parent: Screen?) : FramedScreen(Component.literal("Spawn proofing blocks")) {

    private val picked: MutableSet<String> = HashSet(Config.spawnProofBlocks ?: SpawnProofBlocks.defaults())

    override val resetTip = "Back to every slab and nothing else."

    override fun resetToDefaults() {
        picked.clear()
        picked += SpawnProofBlocks.defaults()
    }

    override fun content(top: Int) {
        val left = width / 2 - WIDTH / 2
        val half = (WIDTH - GAP) / 2
        val small = 40
        var y = top
        for ((kind, blocks) in SpawnProofBlocks.candidates().groupBy { SpawnProofBlocks.kindOf(it)!! }) {
            addRenderableWidget(StringWidget(left, y + (ROW - font.lineHeight) / 2, WIDTH - (small + GAP) * 2, font.lineHeight,
                Component.literal(kind.title), font))
            addRenderableWidget(Button.builder(Component.literal("All")) {
                blocks.forEach { picked += SpawnProofBlocks.id(it) }
                rebuildWidgets()
            }.bounds(left + WIDTH - small * 2 - GAP, y, small, ROW).build())
            addRenderableWidget(Button.builder(Component.literal("None")) {
                blocks.forEach { picked -= SpawnProofBlocks.id(it) }
                rebuildWidgets()
            }.bounds(left + WIDTH - small, y, small, ROW).build())
            y += ROW + GAP
            blocks.forEachIndexed { i, block ->
                val x = if (i % 2 == 0) left else left + half + GAP
                addRenderableWidget(blockButton(block, x, y, if (i % 2 == 0) half else WIDTH - half - GAP))
                if (i % 2 == 1 || i == blocks.lastIndex) y += ROW + GAP
            }
            y += GAP * 3
        }
    }

    /** A block's name, green when picked and grey when not; a click switches it. */
    private fun blockButton(block: Block, x: Int, y: Int, width: Int): Button {
        val id = SpawnProofBlocks.id(block)
        lateinit var button: Button
        button = Button.builder(label(block, id in picked)) {
            if (!picked.remove(id)) picked += id
            button.message = label(block, id in picked)
        }.bounds(x, y, width, ROW).tooltip(Tooltip.create(Component.literal(id))).build()
        return button
    }

    private fun label(block: Block, on: Boolean): Component =
        block.name.copy().withStyle { it.withColor(if (on) ON else OFF) }

    override fun onClose() {
        Config.spawnProofBlocks = if (picked == SpawnProofBlocks.defaults()) null else picked.toSet()
        Config.save()
        minecraft.gui.setScreen(parent)
    }

    private companion object {
        const val WIDTH = 300
        const val ROW = 20
        const val GAP = 2
        const val ON = 0x55FF55
        const val OFF = 0x808080
    }
}
