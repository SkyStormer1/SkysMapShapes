package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.MiniHudGeometry
import com.skystormer.skysmapshapes.Shape
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
 * Asks before moving a shape into MiniHUD, and how it should stand up in the world: a circle as
 * a cylinder, sphere or cone, a square as a prism or pyramid, and so on. From above they all look
 * like the shape on the map. [move] is given the form picked and does the moving.
 */
internal class MoveToMiniHudScreen(
    private val parent: Screen?,
    private val shape: Shape,
    private val y: Int,
    private val move: (MiniHudGeometry.Form) -> Unit,
) : Screen(Component.literal("Move ${shape.name} into MiniHUD?")) {

    private val forms = MiniHudGeometry.forms(shape.type)
    private var form = forms.first()

    private val left get() = width / 2 - WIDTH / 2

    override fun init() {
        var top = maxOf(6, (height - 190) / 2)
        addRenderableWidget(StringWidget(left, top, WIDTH, font.lineHeight, title, font))
        top += font.lineHeight + GAP * 3

        val text = MultiLineTextWidget(left, top, Component.literal(
            "It becomes an ordinary MiniHUD shape, shown in the world as well as on the map, and edited in MiniHUD from then on. " +
                (if (Dimensions.ofPlayer() == shape.dimension) "It is put at your height ($y)"
                else "You are not in the ${Dimensions.name(shape.dimension)}, so it goes into MiniHUD's file for it, at height $y, and MiniHUD has it when you go there. It is put there") +
                ", and is taken off this mod's own map so it is not drawn twice."
        ).withStyle { it.withColor(0xDDDDDD) }, font).setMaxWidth(WIDTH)
        addRenderableWidget(text)
        top += text.height + GAP * 4

        // Only a choice where MiniHUD has more than one: a rectangle is always a box.
        addRenderableWidget(
            CycleButton.builder<MiniHudGeometry.Form>({ Component.literal(MiniHudGeometry.formName(shape.type, it)) }, form)
                .withValues(forms)
                .create(left, top, WIDTH, ROW, Component.literal("In the world, as")) { _, value ->
                    form = value
                    // How big it draws depends on the form, so the warning below may change.
                    rebuildWidgets()
                }
                .also {
                    it.active = forms.size > 1
                    it.setTooltip(tip())
                }
        )
        top += ROW + GAP * 4

        // Too big for MiniHUD: said, and Move it greyed out. Big enough to slow the game: said.
        val tooBig = MiniHudGeometry.whyTooBig(shape, form)
        val warning = tooBig ?: MiniHudGeometry.lagWarning(shape, form)
        if (warning != null) {
            val note = MultiLineTextWidget(left, top, Component.literal(warning)
                .withStyle { it.withColor(if (tooBig != null) 0xFF6060 else 0xFFD040) }, font).setMaxWidth(WIDTH)
            addRenderableWidget(note)
            top += note.height + GAP * 4
        }

        val half = (WIDTH - GAP) / 2
        addRenderableWidget(Button.builder(Component.literal(if (tooBig == null && warning != null) "Move it anyway" else "Move it")) { move(form) }
            .bounds(left, top, half, ROW).build().also { it.active = tooBig == null })
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
            .bounds(left + half + GAP, top, WIDTH - half - GAP, ROW).build())
    }

    private fun tip(): Tooltip = Tooltip.create(Component.literal(
        MiniHudGeometry.formTip(form) +
            if (forms.size > 1) "\nFrom above it looks the same on the map whichever you pick."
            else "\nMiniHUD has only this for a ${shape.type.title.lowercase()}."
    ))

    /** A dark panel behind it, so it reads over the map or the world. */
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
