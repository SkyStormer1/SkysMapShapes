package com.skystormer.skysmapshapes.gui

import com.skystormer.skysmapshapes.Config
import com.skystormer.skysmapshapes.Dimensions
import com.skystormer.skysmapshapes.MapShape
import com.skystormer.skysmapshapes.ShapeShare
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
class ShareScreen(parent: Screen?, private val shape: MapShape) :
    PanelScreen(parent, Component.literal("Share ${shape.name}")) {

    private val chosen = LinkedHashSet<String>()
    private var query = ""
    private val list = PagedRows({ addRenderableWidget(it) }, { removeWidget(it) })
    private lateinit var send: Button

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
        search.setResponder { query = it; list.page = 0; refreshRows() }
        addRenderableWidget(search)
        y += ROW + GAP

        list.top = y
        list.rows = ROWS
        y = list.top + ROWS * (ROW + GAP) + GAP

        list.buttons(font, left, y, (WIDTH - GAP * 2) / 3) { refreshRows() }
        y += ROW + GAP * 2

        val half = (WIDTH - GAP) / 2
        send = addRenderableWidget(Button.builder(Component.empty()) { sendToChosen() }.bounds(left, y, half, ROW).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
            .bounds(left + half + GAP, y, WIDTH - half - GAP, ROW).build())

        refreshRows()
    }

    /**
     * Everyone else on the server, matching the search. Names no player can have are left out:
     * the list comes from the server, and an entry such as `@a` would send the shape to everyone.
     */
    private fun players(): List<String> {
        val connection = minecraft.connection ?: return emptyList()
        val me = minecraft.player?.gameProfile?.name
        val words = query.trim().lowercase()
        return connection.onlinePlayers
            .map { it.profile.name }
            .filter { it != me && ShapeShare.isPlayerName(it) }
            .filter { words.isEmpty() || it.lowercase().contains(words) }
            .sorted()
    }

    private fun refreshRows() {
        val players = players()
        val shown = list.pageOf(players)
        send.message = Component.literal(if (chosen.size <= 1) "Send privately" else "Send to ${chosen.size}")
        send.active = chosen.isNotEmpty()
        send.setTooltip(Tooltip.create(Component.literal(
            if (chosen.isEmpty()) "Pick at least one player above."
            else "Sends it to ${chosen.joinToString(", ")} as a private message (/${Config.privateShareCommand}), one at a time. Nobody else sees it."
        )))

        if (players.isEmpty()) {
            val text = when {
                (minecraft.connection?.onlinePlayers?.size ?: 0) <= 1 -> "Nobody else is online."
                else -> "No players match \"$query\"."
            }
            list.add(StringWidget(left, list.top + 6, WIDTH, font.lineHeight,
                Component.literal(text).withStyle { it.withColor(0xBBBBBB) }, font))
            return
        }

        var y = list.top
        for (name in shown) {
            val picked = name in chosen
            list.add(
                Button.builder(Component.literal(if (picked) "✔ $name" else name)
                    .withStyle { if (picked) it.withColor(0x55FF55) else it }) {
                    if (!chosen.remove(name)) chosen.add(name)
                    refreshRows()
                }.bounds(left, y, WIDTH, ROW).build()
            )
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

    private companion object {
        const val ROWS = 5
    }
}
