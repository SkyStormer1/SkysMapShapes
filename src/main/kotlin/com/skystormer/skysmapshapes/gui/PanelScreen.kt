package com.skystormer.skysmapshapes.gui

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * A screen laid out as one column, [columnWidth] wide and centred, over a dark panel so it reads
 * over the map or the world; closing it goes back to [parent]. The share, move-to-MiniHUD,
 * MiniHUD show-or-hide and shapes list screens are all this.
 */
abstract class PanelScreen(protected val parent: Screen?, title: Component) : Screen(title) {

    /** How wide the column is. */
    protected open val columnWidth: Int get() = WIDTH

    /** How far the dark panel reaches past the column on each side. */
    protected open val margin: Int = 8

    protected val left: Int get() = width / 2 - columnWidth / 2

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left - margin, 2, left + columnWidth + margin, height - 2, PANEL)
    }

    override fun onClose() {
        minecraft.gui.setScreen(parent)
    }

    companion object {
        const val WIDTH = 260
        const val ROW = 20
        const val GAP = 2
        private const val PANEL = 0xC0101010.toInt()
    }
}

/**
 * The paged part of a search list (the shapes list, the players to share with): [rows] lines a
 * page, with `<`, "page / pages" and `>` below them. Each refresh, [pageOf] clears the last page's
 * rows and gives this page's part of the list, and the screen adds its rows with [add].
 * [addWidget] and [removeWidget] are the screen's own, which only it can call.
 */
internal class PagedRows(
    private val addWidget: (AbstractWidget) -> Unit,
    private val removeWidget: (AbstractWidget) -> Unit,
) {
    var page = 0
    var rows = 1
    var top = 0

    private val shown = ArrayList<AbstractWidget>()
    private lateinit var label: StringWidget
    private lateinit var previous: Button
    private lateinit var next: Button

    /** Adds `<`, the page label and `>`, each [cell] wide, from [x] along [y]; [turned] redraws the rows. */
    fun buttons(font: Font, x: Int, y: Int, cell: Int, turned: () -> Unit) {
        previous = Button.builder(Component.literal("<")) { page--; turned() }.bounds(x, y, cell, PanelScreen.ROW).build().also(addWidget)
        label = StringWidget(x + cell + PanelScreen.GAP, y + 6, cell, font.lineHeight, Component.empty(), font).also(addWidget)
        next = Button.builder(Component.literal(">")) { page++; turned() }
            .bounds(x + (cell + PanelScreen.GAP) * 2, y, cell, PanelScreen.ROW).build().also(addWidget)
    }

    /** Takes the last page's rows away, keeps [page] in range for [items], and gives this page's items. */
    fun <T> pageOf(items: List<T>): List<T> {
        shown.forEach(removeWidget)
        shown.clear()
        val pages = maxOf(1, (items.size + rows - 1) / rows)
        page = page.coerceIn(0, pages - 1)
        label.message = Component.literal(if (items.isEmpty()) "" else "${page + 1} / $pages")
        previous.active = page > 0
        next.active = page < pages - 1
        return items.drop(page * rows).take(rows)
    }

    /** Adds one of this page's rows, to be taken away again on the next [pageOf]. */
    fun <W : AbstractWidget> add(widget: W): W {
        addWidget(widget)
        shown += widget
        return widget
    }
}
