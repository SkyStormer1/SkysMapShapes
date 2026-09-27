package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.MapShape
import com.skystormer.skysmapshapes.ShapeShare
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

/**
 * Who to share a shape with: everyone in chat, or any number of players privately.
 *
 * A shared shape travels as an ordinary line of chat, which is how Xaero shares a waypoint too,
 * so a line sent to everyone is seen by everyone — people without the mod see plain text rather
 * than the add button. Picking players sends each of them a private message instead.
 */
class ShareScreen(private val parent: Screen?, private val shape: MapShape) :
    Screen(Component.literal("Share ${shape.name}")) {

    private val chosen = LinkedHashSet<String>()
    private var query = ""
    private var page = 0
    private var rows = 0
    private var rowsTop = 0

    private val rowWidgets = ArrayList<AbstractWidget>()
    private lateinit var pageLabel: StringWidget
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var send: Button

    private val left get() = width / 2 - WIDTH / 2

    override fun init() {
        var y = maxOf(6, (height - 280) / 2)
        addRenderableWidget(StringWidget(left, y, WIDTH, font.lineHeight, title, font))
        y += font.lineHeight + GAP
        addRenderableWidget(StringWidget(left, y, WIDTH, font.lineHeight,
            Component.literal("${shape.describeSize()} · ${Dimensions.name(shape.dimension)}")
                .withStyle { it.withColor(0xBBBBBB) }, font))
        y += font.lineHeight + GAP * 3

        addRenderableWidget(
            Button.builder(Component.literal("Everyone in chat")) { sendToEveryone() }
                .bounds(left, y, WIDTH, ROW)
                .tooltip(Tooltip.create(Component.literal(
                    "Sends one line of chat that everyone can see. Players with this mod see a button to add the shape; the rest see plain text."
                )))
                .build()
        )
        y += ROW + GAP * 3

        addRenderableWidget(StringWidget(left, y, WIDTH, font.lineHeight,
            Component.literal("Or privately, to the players you pick:").withStyle { it.withColor(0xDDDDDD) }, font))
        y += font.lineHeight + GAP

        val search = EditBox(font, left, y, WIDTH, ROW, Component.literal("Search"))
        search.setHint(Component.literal("Search players…"))
        search.setMaxLength(32)
        search.value = query
        search.setResponder { query = it; page = 0; refreshRows() }
        addRenderableWidget(search)
        y += ROW + GAP

        rowsTop = y
        rows = ROWS
        y = rowsTop + rows * (ROW + GAP) + GAP

        val third = (WIDTH - GAP * 2) / 3
        previous = addRenderableWidget(Button.builder(Component.literal("<")) { page--; refreshRows() }
            .bounds(left, y, third, ROW).build())
        pageLabel = addRenderableWidget(StringWidget(left + third + GAP, y + 6, third, font.lineHeight, Component.empty(), font))
        next = addRenderableWidget(Button.builder(Component.literal(">")) { page++; refreshRows() }
            .bounds(left + (third + GAP) * 2, y, third, ROW).build())
        y += ROW + GAP * 2

        val half = (WIDTH - GAP) / 2
        send = addRenderableWidget(Button.builder(Component.empty()) { sendToChosen() }.bounds(left, y, half, ROW).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
            .bounds(left + half + GAP, y, WIDTH - half - GAP, ROW).build())

        refreshRows()
    }

    /** Everyone else on the server, matching the search. */
    private fun players(): List<String> {
        val connection = minecraft.connection ?: return emptyList()
        val me = minecraft.player?.gameProfile?.name
        val words = query.trim().lowercase()
        return connection.onlinePlayers
            .map { it.profile.name }
            .filter { it != me && it.isNotBlank() }
            .filter { words.isEmpty() || it.lowercase().contains(words) }
            .sorted()
    }

    private fun refreshRows() {
        rowWidgets.forEach(::removeWidget)
        rowWidgets.clear()

        val players = players()
        val pages = maxOf(1, (players.size + rows - 1) / rows)
        page = page.coerceIn(0, pages - 1)
        pageLabel.message = Component.literal(if (players.isEmpty()) "" else "${page + 1} / $pages")
        previous.active = page > 0
        next.active = page < pages - 1
        send.message = Component.literal(if (chosen.size <= 1) "Send privately" else "Send to ${chosen.size}")
        send.active = chosen.isNotEmpty()
        send.setTooltip(Tooltip.create(Component.literal(
            if (chosen.isEmpty()) "Pick at least one player above."
            else "Sends it to ${chosen.joinToString(", ")} as a private message (/${Config.privateShareCommand}), one at a time. Nobody else sees it."
        )))

        if (players.isEmpty()) {
            val text = when {
                minecraft.connection?.onlinePlayers?.size ?: 0 <= 1 -> "Nobody else is online."
                else -> "No players match \"$query\"."
            }
            rowWidgets.add(addRenderableWidget(StringWidget(left, rowsTop + 6, WIDTH, font.lineHeight,
                Component.literal(text).withStyle { it.withColor(0xBBBBBB) }, font)))
            return
        }

        var y = rowsTop
        for (name in players.drop(page * rows).take(rows)) {
            val picked = name in chosen
            rowWidgets.add(addRenderableWidget(
                Button.builder(Component.literal(if (picked) "✔ $name" else name)
                    .withStyle { if (picked) it.withColor(0x55FF55) else it }) {
                    if (!chosen.remove(name)) chosen.add(name)
                    refreshRows()
                }.bounds(left, y, WIDTH, ROW).build()
            ))
            y += ROW + GAP
        }
    }

    private fun sendToEveryone() {
        if (ShapeShare.share(shape)) ShapeShare.saidWhereItWent(shape, emptyList())
        minecraft.gui.setScreen(parent)
    }

    private fun sendToChosen() {
        val players = chosen.toList()
        if (players.isEmpty()) return
        if (ShapeShare.shareWith(shape, players)) ShapeShare.saidWhereItWent(shape, players)
        minecraft.gui.setScreen(parent)
    }

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
        const val ROWS = 5
        const val PANEL = 0xC0101010.toInt()
    }
}
