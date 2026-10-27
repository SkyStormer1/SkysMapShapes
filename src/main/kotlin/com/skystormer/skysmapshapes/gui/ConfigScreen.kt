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
    private var beaconOutlines = Config.beaconOutlines
    private var beaconLimit = Config.beaconLimit
    private var beaconDistance = Config.beaconDistance
    private var hideGameBeams = Config.hideGameBeams
    private var beaconThroughWalls = Config.beaconThroughWalls
    private var beaconMinY = Config.beaconMinY
    private var beaconMaxY = Config.beaconMaxY
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
        beaconOutlines = false
        beaconLimit = Config.MAX_BEACON_LIMIT
        beaconDistance = Config.MAX_BEACON_DISTANCE
        hideGameBeams = false
        beaconThroughWalls = true
        beaconMinY = Config.LOWEST_Y
        beaconMaxY = Config.HIGHEST_Y
    }

    override fun content(top: Int) {
        nameBoxes.clear()
        radiusBoxes.clear()
        val left = width / 2 - panelWidth / 2
        var y = top

        // The tabs: the one showing is greyed out. The last takes what rounding leaves.
        val tabs = Page.entries.size
        val tabWidth = (panelWidth - GAP * (tabs - 1)) / tabs
        Page.entries.forEachIndexed { i, tab ->
            val x = left + i * (tabWidth + GAP)
            addRenderableWidget(Button.builder(Component.literal(tab.title)) { switchPage(tab) }
                .bounds(x, y, if (i < tabs - 1) tabWidth else panelWidth - (tabWidth + GAP) * i, ROW).build()
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

    /**
     * Settings still being tried out; all off as the mod comes. Grouped under headings: what is
     * drawn, how far out, and at what heights.
     */
    private fun experimentalPage(left: Int, top: Int) {
        var y = top
        val about = "Still being tried out: these may change or go in a later version."
        addRenderableWidget(MultiLineTextWidget(left, y, Component.literal(about), font).setMaxWidth(panelWidth))
        y += font.lineHeight * font.split(Component.literal(about), panelWidth).size + GAP * 3
        val half = (panelWidth - GAP) / 2
        val third = (panelWidth - GAP * 2) / 3

        y = heading(left, y, "Beacons: what is drawn")
        addRenderableWidget(toggle(left, y, third, "Beams", beaconBeams,
            "Draws a beam straight up from every beacon near you, lit or not.") { beaconBeams = it })
        addRenderableWidget(toggle(left + third + GAP, y, third, "Blocked", beaconOutlines,
            "Outlines in red every block in a beacon's way: the ones to break to light it. As in the game, only full solid " +
                "blocks stop it: glass, leaves, slabs, stairs, anvils, chains, honey and bedrock let it through, but tinted glass does not.") {
            beaconOutlines = it
        })
        // On means the game draws its beams; off hides them, so only this mod's show.
        addRenderableWidget(toggle(left + (third + GAP) * 2, y, panelWidth - (third + GAP) * 2, "Game beams", !hideGameBeams,
            "The game's own beacon beams. Off hides them, so only the beams above show, or none at all.") { hideGameBeams = !it })
        y += ROW + GAP
        addRenderableWidget(toggle(left, y, panelWidth, "Blocked: through walls", beaconThroughWalls,
            "On: the blocks in a beam's way are outlined through everything in front of them. Off: only where you can see them.") {
            beaconThroughWalls = it
        })
        y += ROW + GAP * 3

        y = heading(left, y, "Beacons: how far")
        val near = Config.MIN_BEACON_DISTANCE.toFloat()
        val far = Config.MAX_BEACON_DISTANCE.toFloat()
        addRenderableWidget(Slider(left, y, half, toSlider(beaconDistance.toFloat(), near, far),
            { "Distance: ${lerp(it, near, far, 1f).roundToInt()} chunks" },
            "How far out beacons are looked for, in chunks, as a circle around you. Never past the chunks the game has loaded (your render distance).") {
            beaconDistance = lerp(it, near, far, 1f).roundToInt()
        })
        val fewest = Config.MIN_BEACON_LIMIT.toFloat()
        val most = Config.MAX_BEACON_LIMIT.toFloat()
        addRenderableWidget(Slider(left + half + GAP, y, panelWidth - half - GAP, toSlider(beaconLimit.toFloat(), fewest, most),
            { "Up to ${lerp(it, fewest, most, BEACON_STEP).roundToInt()} beacons" },
            "How many beacons get a beam at most, the nearest to you first. More costs more to draw.") {
            beaconLimit = lerp(it, fewest, most, BEACON_STEP).roundToInt()
        })
        y += ROW + GAP * 3

        // From and to, side by side; each keeps clear of the other, so the range never turns inside out.
        y = heading(left, y, "Beacons: heights drawn")
        val low = Config.LOWEST_Y.toFloat()
        val high = Config.HIGHEST_Y.toFloat()
        addRenderableWidget(Slider(left, y, half, toSlider(beaconMinY.toFloat(), low, high),
            { "From Y ${lerp(it, low, high, 1f).roundToInt().coerceAtMost(beaconMaxY)}" },
            "The lowest height beams and the blocks in their way are drawn at. At 128, only what is above the Nether's roof shows.") {
            beaconMinY = lerp(it, low, high, 1f).roundToInt().coerceAtMost(beaconMaxY)
        })
        addRenderableWidget(Slider(left + half + GAP, y, panelWidth - half - GAP, toSlider(beaconMaxY.toFloat(), low, high),
            { "To Y ${lerp(it, low, high, 1f).roundToInt().coerceAtLeast(beaconMinY)}" },
            "The highest height beams and the blocks in their way are drawn at.") {
            beaconMaxY = lerp(it, low, high, 1f).roundToInt().coerceAtLeast(beaconMinY)
        })
    }

    /** A heading over a group of settings; returns the y under it. */
    private fun heading(left: Int, y: Int, text: String): Int {
        addRenderableWidget(StringWidget(left, y, panelWidth, font.lineHeight, Component.literal(text), font))
        return y + font.lineHeight + GAP * 2
    }

    /** Where shapes show, how they look, and the presets. */
    private fun shapesPage(left: Int, top: Int) {
        val third = (panelWidth - GAP * 2) / 3
        var y = top

        // The switch for the whole mod, the panel, and the way to the shapes list, first.
        addRenderableWidget(toggle(left, y, third, "Mod", enabled,
            "Off turns the whole mod off: no shapes drawn, no right-click options, no Shapes panel on the map. Your shapes are kept.") { enabled = it })
        addRenderableWidget(toggle(left + third + GAP, y, third, "Map panel", showPanel,
            "The Shapes panel on the world map: every shape in the dimension shown, to hide, show or right-click. It docks under Sky's Map Exposer's bar.") { showPanel = it })
        addRenderableWidget(Button.builder(Component.literal("Shapes list…")) {
            save()
            minecraft.gui.setScreen(ShapeListScreen(this))
        }.bounds(left + (third + GAP) * 2, y, panelWidth - (third + GAP) * 2, ROW)
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
            }.bounds(left, y, panelWidth, ROW)
                .tooltip(Tooltip.create(Component.literal(
                    "MiniHUD's light level overlay inside your shapes, Nether spawns by biome, and Easy Place spawn proofing with its overlay."
                ))).build())
            y += ROW + GAP
        }

        val half = (panelWidth - GAP) / 2
        addRenderableWidget(Slider(left, y, half, toSlider(thicknessScale, Config.MIN_SCALE, Config.MAX_SCALE),
            { "Thickness ${(lerp(it, Config.MIN_SCALE, Config.MAX_SCALE, 0.05f) * 100).roundToInt()}%" },
            "Makes every shape's outline thicker or thinner at once, on top of each shape's own thickness (set in its Edit screen). 100% draws them as set.") {
            thicknessScale = lerp(it, Config.MIN_SCALE, Config.MAX_SCALE, 0.05f)
        })
        addRenderableWidget(Slider(left + half + GAP, y, half, fillOpacity.toDouble(),
            { "Fill ${(lerp(it, 0f, 1f, 0.05f) * 100).roundToInt()}%" },
            "How strongly the inside of a filled shape is shaded.") { fillOpacity = lerp(it, 0f, 1f, 0.05f) })
        y += ROW + GAP * 3

        y = heading(left, y, "Presets: circles offered when adding a shape")

        val nameWidth = panelWidth / 2
        val radiusWidth = panelWidth / 6
        for (preset in presets) {
            nameBoxes.add(field(left, y, nameWidth, preset.name, "unused", Config.MAX_PRESET_NAME))
            radiusBoxes.add(field(left + nameWidth + GAP, y, radiusWidth, preset.radius, "radius", 10).also { box ->
                box.setTooltip(Tooltip.create(Component.literal(
                    "Radius in blocks, up to ${Shape.number(Shape.MAX_TYPED_SIZE)}. One shown in red is not kept.")))
                // Red while it is not a radius a shape can have, so it is clear it will not be kept.
                fun colour(text: String) = box.setTextColor(
                    if (text.isBlank() || presetRadius(text) != null) TEXT_COLOUR else BAD_COLOUR)
                colour(box.value)
                box.setResponder(::colour)
            })
            val colourLeft = left + nameWidth + radiusWidth + GAP * 2
            addRenderableWidget(
                CycleButton.builder<Int>({ Component.literal(Colours.name(it)).withStyle { s -> s.withColor(it and 0xFFFFFF) } }, preset.colour)
                    .withValues(Colours.entries.map { it.argb }.let { if (preset.colour in it) it else listOf(preset.colour) + it })
                    .displayOnlyValue()
                    .create(colourLeft, y, left + panelWidth - colourLeft, ROW, Component.literal("Colour")) { _, value -> preset.colour = value }
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
        }.bounds(left, y, panelWidth, ROW)
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
        Config.beaconOutlines = beaconOutlines
        Config.beaconLimit = beaconLimit
        Config.beaconDistance = beaconDistance
        Config.hideGameBeams = hideGameBeams
        Config.beaconThroughWalls = beaconThroughWalls
        Config.beaconMinY = beaconMinY
        Config.beaconMaxY = beaconMaxY
        Config.presets = presets.mapNotNull { draft ->
            val radius = presetRadius(draft.radius) ?: return@mapNotNull null
            if (draft.name.isBlank() || !Config.validPreset(draft.name.trim(), radius)) return@mapNotNull null
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
        private const val BEACON_STEP = 50f
        private const val TEXT_COLOUR = 0xFFE0E0E0.toInt()
        private const val BAD_COLOUR = 0xFFFF5555.toInt()

        /** A typed preset radius, when it is one a preset may have; null otherwise. */
        fun presetRadius(text: String): Double? =
            text.trim().toDoubleOrNull()?.takeIf { Config.validPreset("", it) }

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
