package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.Log
import com.skystormer.skysmapshapes.MapCamera
import com.skystormer.skysmapshapes.MapMenus
import com.skystormer.skysmapshapes.MapShape
import com.skystormer.skysmapshapes.MenuTips
import com.skystormer.skysmapshapes.MiniHudShape
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.ShapeStore
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import xaero.map.gui.IRightClickableElement
import xaero.map.gui.dropdown.rightclick.RightClickOption

/**
 * The Shapes panel on Xaero's world map, styled like Sky's Structure Map's legend: one line per
 * shape in the dimension the map is showing, this mod's and MiniHUD's, with its size.
 *
 * - Clicking a line hides or shows that shape on the map; right-clicking it opens the same menu
 *   as right-clicking the shape on the map (plus Go to), as a small popup of its own.
 * - Hide in the title hides every shape showing here and remembers which, so clicking it again
 *   brings back only those: shapes hidden one by one stay hidden. It is lit while nothing shows.
 *   With nothing remembered, it shows every shape. The Hide all: MiniHUD setting says whether
 *   MiniHUD's are switched off and on in MiniHUD too.
 * - Set opens the settings.
 *
 * Moving, folding, docking with the other mods' panels, the grips and the size are [DockPanel]'s.
 */
object ShapesPanel {

    private const val ROW = DockPanel.ROW
    private const val BACKGROUND = 0x70000000
    private const val HOVER = 0x30FFFFFF
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val OFF = 0xFF9A9A9A.toInt()
    private const val COUNT = 0xFFD0D0D0.toInt()
    private const val MINIHUD = 0xFF9FD0FF.toInt()
    private const val MINIHUD_OFF = 0xFFE3B46A.toInt()

    private const val TITLE = "Shapes"
    private const val HIDE = "Hide"
    private const val SET = "Set"
    private const val TOOLTIP_WIDTH = 190
    /** Names longer than this are cut short with "…", so one long name cannot make the panel huge. */
    private const val NAME_WIDTH = 130

    private const val MENU_ROW = 12
    private const val MENU_PAD = 4
    private const val ESCAPE = 256

    private var popup: Popup? = null

    fun addTo(screen: Screen) {
        if (!Config.enabled || !Config.showPanel) return
        val panel = Panel(screen)
        popup = null
        Screens.getWidgets(screen).add(panel)

        // Xaero zooms on the wheel before any widget hears of it; over the panel, it scrolls instead.
        ScreenMouseEvents.allowMouseScroll(screen).register { _, mouseX, mouseY, _, amount ->
            if (popup == null && panel.visible && panel.isMouseOver(mouseX, mouseY)) {
                panel.scroll(amount)
                false
            } else true
        }
        // The popup takes every click while it is open; a right-click on the panel opens it.
        ScreenMouseEvents.allowMouseClick(screen).register { s, event ->
            popup?.let { open ->
                open.click(s, event.x(), event.y())
                return@register false
            }
            if (event.button() == 1 && panel.visible && panel.isMouseOver(event.x(), event.y())) {
                panel.shapeAt(event.x(), event.y())?.let { popup = Popup.of(s, it, event.x().toInt(), event.y().toInt()) }
                return@register false
            }
            true
        }
        ScreenKeyboardEvents.allowKeyPress(screen).register { _, event ->
            if (popup != null && event.key() == ESCAPE) {
                popup = null
                false
            } else true
        }
        // Drawn after everything else on the screen, so no panel or map element covers it.
        ScreenEvents.afterExtract(screen).register { s, graphics, mouseX, mouseY, _ ->
            popup?.draw(s, graphics, mouseX, mouseY)
        }
        ScreenEvents.remove(screen).register { popup = null }
    }

    /** The shapes the panel lists: the map's dimension, by name. */
    private fun shapes(): List<MapShape> {
        val dimension = Dimensions.ofMap() ?: return emptyList()
        return (ShapeStore.inDimension(dimension) + MiniHudShapes.inDimension(dimension))
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun font() = Minecraft.getInstance().font

    private fun tooltip(graphics: GuiGraphicsExtractor, text: String, mouseX: Int, mouseY: Int) {
        DockPanel.tooltip(graphics, text, mouseX, mouseY, TOOLTIP_WIDTH)
    }

    private fun shortName(shape: MapShape): String {
        val font = font()
        val name = shape.name
        if (font.width(name) <= NAME_WIDTH) return name
        return font.plainSubstrByWidth(name, NAME_WIDTH - font.width("…")) + "…"
    }

    /** "MiniHUD" after a MiniHUD shape's name, or "MiniHUD off" while it is switched off there. */
    private fun tag(shape: MapShape): String? = (shape as? MiniHudShape)?.let { if (it.enabledInMiniHud) "MiniHUD" else "MiniHUD off" }

    class Panel(screen: Screen) : DockPanel(screen, "skysmapshapes:panel", TITLE) {

        private var offset = 0
        override var scrollOffset: Int
            get() = offset
            set(value) { offset = value }
        private var dimension: String? = null

        override fun savedLeft(width: Int) = screen.width - Config.panelRight - width
        override fun saveLeft(left: Int, width: Int) { Config.panelRight = screen.width - left - width }
        override var savedWidth: Int
            get() = Config.panelWidth
            set(value) { Config.panelWidth = value }
        override var savedTop: Int
            get() = Config.panelTop
            set(value) { Config.panelTop = value }
        override var savedRows: Int
            get() = Config.panelRows
            set(value) { Config.panelRows = value }
        override var savedOpen: Boolean
            get() = Config.panelOpen
            set(value) { Config.panelOpen = value }
        override var savedScale: Float
            get() = Config.panelScale
            set(value) { Config.panelScale = value }
        override var savedExtra: Int
            get() = Config.panelExtra
            set(value) { Config.panelExtra = value }
        override var savedUnder: String
            get() = Config.panelUnder
            set(value) { Config.panelUnder = value }
        override val maxScale get() = Config.panelMaxScale
        override fun save() = Config.save()

        override val background = BACKGROUND
        override val listTop = ROW + 1

        /** The list, from the top again whenever the map changes dimension. */
        private val listed: List<MapShape>
            get() {
                val shown = Dimensions.ofMap()
                if (shown != dimension) {
                    dimension = shown
                    offset = 0
                }
                return shapes()
            }

        override fun itemCount() = maxOf(1, listed.size)

        private fun switchWidth(label: String) = font().width(label) + 4

        override fun naturalWidth(): Int {
            val font = font()
            val header = 3 + font.width("- $TITLE") + 8 + switchWidth(HIDE) + 2 + switchWidth(SET) + 2
            val lines = listed.maxOfOrNull { shape ->
                13 + font.width(shortName(shape)) + (tag(shape)?.let { 4 + font.width(it) } ?: 0) + 10 +
                    font.width(shape.describeSizeShort()) + 4 + DockPanel.BAR + 3
            } ?: font.width("No shapes here yet") + 8
            return maxOf(header, lines, 110)
        }

        override fun afterLayout() {
            offset = offset.coerceIn(0, maxOf(0, listed.size - shownRows))
        }

        fun scroll(amount: Double) {
            if (!open) return
            offset = (offset - Math.signum(amount).toInt()).coerceIn(0, maxOf(0, listed.size - shownRows))
        }

        /** The shape on the line under the mouse, if any. */
        fun shapeAt(mouseX: Double, mouseY: Double): MapShape? {
            if (!open) return null
            val ly = (mouseY - y) / scale
            val row = Math.floor((ly - listTop) / ROW).toInt()
            if (row !in 0 until shownRows) return null
            return listed.getOrNull(row + offset)
        }

        private val setLeft get() = baseWidth - 2 - switchWidth(SET)
        private val hideLeft get() = setLeft - 2 - switchWidth(HIDE)

        private fun over(lx: Double, left: Int, label: String) = lx >= left && lx < left + switchWidth(label)

        /** Lit while nothing in the list shows on the map. */
        private fun hiding(list: List<MapShape>) = list.isNotEmpty() && list.none { it.visible }

        private fun hideTip(list: List<MapShape>): String {
            val inMiniHud = if (Config.hideInMiniHud) " MiniHUD's are switched off in MiniHUD too (Hide all: MiniHUD in the settings)."
            else " MiniHUD's are only hidden on the map (Hide all: MiniHUD in the settings)."
            val remembered = ShapeStore.hiddenByHideAll(list)
            return when {
                list.isEmpty() -> "No shapes in this dimension yet. Right-click the map to add one."
                list.any { it.visible } -> "Hide every shape showing here, and remember which, so clicking again brings back only those. Shapes you hid yourself stay hidden.$inMiniHud"
                remembered > 0 -> "Bring back the $remembered shape${if (remembered == 1) "" else "s"} Hide hid here, and no others."
                else -> "Show every shape in this dimension" + if (Config.hideInMiniHud) ", switching MiniHUD's back on in MiniHUD too." else "."
            }
        }

        override fun drawLocal(graphics: GuiGraphicsExtractor, lx: Int, ly: Int, mouseX: Int, mouseY: Int, idle: Boolean, partialTick: Float) {
            val list = listed
            val font = font()
            val inside = lx in 0 until baseWidth
            val mx = lx.toDouble()
            val menuOpen = popup != null

            val overHeader = inside && ly in 0 until ROW
            val overHide = overHeader && over(mx, hideLeft, HIDE)
            val overSet = overHeader && over(mx, setLeft, SET)
            if (overHeader && lx < hideLeft - 1) graphics.fill(0, 0, hideLeft - 1, ROW, HOVER)
            graphics.text(font, if (open) "- $TITLE" else "+ $TITLE", 3, 2, WHITE, false)
            switch(graphics, HIDE, hideLeft, hiding(list), overHide)
            switch(graphics, SET, setLeft, false, overSet)
            if (idle && !menuOpen) when {
                overHide -> tooltip(graphics, hideTip(list), mouseX, mouseY)
                overSet -> tooltip(graphics, "Settings: where shapes show, MiniHUD, presets", mouseX, mouseY)
                overHeader -> tooltip(graphics, "Click to fold the panel away or open it. Drag to move it, or up under another panel to dock it there.", mouseX, mouseY)
            }
            if (!open) return

            if (list.isEmpty()) {
                graphics.text(font, "No shapes here yet", 3, listTop + 2, OFF, false)
                return
            }
            for (i in 0 until shownRows) {
                val shape = list.getOrNull(i + offset) ?: break
                val top = listTop + i * ROW
                val shown = shape.visible
                val over = idle && !menuOpen && inside && ly >= top && ly < top + ROW
                if (over) graphics.fill(0, top, baseWidth, top + ROW, HOVER)
                val colour = shape.colour or 0xFF000000.toInt()
                val iconColour = if (shown) colour else (colour and 0xFFFFFF) or 0x60000000
                if (shape.fill) graphics.fill(3, top + 2, 10, top + 9, iconColour) else graphics.outline(3, top + 2, 7, 7, iconColour)
                val name = shortName(shape)
                graphics.text(font, name, 13, top + 2, if (shown) WHITE else OFF, false)
                tag(shape)?.let {
                    val on = (shape as MiniHudShape).enabledInMiniHud
                    graphics.text(font, it, 13 + font.width(name) + 4, top + 2, if (!shown) OFF else if (on) MINIHUD else MINIHUD_OFF, false)
                }
                val size = shape.describeSizeShort()
                graphics.text(font, size, baseWidth - 3 - DockPanel.BAR - 3 - font.width(size), top + 2, if (shown) COUNT else OFF, false)
                if (over) tooltip(graphics, rowTip(shape), mouseX, mouseY)
            }
        }

        private fun rowTip(shape: MapShape): String {
            val where = "${shape.describeSize()}. ${shape.describePosition()}."
            return if (shape is MiniHudShape)
                "${shape.name}\n$where\nOn the map: ${if (shape.visible) "shown" else "hidden"}. In MiniHUD (the world): ${if (shape.enabledInMiniHud) "on" else "off"}.\n" +
                    "Click to ${if (shape.visible) "hide" else "show"} it on the map. Right-click to choose where it shows, or delete it."
            else "${shape.name}\n$where\nClick to ${if (shape.visible) "hide" else "show"} it on the map. Right-click to edit, share or delete it."
        }

        private fun switch(graphics: GuiGraphicsExtractor, label: String, left: Int, on: Boolean, hovered: Boolean) {
            graphics.fill(left, 1, left + switchWidth(label), ROW - 1, if (on) 0x60FFFFFF else if (hovered) 0x30FFFFFF else 0x20FFFFFF)
            graphics.text(font(), label, left + 2, 2, if (on || label == SET) WHITE else OFF, false)
        }

        override fun clickLocal(lx: Double, ly: Double): Click {
            if (ly < ROW) {
                when {
                    over(lx, setLeft, SET) -> Minecraft.getInstance().gui.setScreen(ConfigScreen(screen))
                    over(lx, hideLeft, HIDE) -> toggleHide()
                    // The title: a drag moves the panel, a click without one folds it (on release).
                    else -> return Click.MOVE
                }
                return Click.DONE
            }
            val row = ((ly - listTop) / ROW).toInt()
            listed.getOrNull(row + offset)?.takeIf { row in 0 until shownRows }?.let { shape ->
                ShapeStore.setVisible(shape, !shape.visible)
            }
            return Click.DONE
        }

        private fun toggleHide() {
            val list = listed
            if (list.any { it.visible }) ShapeStore.hideAll(list.filter { it.visible }, Config.hideInMiniHud)
            else ShapeStore.showAll(list, Config.hideInMiniHud)
        }
    }

    /**
     * A shape's menu, opened from its line: Go to, then the options its right-click on the map
     * has. Xaero's options are reused as they are; only the drawing and the clicks are ours.
     */
    private class Popup(
        private val shape: MapShape,
        private val options: List<RightClickOption>,
        private val left: Int,
        private val top: Int,
        private val width: Int,
    ) {
        private val height get() = MENU_ROW * (options.size + 1) + 2

        private fun lineAt(mouseX: Double, mouseY: Double): Int? {
            if (mouseX < left || mouseX >= left + width || mouseY < top + MENU_ROW || mouseY >= top + height - 1) return null
            return ((mouseY - top - MENU_ROW) / MENU_ROW).toInt().takeIf { it in options.indices }
        }

        fun draw(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
            val font = font()
            graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, 0xFF3A1A6A.toInt())
            graphics.fill(left, top, left + width, top + height, 0xF00A0A0C.toInt())
            graphics.fill(left, top, left + 3, top + MENU_ROW, shape.colour or 0xFF000000.toInt())
            graphics.text(font, shape.name, left + 3 + MENU_PAD, top + 2, OFF, false)
            val hovered = lineAt(mouseX.toDouble(), mouseY.toDouble())
            options.forEachIndexed { i, option ->
                val y = top + MENU_ROW * (i + 1)
                if (i == hovered && option.isActive) graphics.fill(left, y, left + width, y + MENU_ROW, HOVER)
                graphics.text(font, option.displayName, left + MENU_PAD, y + 2, if (option.isActive) WHITE else 0xFF707070.toInt(), false)
            }
            val tip = hovered?.let { (options[it] as? MenuTips.TipOption)?.tip }
            if (tip != null) graphics.setComponentTooltipForNextFrame(font, tip.split('\n').map { Component.literal(it) }, mouseX, mouseY)
        }

        /** Runs the line clicked, if it can be; any click closes the menu. */
        fun click(screen: Screen, mouseX: Double, mouseY: Double) {
            val line = lineAt(mouseX, mouseY)
            if (line == null && mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + MENU_ROW) return
            popup = null
            val option = line?.let { options[it] } ?: return
            if (!option.isActive) return
            try {
                option.onAction(screen)
            } catch (e: Throwable) {
                Log.error("Shapes panel menu: \"${option.displayName.string}\" failed", e)
            }
        }

        companion object {
            fun of(screen: Screen, shape: MapShape, mouseX: Int, mouseY: Int): Popup? {
                val options = ArrayList<RightClickOption>()
                val target = object : IRightClickableElement {
                    override fun getRightClickOptions() = options
                    override fun isRightClickValid() = true
                    override fun getRightClickTitleBackgroundColor() = shape.colour
                }
                try {
                    options.add(MapMenus.option("Go to", options.size, target, tip = "Move the map to it, and show it if it is hidden.") { s ->
                        if (!shape.visible) ShapeStore.setVisible(shape, true)
                        val b = shape.bounds
                        (s as? MapCamera)?.skysmapshapesCentreOn(Math.floor(b.centreX).toInt(), Math.floor(b.centreZ).toInt())
                    })
                    if (shape is MiniHudShape) {
                        options.add(MapMenus.option("Where it shows…", options.size, target,
                            tip = "On the map and in MiniHUD (the world), one switch for each.") { s -> MapMenus.open(MiniHudVisibilityScreen(s, shape)) })
                    }
                    MapMenus.addShapeOptions(options, target, shape)
                } catch (e: Throwable) {
                    Log.error("Could not build the menu for ${shape.name}", e)
                    return null
                }
                val font = font()
                val width = maxOf(font.width(shape.name) + 3, options.maxOf { font.width(it.displayName) }) + MENU_PAD * 2
                val height = MENU_ROW * (options.size + 1) + 2
                val left = mouseX.coerceIn(1, maxOf(1, screen.width - width - 1))
                val top = mouseY.coerceIn(1, maxOf(1, screen.height - height - 1))
                return Popup(shape, options, left, top, width)
            }
        }
    }
}
