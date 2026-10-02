package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.MapMenus
import com.skystormer.skysmapshapes.MiniHudShape
import com.skystormer.skysmapshapes.MiniHudShapes
import com.skystormer.skysmapshapes.ShapeStore
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.MultiLineTextWidget
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * Where one of MiniHUD's shapes shows: on the map, in MiniHUD (the world), both or neither, as
 * two switches that each leave the other alone. Changes happen as they are clicked.
 */
class MiniHudVisibilityScreen(
    private val parent: Screen?,
    shape: MiniHudShape,
) : Screen(Component.literal("Show or hide ${shape.name}")) {

    private val id = shape.id
    private var shape = shape

    private val left get() = width / 2 - WIDTH / 2

    override fun init() {
        // Each change re-reads MiniHUD, so the shape is looked up again by its id.
        MiniHudShapes.all.firstOrNull { it.id == id }?.let { shape = it }
        var top = maxOf(6, (height - 130) / 2)
        addRenderableWidget(StringWidget(left, top, WIDTH, font.lineHeight, title, font))
        top += font.lineHeight + GAP * 3

        addRenderableWidget(
            CycleButton.onOffBuilder(shape.visible)
                .create(left, top, WIDTH, ROW, Component.literal("On the map")) { _, on ->
                    ShapeStore.setVisible(shape, on)
                    rebuildWidgets()
                }
                .also { it.setTooltip(Tooltip.create(Component.literal("Draws it on Xaero's maps or not. MiniHUD is left as it is."))) }
        )
        top += ROW + GAP

        addRenderableWidget(
            CycleButton.onOffBuilder(shape.enabledInMiniHud)
                .create(left, top, WIDTH, ROW, Component.literal("In MiniHUD (the world)")) { _, on ->
                    if (!ShapeStore.setInMiniHud(shape, on)) MapMenus.say("MiniHUD would not switch ${shape.name} ${if (on) "on" else "off"}; the log says why.")
                    rebuildWidgets()
                }
                .also {
                    it.active = shape.changeable
                    it.setTooltip(Tooltip.create(Component.literal(
                        if (!shape.changeable) "MiniHUD's file for the ${Dimensions.name(shape.dimension)} could not be found."
                        else "Switches it on or off in MiniHUD, as MiniHUD's own shape list does, so it shows in the world or not. The map is left as it is." +
                            if (shape.editable) "" else "\nIt is in the ${Dimensions.name(shape.dimension)}, so MiniHUD has the change when you next go there."
                    )))
                }
        )
        top += ROW + GAP * 3

        val note = MultiLineTextWidget(left, top, Component.literal(
            "Hide all and Show all in the Shapes list remember what they hid, and bring back only that."
        ).withStyle { it.withColor(0xBBBBBB) }, font).setMaxWidth(WIDTH)
        addRenderableWidget(note)
        top += note.height + GAP * 4

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE) { onClose() }.bounds(left, top, WIDTH, ROW).build())
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left - 8, 2, left + WIDTH + 8, height - 2, PANEL)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    private companion object {
        const val WIDTH = 260
        const val ROW = 20
        const val GAP = 2
        const val PANEL = 0xC0101010.toInt()
    }
}
