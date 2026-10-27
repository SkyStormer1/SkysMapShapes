package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Colours
import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.SpawnProofSetup
import com.skystormer.skysmapshapes.SpawnProofing
import com.skystormer.skysmapshapes.gui.ConfigScreen.Companion.GAP
import com.skystormer.skysmapshapes.gui.ConfigScreen.Companion.ROW
import com.skystormer.skysmapshapes.gui.ConfigScreen.Companion.lerp
import com.skystormer.skysmapshapes.gui.ConfigScreen.Companion.toSlider
import com.skystormer.skysmapshapes.gui.ConfigScreen.Companion.toggle
import com.skystormer.skysmapshapes.gui.ConfigScreen.Slider
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * The MiniHUD light level settings and Easy Place spawn proofing, on a screen of their own off
 * the main settings: which floors MiniHUD marks, and how spawn proofing covers them. Opened from
 * the main settings, and only there when MiniHUD is installed. Done saves.
 */
class LightLevelsScreen(private val parent: Screen?) : FramedScreen(Component.literal("Light levels & spawn proofing")) {

    private var lightInsideShapes = Config.lightInsideShapes
    private var netherBiomeSpawns = Config.netherBiomeSpawns
    private var spawnProofing = Config.spawnProofing
    private var spawnProofEverywhere = Config.spawnProofEverywhere
    private var spawnProofRange = Config.spawnProofRange
    private var spawnProofRenderThrough = Config.spawnProofRenderThrough
    private var spawnProofColour = Config.spawnProofColour
    private var spawnProofGhostBlocks = Config.spawnProofGhostBlocks
    private var spawnProofOpacity = Config.spawnProofOpacity
    private var spawnProofHeight = Config.spawnProofHeight

    override val resetTip = "Puts the light level and spawn proofing settings back to how the mod comes. Your accepted blocks are kept."

    override fun resetToDefaults() {
        lightInsideShapes = Config.default(Config::lightInsideShapes)
        netherBiomeSpawns = Config.default(Config::netherBiomeSpawns)
        spawnProofing = Config.default(Config::spawnProofing)
        spawnProofEverywhere = Config.default(Config::spawnProofEverywhere)
        spawnProofRange = Config.default(Config::spawnProofRange)
        spawnProofRenderThrough = Config.default(Config::spawnProofRenderThrough)
        spawnProofColour = Config.default(Config::spawnProofColour)
        spawnProofGhostBlocks = Config.default(Config::spawnProofGhostBlocks)
        spawnProofOpacity = Config.default(Config::spawnProofOpacity)
        spawnProofHeight = Config.default(Config::spawnProofHeight)
    }

    override fun content(top: Int) {
        val left = width / 2 - panelWidth / 2
        val third = (panelWidth - GAP * 2) / 3
        var y = top

        if (SpawnProofing.installed) y = setup(left, y)

        if (MiniHudShapes.installed) {
            addRenderableWidget(toggle(left, y, panelWidth, "MiniHUD light levels only in shapes", lightInsideShapes,
                "With MiniHUD's light level overlay on, only show light levels inside your shapes. " +
                    "Right-click a shape to choose how it counts: show (the default), none (dark inside, even within another shape) or ignore. " +
                    "MiniHUD shapes count while they are on in MiniHUD, at their real size and height; " +
                    "this mod's own count while shown on the map, at every height. Needs MiniHUD shapes on.") { lightInsideShapes = it })
            y += ROW + GAP
            addRenderableWidget(toggle(left, y, panelWidth, "MiniHUD: Nether spawns by biome", netherBiomeSpawns,
                "In the Nether, MiniHUD's light level overlay marks a floor when a mob of that biome can spawn on it. " +
                    "Magma counts where fire-immune mobs spawn (wastes, crimson forest, soul sand valley, basalt deltas); " +
                    "red nether wart blocks do not count in a crimson forest. " +
                    "The colours still follow light: only wither skeletons, skeletons and endermen need the dark.") { netherBiomeSpawns = it })
            y += ROW + GAP
        }

        if (SpawnProofing.installed) {
            // Spawn proofing with Litematica's Easy Place: only with MiniHUD and Litematica both installed.
            val halfRow = (panelWidth - GAP) / 2
            addRenderableWidget(toggle(left, y, halfRow, "Easy Place spawn proofing", spawnProofing,
                "With MiniHUD's light level overlay on, the floors it marks where mobs could spawn get a Litematica block " +
                    "(a bottom slab, say) for Easy Place to put down. Only inside shapes you right-click and set to spawn proofing, " +
                    "unless Everywhere is on. In the Overworld and the End a lit floor is left alone; in the Nether light does not stop " +
                    "spawns, so every marked floor counts. MiniHUD's light level hotkey switches it on and off with the overlay.") { spawnProofing = it })
            addRenderableWidget(toggle(left + halfRow + GAP, y, panelWidth - halfRow - GAP, "Everywhere", spawnProofEverywhere,
                "On: every floor the overlay marks counts, not only those inside spawn-proofed shapes.") {
                spawnProofEverywhere = it
            })
            y += ROW + GAP
            addRenderableWidget(Slider(left, y, halfRow, toSlider(spawnProofRange.toFloat(), Config.MIN_SPAWN_PROOF_RANGE.toFloat(), Config.MAX_SPAWN_PROOF_RANGE.toFloat()),
                { rangeOf(it).let { r -> if (r >= Config.MAX_SPAWN_PROOF_RANGE) "Distance: as far as MiniHUD" else "Distance: $r blocks" } },
                "How far from you, in blocks each way, spawn proofing puts blocks. At the top it goes as far as MiniHUD's light levels: " +
                    "MiniHUD's Light Level Range setting, 24 blocks unless you change it, at most 64. Turn it down if the game slows.") { spawnProofRange = rangeOf(it) })
            addRenderableWidget(Button.builder(Component.literal("Blocks…")) {
                save()
                minecraft.gui.setScreen(SpawnProofBlocksScreen(this))
            }.bounds(left + halfRow + GAP, y, panelWidth - halfRow - GAP, ROW)
                .tooltip(Tooltip.create(Component.literal(
                    "The accepted blocks: which slabs, trapdoors, buttons, carpets, pressure plates and rails it may use. " +
                        "The one in your hand is used when it is accepted; Easy Placing a spot with any other block swaps in an accepted one you carry."
                ))).build())
            y += ROW + GAP
            addRenderableWidget(toggle(left + halfRow + GAP, y, panelWidth - halfRow - GAP, "Litematica blocks", spawnProofGhostBlocks,
                "Litematica's see-through blocks on the spots. Off: only this mod's overlay marks them, and Easy Place still puts the blocks down. " +
                    "Only spawn proofing's spots: your other placements are left alone.") { spawnProofGhostBlocks = it })
            addRenderableWidget(toggle(left, y, halfRow, "Show through blocks", spawnProofRenderThrough,
                "Each patch of spots still to cover gets one outline, in the overlay colour, drawn through blocks, so you can see spots behind walls. " +
                    "Only spawn proofing's spots: Litematica's settings and your other placements are left alone.") { spawnProofRenderThrough = it })
            y += ROW + GAP
            // The overlay on the spots, drawn by this mod instead of Litematica's, so it can be toned down.
            val overlayTip = "Spawn proofing's spots get this mod's overlay instead of Litematica's: "
            addRenderableWidget(
                CycleButton.builder<Int>({ Component.literal(Colours.name(it)).withStyle { s -> s.withColor(it and 0xFFFFFF) } }, spawnProofColour)
                    .withValues(Colours.entries.map { it.argb }.let { if (spawnProofColour in it) it else listOf(spawnProofColour) + it })
                    .create(left, y, third, ROW, Component.literal("Overlay")) { _, value -> spawnProofColour = value }
                    .also { it.setTooltip(Tooltip.create(Component.literal(overlayTip +
                        "its colour, also used for the through-blocks outline. Your other Litematica placements keep Litematica's colours."))) }
            )
            addRenderableWidget(Slider(left + third + GAP, y, third, spawnProofOpacity.toDouble(),
                { if (it <= 0.0) "Opacity: off" else "Opacity ${(lerp(it, 0f, 1f, 0.05f) * 100).roundToInt()}%" },
                overlayTip + "how solid it is. At off only Litematica's ghost blocks show where to place.") { spawnProofOpacity = lerp(it, 0f, 1f, 0.05f) })
            addRenderableWidget(Slider(left + (third + GAP) * 2, y, panelWidth - (third + GAP) * 2,
                toSlider(spawnProofHeight.toFloat(), Config.MIN_SPAWN_PROOF_HEIGHT.toFloat(), Config.MAX_SPAWN_PROOF_HEIGHT.toFloat()),
                { heightText(heightOf(it)) },
                overlayTip + "how tall it is, from a thin sheet on the floor up to a whole block.") { spawnProofHeight = heightOf(it) })
            y += ROW + GAP
        }
    }

    /**
     * The Setup lines, from [top]: one per [SpawnProofSetup.Check], ✔ or ✘ (? when it cannot tell),
     * its reason as a tooltip and a Fix button where it has a fix; then Fix all. Looked at again when
     * the screen opens and after each fix. Returns where the next row goes.
     */
    private fun setup(left: Int, top: Int): Int {
        var y = top
        for (check in SpawnProofSetup.Check.entries) {
            val passes = SpawnProofSetup.passes(check)
            val (mark, colour) = when (passes) {
                true -> "✔" to PASS
                false -> "✘" to FAIL
                null -> "?" to UNKNOWN
            }
            val tip = check.why + when (passes) {
                true -> ""
                false -> "\n\nNow: " + SpawnProofSetup.problem(check) + "." + (SpawnProofSetup.advice(check)?.let { " $it" } ?: "")
                null -> "\n\nCannot tell with this version of MiniHUD or Litematica."
            }
            val text = Component.literal("$mark ").withStyle { it.withColor(colour) }.append(Component.literal(SpawnProofSetup.line(check)))
            addRenderableWidget(StringWidget(left, y + (ROW - font.lineHeight) / 2, text, font).also { it.setTooltip(Tooltip.create(Component.literal(tip))) })
            if (passes == false && SpawnProofSetup.canFix(check)) {
                addRenderableWidget(Button.builder(Component.literal("Fix")) {
                    SpawnProofSetup.fix(check)
                    rebuildWidgets()
                }.bounds(left + panelWidth - FIX_WIDTH, y, FIX_WIDTH, ROW).tooltip(Tooltip.create(Component.literal(check.why))).build())
            }
            y += ROW + SETUP_GAP
        }
        val fixable = SpawnProofSetup.fixable()
        addRenderableWidget(Button.builder(Component.literal("Fix all")) {
            SpawnProofSetup.fixable().forEach { SpawnProofSetup.fix(it) }
            rebuildWidgets()
        }.bounds(left, y, panelWidth, ROW)
            .tooltip(Tooltip.create(Component.literal(
                "Applies every fix above: switches on what spawn proofing needs in MiniHUD and Litematica. Nothing there changes except when you click."
            ))).build().also { it.active = fixable.isNotEmpty() })
        return y + ROW + GAP
    }

    private fun save() {
        val proofingChanged = Config.spawnProofing != spawnProofing || Config.spawnProofEverywhere != spawnProofEverywhere ||
            Config.spawnProofRange != spawnProofRange || Config.spawnProofRenderThrough != spawnProofRenderThrough ||
            (Config.spawnProofOpacity <= 0f) != (spawnProofOpacity <= 0f)
        val ghostChanged = Config.spawnProofGhostBlocks != spawnProofGhostBlocks
        Config.lightInsideShapes = lightInsideShapes
        Config.netherBiomeSpawns = netherBiomeSpawns
        Config.spawnProofing = spawnProofing
        Config.spawnProofEverywhere = spawnProofEverywhere
        Config.spawnProofRange = spawnProofRange
        Config.spawnProofRenderThrough = spawnProofRenderThrough
        Config.spawnProofColour = spawnProofColour
        Config.spawnProofGhostBlocks = spawnProofGhostBlocks
        Config.spawnProofOpacity = spawnProofOpacity
        Config.spawnProofHeight = spawnProofHeight
        Config.save()
        if (proofingChanged) SpawnProofing.refresh()
        if (ghostChanged) SpawnProofing.redrawLitematica()
    }

    override fun onClose() {
        save()
        minecraft.gui.setScreen(parent)
    }

    private companion object {
        private const val FIX_WIDTH = 50
        private const val SETUP_GAP = 2
        private const val PASS = 0x55FF55
        private const val FAIL = 0xFF5555
        private const val UNKNOWN = 0xAAAAAA

        /** Spawn proofing's distance in blocks, for the slider's value. */
        fun rangeOf(slider: Double): Int =
            lerp(slider, Config.MIN_SPAWN_PROOF_RANGE.toFloat(), Config.MAX_SPAWN_PROOF_RANGE.toFloat(), 1f).roundToInt()

        /** The overlay's height in sixteenths of a block, for the slider's value. */
        fun heightOf(slider: Double): Int =
            lerp(slider, Config.MIN_SPAWN_PROOF_HEIGHT.toFloat(), Config.MAX_SPAWN_PROOF_HEIGHT.toFloat(), 1f).roundToInt()

        fun heightText(sixteenths: Int): String = when (sixteenths) {
            16 -> "Height: 1 block"
            8 -> "Height: half a block"
            else -> "Height: $sixteenths/16"
        }
    }
}
