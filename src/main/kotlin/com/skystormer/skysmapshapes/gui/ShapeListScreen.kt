package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.Log
import com.skystormer.skysmapshapes.MapCamera
import com.skystormer.skysmapshapes.MapMenus
import com.skystormer.skysmapshapes.MapShape
import com.skystormer.skysmapshapes.MapShapes
import com.skystormer.skysmapshapes.MiniHudShape
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.Shape
import com.skystormer.skysmapshapes.ShapeStore
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import xaero.map.WorldMapSession
import xaero.map.gui.GuiMap
import kotlin.math.hypot

/**
 * Every shape on this server, like Xaero's waypoint list: search by name, this dimension or all,
 * show or hide each (hidden ones stay listed, greyed, so they are easy to bring back), jump the
 * world map to one, edit or delete it. MiniHUD's can be deleted from here in any dimension.
 *
 * Opened from the settings or a key you can bind in Controls. The mouse wheel turns the pages.
 */
class ShapeListScreen(parent: Screen?) : PanelScreen(parent, Component.literal("Shapes")) {

    private var query = ""
    private var allDimensions = false

    /** The list itself, replaced on every change without touching the search box. */
    private val list = PagedRows({ addRenderableWidget(it) }, { removeWidget(it) })
    private lateinit var showAll: Button

    override val columnWidth: Int get() = minOf(MAX_WIDTH, width - 16)
    override val margin = 6
    private val listWidth get() = columnWidth

    override fun init() {
        var y = 6
        val world = ShapeStore.worldName
        addRenderableWidget(StringWidget(left, y, listWidth, font.lineHeight,
            Component.literal(if (world == null) "Shapes: join a world first" else "Shapes on $world"), font))
        y += font.lineHeight + GAP * 2

        // Search, and which dimension.
        val half = (listWidth - GAP) / 2
        val search = EditBox(font, left, y, half, ROW, Component.literal("Search"))
        search.setHint(Component.literal("Search names…"))
        search.setMaxLength(64)
        search.value = query
        search.setResponder { query = it; list.page = 0; refreshRows() }
        addRenderableWidget(search)
        addRenderableWidget(
            CycleButton.builder<Boolean>({ Component.literal(if (it) "All dimensions" else "This dimension") }, allDimensions)
                .withValues(listOf(false, true))
                .displayOnlyValue()
                .create(left + half + GAP, y, listWidth - half - GAP, ROW, Component.literal("Dimension")) { _, all -> allDimensions = all; list.page = 0; refreshRows() }
        )
        y += ROW + GAP * 2

        list.top = y
        val footer = (ROW + GAP) * 2 + GAP * 2
        list.rows = ((height - list.top - footer - 4) / (ROW + GAP)).coerceAtLeast(2)
        y = list.top + list.rows * (ROW + GAP) + GAP

        val fifth = (listWidth - GAP * 4) / 5
        list.buttons(font, left, y, fifth) { refreshRows() }
        showAll = addRenderableWidget(Button.builder(Component.literal("Show all")) { showAll() }
            .bounds(left + (fifth + GAP) * 3, y, fifth, ROW).build())
        addRenderableWidget(Button.builder(Component.literal("Hide all")) { hideAll() }
            .bounds(left + (fifth + GAP) * 4, y, listWidth - (fifth + GAP) * 4, ROW)
            .tooltip(Tooltip.create(Component.literal(
                "Hide every shape in this list that is showing, and remember which, so Show all brings back only those." +
                    if (Config.hideInMiniHud) " MiniHUD's are switched off in MiniHUD too (see the settings)." else " MiniHUD's are hidden on the map only (see the settings)."
            ))).build())
        y += ROW + GAP

        addRenderableWidget(Button.builder(Component.literal("Add a shape where I am")) { addHere() }
            .bounds(left, y, half, ROW).build()).active = ShapeStore.isOpen && minecraft.player != null
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(left + half + GAP, y, listWidth - half - GAP, ROW).build())

        refreshRows()
    }

    /** The shapes this list shows: matching the search, in the chosen dimensions. */
    private fun shapes(): List<MapShape> {
        val here = currentDimension()
        val words = query.trim().lowercase()
        return MapShapes.all()
            .filter { allDimensions || it.dimension == here }
            .filter { words.isEmpty() || it.name.lowercase().contains(words) }
            .sortedWith(compareBy<MapShape>({ it.dimension != here }, { it.dimension }, { it.name.lowercase() }))
    }

    private fun refreshRows() {
        val shapes = shapes()
        val shown = list.pageOf(shapes)
        val remembered = ShapeStore.hiddenByHideAll(shapes)
        showAll.setTooltip(Tooltip.create(Component.literal(
            if (remembered > 0) "Bring back the $remembered shape${if (remembered == 1) "" else "s"} Hide all hid here, and no others. Press it again after to show every shape."
            else "Show every shape in this list" + if (Config.hideInMiniHud) ", switching MiniHUD's back on in MiniHUD too." else "."
        )))

        if (shapes.isEmpty()) {
            val text = when {
                MapShapes.all().isEmpty() -> "No shapes yet: right-click the world map to add one."
                query.isNotBlank() -> "No shapes match \"$query\"."
                else -> "No shapes in this dimension. Choose All dimensions to see the rest."
            }
            row(StringWidget(left, list.top + 6, listWidth, font.lineHeight, Component.literal(text), font))
            return
        }

        // From the world map, Go to moves it; from anywhere else, it opens the map there. Either
        // way only for shapes in the dimension that map shows.
        val mapDimension = if (parent is MapCamera) Dimensions.ofMap() else Dimensions.ofPlayer()
        val buttonWidth = 40
        val buttons = 4
        val textWidth = listWidth - (buttonWidth + GAP) * buttons
        var y = list.top
        for (shape in shown) {
            val name = Component.literal(shape.name).withStyle {
                if (shape.visible) it.withColor(shape.colour and 0xFFFFFF) else it.withColor(HIDDEN).withItalic(true)
            }
            val details = buildString {
                if (shape.fromMiniHud) append("MiniHUD · ")
                if (!shape.visible) append("hidden · ")
                append(shape.describeSizeShort())
                if (allDimensions) append(" · ").append(Dimensions.name(shape.dimension))
                distanceTo(shape)?.let { append(" · ").append(it).append(" away") }
            }
            val line = name.copy().append(Component.literal("  $details").withStyle { it.withColor(DETAILS) })
            val text = StringWidget(left, y + 6, textWidth - GAP, font.lineHeight, line, font)
            text.setTooltip(Tooltip.create(Component.literal(
                "${shape.name}\n${shape.describeSize()}\n${shape.describePosition()}\n${Dimensions.name(shape.dimension)}" +
                    (if (shape is Shape) "\nThickness ${Thickness.format(shape.lineWidth)}" else "\nFrom MiniHUD: Edit opens it in MiniHUD")
            )))
            row(text)

            var x = left + textWidth
            // One of MiniHUD's can be on the map, in MiniHUD (the world), both or neither, so it gets a choice.
            val hide = if (shape is MiniHudShape) {
                Button.builder(Component.literal(if (shape.visible) "Hide…" else "Show…")) {
                    minecraft.gui.setScreen(MiniHudVisibilityScreen(this, shape))
                }.bounds(x, y, buttonWidth, ROW).tooltip(Tooltip.create(Component.literal(
                    "On the map: ${if (shape.visible) "shown" else "hidden"}. In MiniHUD: ${if (shape.enabledInMiniHud) "on" else "off"}.\nChoose where it shows."
                )))
            } else {
                Button.builder(Component.literal(if (shape.visible) "Hide" else "Show")) {
                    ShapeStore.setVisible(shape, !shape.visible)
                    refreshRows()
                }.bounds(x, y, buttonWidth, ROW)
            }
            row(hide.build())
            x += buttonWidth + GAP
            val go = Button.builder(Component.literal("Go to")) { goTo(shape) }.bounds(x, y, buttonWidth, ROW)
                .tooltip(Tooltip.create(Component.literal(
                    if (shape.dimension == mapDimension) "Show this shape on the world map."
                    else "This shape is in the ${Dimensions.name(shape.dimension)}: go there to see it on the map."
                )))
                .build()
            go.active = mapDimension != null && shape.dimension == mapDimension
            row(go)
            x += buttonWidth + GAP
            // MiniHUD's shapes are edited and deleted in MiniHUD; deleting one there removes it here.
            val own = shape as? Shape
            val miniHud = shape as? MiniHudShape
            val edit = Button.builder(Component.literal("Edit")) {
                own?.let { minecraft.gui.setScreen(ShapeEditScreen.forExisting(this, it)) }
                miniHud?.let { MiniHudShapes.openEditor(it, this) }
            }.bounds(x, y, buttonWidth, ROW)
            if (miniHud != null) edit.tooltip(Tooltip.create(Component.literal(
                if (miniHud.editable) "Open this shape in MiniHUD's Shape Editor."
                else "Go to the ${Dimensions.name(shape.dimension)} to edit it: MiniHUD only edits shapes in the dimension you are in."
            )))
            row(edit.build().also { it.active = own != null || miniHud?.editable == true })
            x += buttonWidth + GAP
            val delete = Button.builder(Component.literal("Delete")) {
                own?.let { MapMenus.confirmDelete(this, it) }
                miniHud?.let { MapMenus.confirmDelete(this, it) }
            }.bounds(x, y, buttonWidth, ROW)
            if (miniHud != null) delete.tooltip(Tooltip.create(Component.literal(
                if (miniHud.changeable) "Delete it from MiniHUD, which takes it off the map too." +
                    (if (miniHud.editable) "" else " It is in the ${Dimensions.name(shape.dimension)}, so it is taken out of MiniHUD's file for it.")
                else "MiniHUD's file for the ${Dimensions.name(shape.dimension)} could not be found."
            )))
            row(delete.build().also { it.active = own != null || miniHud?.changeable == true })
            y += ROW + GAP
        }
    }

    private fun row(widget: AbstractWidget) {
        list.add(widget)
    }

    private fun hideAll() {
        ShapeStore.hideAll(shapes(), Config.hideInMiniHud)
        refreshRows()
    }

    private fun showAll() {
        ShapeStore.showAll(shapes(), Config.hideInMiniHud)
        refreshRows()
    }

    private fun goTo(shape: MapShape) {
        val b = shape.bounds
        val x = Math.floor(b.centreX).toInt()
        val z = Math.floor(b.centreZ).toInt()
        if (!shape.visible) ShapeStore.setVisible(shape, true)
        if (parent is MapCamera) {
            parent.skysmapshapesCentreOn(x, z)
            onClose()
            return
        }
        // Opened from the settings or a key: open Xaero's world map, as its own key would, then glide to the shape.
        try {
            val player = minecraft.player ?: return
            val session = WorldMapSession.getCurrentSession() ?: return Log.warn("Go to: Xaero's world map has no session yet")
            val map = GuiMap(null, null, session.mapProcessor, player)
            (map as Any as? MapCamera)?.skysmapshapesCentreOn(x, z) ?: Log.warn("Go to: the world map hook is missing")
            minecraft.gui.setScreen(map)
        } catch (e: Throwable) {
            Log.error("Could not open the world map at ${shape.name}", e)
        }
    }

    private fun addHere() {
        val player = minecraft.player ?: return
        val dimension = Dimensions.ofPlayer() ?: return
        minecraft.gui.setScreen(ShapeEditScreen.forNew(this, dimension, player.blockX, player.blockZ, ""))
    }

    /** Whole blocks from you to the shape's centre, when you are in its dimension. */
    private fun distanceTo(shape: MapShape): String? {
        val player = minecraft.player ?: return null
        if (Dimensions.ofPlayer() != shape.dimension) return null
        val b = shape.bounds
        return hypot(b.centreX - player.x, b.centreZ - player.z).toInt().toString()
    }

    /** The world map's dimension if it is open behind this, else the one you are in. */
    private fun currentDimension(): String? =
        (if (parent is MapCamera) Dimensions.ofMap() else null) ?: Dimensions.ofPlayer()

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0) {
            list.page += if (scrollY < 0) 1 else -1
            refreshRows()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    companion object {
        private const val MAX_WIDTH = 420
        private const val DETAILS = 0xE0E0E0
        private const val HIDDEN = 0xB0B0B0
    }
}
