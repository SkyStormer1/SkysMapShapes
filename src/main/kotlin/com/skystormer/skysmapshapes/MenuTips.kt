package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.DockPanel
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import xaero.lib.client.gui.widget.dropdown.DropDownWidget
import xaero.map.gui.IRightClickableElement
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu
import xaero.map.gui.dropdown.rightclick.RightClickOption
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Hover text for the lines this mod adds to Xaero's right-click menus.
 *
 * Xaero's own options carry a label and whether they can be clicked, and nothing else, so a line
 * that is greyed out cannot say why. Ours carry their hover text with them ([TipOption]), and this
 * draws it: the menu is rendered with the same mouse position as the map screen, Xaero is asked
 * which of its lines the mouse is on, and the tooltip goes on top of the menu afterwards.
 *
 * Everything here is optional. If a future Xaero changes how its menus work, the menu still works
 * and only the hover text goes missing, with one line in the log to say so.
 */
object MenuTips {

    /** A menu line of ours, with the hover text it shows. */
    class TipOption(
        name: String,
        index: Int,
        target: IRightClickableElement,
        /** Shown while the mouse is over this line; lines are split on `\n`. */
        val tip: String?,
        private val action: (Screen) -> Unit,
    ) : RightClickOption(name, index, target) {
        override fun onAction(screen: Screen) = action(screen)
    }

    fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, menu: GuiRightClickMenu?) {
        if (menu == null || broken) return
        try {
            val options = optionsOf(menu) ?: return
            val line = hoveredLine(menu, mouseX, mouseY) ?: return
            val tip = (options.getOrNull(line) as? TipOption)?.tip ?: return
            // Wrapped and kept on screen, which vanilla's tooltip is not at a big GUI scale.
            DockPanel.tooltip(graphics, tip, mouseX, mouseY)
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not put hover text on Xaero's right-click menu", e)
        }
    }

    /** The menu's own lines, in the order it draws them. */
    @Suppress("UNCHECKED_CAST")
    private fun optionsOf(menu: GuiRightClickMenu): List<RightClickOption>? =
        (hooks.actionOptions.get(menu) as? List<RightClickOption>)

    /**
     * Which line the mouse is on, from Xaero's own reckoning, so that scrolling and any change to
     * how the menu is laid out are its business rather than ours.
     */
    private fun hoveredLine(menu: GuiRightClickMenu, mouseX: Int, mouseY: Int): Int? {
        val height = Minecraft.getInstance().gui.screen()?.height ?: return null
        val limit = hooks.optionLimit.invoke(menu, height) as Int
        val scrolling = hooks.scrolling.invoke(menu, limit) as Boolean
        val line = hooks.getHoveredId.invoke(menu, mouseX, mouseY, scrolling, limit) as Int
        return line.takeIf { it >= 0 }
    }

    /** Xaero's menu internals, looked up on first use, inside [draw]'s guard, so a missing one is caught there. */
    private class Hooks {
        private val dropDown: Class<*> = DropDownWidget::class.java

        val actionOptions: Field =
            GuiRightClickMenu::class.java.getDeclaredField("actionOptions").apply { isAccessible = true }

        val optionLimit: Method =
            dropDown.getDeclaredMethod("optionLimit", Int::class.javaPrimitiveType).apply { isAccessible = true }

        val scrolling: Method =
            dropDown.getDeclaredMethod("scrolling", Int::class.javaPrimitiveType).apply { isAccessible = true }

        val getHoveredId: Method = dropDown.getDeclaredMethod(
            "getHoveredId",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
    }

    private val hooks: Hooks by lazy { Hooks() }

    private var broken = false
}
