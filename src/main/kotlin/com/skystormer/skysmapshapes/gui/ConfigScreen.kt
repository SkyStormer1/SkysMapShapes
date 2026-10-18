package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Colours
import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.Shape
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

/**
 * Everything in `config/skysmapshapes.json`, on two tabs. Shapes: where shapes show, how thick and
 * how shaded they are, and the presets offered when adding one — by default vanilla's despawn
 * distances, which any server may change, so every one can be renamed, resized, recoloured or
 * cleared. Experimental: settings still being tried out, off unless you turn them on. Done saves.
 *
 * Built from plain vanilla widgets rather than a config library, like Sky's Map Exposer's.
 */
class ConfigScreen(private val parent: Screen?) : FramedScreen(Component.literal("Sky's Map Shapes")) {

    private class PresetDraft(var name: String, var radius: String, var colour: Int)

    private enum class Page(val title: String) { SHAPES("Shapes"), EXPERIMENTAL("Experimental") }

    private var page = Page.SHAPES

    private var showOnWorldMap = Config.showOnWorldMap
    private var showOnMinimap = Config.showOnMinimap
    private var showLabels = Config.showLabels
    private var enabled = Config.enabled
    private var showPanel = Config.showPanel
    private var thicknessScale = Config.thicknessScale
    private var showMiniHud = Config.showMiniHud
    private var miniHudIncludeDisabled = Config.miniHudIncludeDisabled
    private var hideInMiniHud = Config.hideInMiniHud
    private var fillOpacity = Config.fillOpacity
    private var beaconBeams = Config.beaconBeams
    private val presets = MutableList(Config.MAX_PRESETS) { i ->
        Config.presets.getOrNull(i)?.let { PresetDraft(it.name, Shape.number(it.radius), it.colour) }
            ?: PresetDraft("", "", Colours.entries[i % Colours.entries.size].argb)
    }
    private val nameBoxes = ArrayList<EditBox>()
    private val radiusBoxes = ArrayList<EditBox>()

    override val resetTip = "Puts the switches, thickness and fill back to how the mod comes. " +
        "Your shapes and presets are kept; Vanilla presets resets those. Light levels and spawn proofing have their own screen and reset."

    override fun resetToDefaults() {
        keepEdits()
        enabled = true
        showPanel = true
        showOnWorldMap = true
        showOnMinimap = true
        showLabels = true
        showMiniHud = true
        hideInMiniHud = true
        miniHudIncludeDisabled = false
        thicknessScale = 1f
        fillOpacity = 0.15f
        beaconBeams = false
    }

    override fun content(top: Int) {
        nameBoxes.clear()
        radiusBoxes.clear()
        val left = width / 2 - WIDTH / 2
        var y = top

        // The tabs: the one showing is greyed out. The last takes what rounding leaves.
        val tabs = Page.entries.size
        val tabWidth = (WIDTH - GAP * (tabs - 1)) / tabs
        Page.entries.forEachIndexed { i, tab ->
            val x = left + i * (tabWidth + GAP)
            addRenderableWidget(Button.builder(Component.literal(tab.title)) { switchPage(tab) }
                .bounds(x, y, if (i < tabs - 1) tabWidth else WIDTH - (tabWidth + GAP) * i, ROW).build()
                .also { it.active = tab != page })
        }
        y += ROW + GAP * 3

        when (page) {
            Page.SHAPES -> shapesPage(left, y)
            Page.EXPERIMENTAL -> experimentalPage(left, y)
        }
    }

    private fun switchPage(to: Page) {
        keepEdits()
        page = to
        rebuildWidgets()
    }

    /** Settings still being tried out; all off as the mod comes. */
    private fun experimentalPage(left: Int, top: Int) {
        var y = top
        val about = "Still being tried out: these may change or go in a later version."
        addRenderableWidget(MultiLineTextWidget(left, y, Component.literal(about), font).setMaxWidth(WIDTH))
        y += font.lineHeight * font.split(Component.literal(about), WIDTH).size + GAP * 3
        addRenderableWidget(toggle(left, y, WIDTH, "Beacon beams", beaconBeams,
            "Draws a beam straight up from every beacon near you, lit or not, and outlines in red, through walls, " +
                "every block in its way: the ones to break to light it. As in the game, only full solid blocks stop it: " +
                "glass, leaves, slabs, stairs, anvils, chains, honey and bedrock let it through, but tinted glass does not.") { beaconBeams = it })
    }

    /** Where shapes show, how they look, and the presets. */
    private fun shapesPage(left: Int, top: Int) {
        val third = (WIDTH - GAP * 2) / 3
        var y = top

        // The switch for the whole mod, the panel, and the way to the shapes list, first.
        addRenderableWidget(toggle(left, y, third, "Mod", enabled,
            "Off turns the whole mod off: no shapes drawn, no right-click options, no Shapes panel on the map. Your shapes are kept.") { enabled = it })
        addRenderableWidget(toggle(left + third + GAP, y, third, "Map panel", showPanel,
            "The Shapes panel on the world map: every shape in the dimension shown, to hide, show or right-click. It docks under Sky's Map Exposer's bar.") { showPanel = it })
        addRenderableWidget(Button.builder(Component.literal("Shapes list…")) {
            save()
            minecraft.gui.setScreen(ShapeListScreen(this))
        }.bounds(left + (third + GAP) * 2, y, WIDTH - (third + GAP) * 2, ROW)
            .tooltip(Tooltip.create(Component.literal("All your shapes: show, hide, find, edit or delete them.")))
            .build())
        y += ROW + GAP * 3

        addRenderableWidget(toggle(left, y, third, "World map", showOnWorldMap, "Draw shapes on Xaero's World Map.") { showOnWorldMap = it })
        addRenderableWidget(toggle(left + third + GAP, y, third, "Minimap", showOnMinimap, "Draw shapes on Xaero's Minimap too.") { showOnMinimap = it })
        addRenderableWidget(toggle(left + (third + GAP) * 2, y, third, "Labels", showLabels, "Show each shape's label on the world map. Right-click a label to edit or delete its shape.") { showLabels = it })
        y += ROW + GAP

        if (MiniHudShapes.installed) {
            // MiniHUD's shapes: only when it is installed.
            addRenderableWidget(toggle(left, y, third, "MiniHUD shapes", showMiniHud,
                "Show MiniHUD's shapes on the map as outlines, even with MiniHUD's own shape renderer off. Delete one in MiniHUD and it goes from the map too.") { showMiniHud = it })
            addRenderableWidget(toggle(left + third + GAP, y, third, "Hide all: MiniHUD", hideInMiniHud,
                "On: Hide all and Show all in the Shapes list switch MiniHUD's shapes off and on in MiniHUD too, so they go from the world as well. Off: they only hide them on the map. One shape at a time, you choose each time.") { hideInMiniHud = it })
            addRenderableWidget(toggle(left + (third + GAP) * 2, y, third, "Ones off in MiniHUD", miniHudIncludeDisabled,
                "Also draw MiniHUD shapes that are switched off in MiniHUD. They are always in the shapes list, so they can be switched back on.") { miniHudIncludeDisabled = it })
            y += ROW + GAP
            addRenderableWidget(Button.builder(Component.literal("Light levels & spawn proofing…")) {
                save()
                minecraft.gui.setScreen(LightLevelsScreen(this))
            }.bounds(left, y, WIDTH, ROW)
                .tooltip(Tooltip.create(Component.literal(
                    "MiniHUD's light level overlay inside your shapes, Nether spawns by biome, and Easy Place spawn proofing with its overlay."
                ))).build())
            y += ROW + GAP
        }

        val half = (WIDTH - GAP) / 2
        addRenderableWidget(Slider(left, y, half, toSlider(thicknessScale, Config.MIN_SCALE, Config.MAX_SCALE),
            { "Thickness ${(lerp(it, Config.MIN_SCALE, Config.MAX_SCALE, 0.05f) * 100).roundToInt()}%" },
            "Makes every shape's outline thicker or thinner at once, on top of each shape's own thickness (set in its Edit screen). 100% draws them as set.") {
            thicknessScale = lerp(it, Config.MIN_SCALE, Config.MAX_SCALE, 0.05f)
        })
        addRenderableWidget(Slider(left + half + GAP, y, half, fillOpacity.toDouble(),
            { "Fill ${(lerp(it, 0f, 1f, 0.05f) * 100).roundToInt()}%" },
            "How strongly the inside of a filled shape is shaded.") { fillOpacity = lerp(it, 0f, 1f, 0.05f) })
        y += ROW + GAP * 3

        addRenderableWidget(StringWidget(left, y, WIDTH, font.lineHeight, Component.literal("Presets: circles offered when adding a shape"), font))
        y += font.lineHeight + GAP * 2

        val nameWidth = 150
        val radiusWidth = 50
        for (preset in presets) {
            nameBoxes.add(field(left, y, nameWidth, preset.name, "unused", 32))
            radiusBoxes.add(field(left + nameWidth + GAP, y, radiusWidth, preset.radius, "radius", 10)
                .also { it.setTooltip(Tooltip.create(Component.literal("Radius in blocks."))) })
            val colourLeft = left + nameWidth + radiusWidth + GAP * 2
            addRenderableWidget(
                CycleButton.builder<Int>({ Component.literal(Colours.name(it)).withStyle { s -> s.withColor(it and 0xFFFFFF) } }, preset.colour)
                    .withValues(Colours.entries.map { it.argb }.let { if (preset.colour in it) it else listOf(preset.colour) + it })
                    .displayOnlyValue()
                    .create(colourLeft, y, left + WIDTH - colourLeft, ROW, Component.literal("Colour")) { _, value -> preset.colour = value }
            )
            y += ROW + GAP
        }
        y += GAP

        addRenderableWidget(Button.builder(Component.literal("Vanilla presets")) {
            keepEdits()
            val defaults = Config.defaultPresets()
            presets.forEachIndexed { i, draft ->
                val preset = defaults.getOrNull(i)
                draft.name = preset?.name ?: ""
                draft.radius = preset?.let { Shape.number(it.radius) } ?: ""
                if (preset != null) draft.colour = preset.colour
            }
            rebuildWidgets()
        }.bounds(left, y, WIDTH, ROW)
            .tooltip(Tooltip.create(Component.literal("Back to vanilla's despawn distances: instant despawn beyond 128 blocks, no despawning within 32.")))
            .build())
    }

    /** A slider from 0 to 1, showing [text] of its value. */
    internal class Slider(
        x: Int, y: Int, width: Int, initial: Double, private val text: (Double) -> String, explanation: String,
        private val onChange: (Double) -> Unit,
    ) : AbstractSliderButton(x, y, width, ROW, Component.empty(), initial) {
        init {
            updateMessage()
            setTooltip(Tooltip.create(Component.literal(explanation)))
        }

        override fun updateMessage() {
            message = Component.literal(text(value))
        }

        override fun applyValue() = onChange(value)
    }

    private fun keepEdits() {
        // The preset boxes only exist while the Shapes tab is showing.
        if (nameBoxes.size != presets.size) return
        presets.forEachIndexed { i, draft ->
            draft.name = nameBoxes[i].value
            draft.radius = radiusBoxes[i].value
        }
    }

    private fun save() {
        keepEdits()
        Config.showOnWorldMap = showOnWorldMap
        Config.showOnMinimap = showOnMinimap
        Config.showLabels = showLabels
        Config.enabled = enabled
        Config.showPanel = showPanel
        Config.thicknessScale = thicknessScale
        Config.showMiniHud = showMiniHud
        Config.miniHudIncludeDisabled = miniHudIncludeDisabled
        Config.hideInMiniHud = hideInMiniHud
        Config.fillOpacity = fillOpacity
        Config.beaconBeams = beaconBeams
        Config.presets = presets.mapNotNull { draft ->
            val radius = draft.radius.trim().toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            if (draft.name.isBlank()) return@mapNotNull null
            Config.Preset(draft.name.trim(), radius, draft.colour)
        }
        Config.save()
    }

    override fun onClose() {
        save()
        minecraft.gui.setScreen(parent)
    }


    private fun field(x: Int, y: Int, width: Int, value: String, hint: String, maxLength: Int): EditBox {
        val box = EditBox(font, x, y, width, ROW, Component.literal(hint))
        box.setMaxLength(maxLength)
        box.setValue(value)
        box.setHint(Component.literal(hint))
        return addRenderableWidget(box)
    }

    internal companion object {
        const val WIDTH = 300
        const val ROW = 20
        const val GAP = 2

        /** An on/off button with a tooltip. */
        fun toggle(x: Int, y: Int, width: Int, name: String, value: Boolean, explanation: String, onChange: (Boolean) -> Unit): CycleButton<Boolean> =
            CycleButton.onOffBuilder(value).create(x, y, width, ROW, Component.literal(name)) { _, on -> onChange(on) }
                .also { it.setTooltip(Tooltip.create(Component.literal(explanation))) }

        fun toSlider(value: Float, min: Float, max: Float): Double = ((value - min) / (max - min)).toDouble()

        /** The slider's value between [min] and [max], snapped to [step]. */
        fun lerp(slider: Double, min: Float, max: Float, step: Float): Float =
            ((min + slider.toFloat() * (max - min)) / step).roundToInt() * step
    }
}
