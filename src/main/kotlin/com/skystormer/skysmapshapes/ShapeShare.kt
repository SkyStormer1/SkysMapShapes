package com.skystormer.skysmapshapes

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.util.Base64

/**
 * Sharing a shape in chat, the way Xaero shares a waypoint and Sky's Structure Map shares a
 * structure: one plain line anyone can read, such as
 * `Map shape AFK spot: circle r128 at 250 -96 (Nether) · orange · filled`. Players without this
 * mod see just that; anyone with it sees an [Add to my map] button beside it, which opens the shape
 * for them to look at before keeping it. The coded form only lives in that button's command and
 * never goes into chat. (1.2.0 and earlier put a coded string on the end of the line, which read as
 * random letters to everyone; those old lines are still understood.)
 *
 * The line carries the shape's own dimension, so it always lands on the right map: a Nether shape
 * shared while the other player is in the Overworld is added to their Nether map, at the Nether
 * coordinates it had here. Nothing is scaled and nothing is added without the other player
 * clicking. A MiniHUD shape also carries MiniHUD's own kind of shape and the height it sits at.
 */
object ShapeShare {

    /** Where a shared line starts. */
    private const val START = "Map shape "

    /** How 1.2.0 and earlier marked a shared line: a code followed it. */
    private const val TAG = "SMS1:"

    /** Between the readable parts of a shared line. */
    private const val SEP = " · "

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** A shared shape: the outline for the map, and what MiniHUD needs to make it in the world. */
    class Shared(val shape: Shape, val miniHudType: String?, val y: Int?)

    /** The outline as one of our own shapes; null for one that cannot be shared, such as a line. */
    fun asShape(shape: MapShape): Shape? = when (shape) {
        is Shape -> shape
        is MiniHudShape -> shape.asOwnShape()
        else -> null
    }

    /** What [shape] is shared as: its outline, and MiniHUD's kind and height when it came from there. */
    fun sharedOf(shape: MapShape): Shared? {
        val own = asShape(shape) ?: return null
        return if (shape is MiniHudShape) Shared(own, shape.typeId, shape.centreY) else Shared(own, null, own.y)
    }

    /** The chat line for [shape]: every word of it readable. Empty for one that cannot be shared. */
    fun message(shape: MapShape): String = sharedOf(shape)?.let(::line) ?: ""

    fun line(shared: Shared): String {
        val shape = shared.shape
        val size = when (shape.type.sized) {
            Shape.Sized.RADIUS -> "r${num(shape.radius)}"
            Shape.Sized.WIDTH_LENGTH -> "${num(shape.width)}x${num(shape.length)}" +
                if (shape.type == Shape.Type.RECTANGLE && shape.anchor == Shape.Anchor.CORNER) " from corner" else ""
        }
        val text = StringBuilder()
        text.append(START).append(shape.name).append(": ")
            .append(shape.type.title.lowercase()).append(' ').append(size)
            .append(" at ").append(shape.x).append(' ').append(shape.z)
            .append(" (").append(dimensionName(shape.dimension)).append(')')
        text.append(SEP).append(Colours.of(shape.colour)?.title?.lowercase() ?: Colours.format(shape.colour))
        if (shape.fill) text.append(SEP).append("filled")
        if (shape.lineWidth != Config.DEFAULT_LINE_WIDTH) text.append(SEP).append(num(shape.lineWidth.toDouble())).append("px")
        if (shared.miniHudType != null) {
            text.append(SEP).append("MiniHUD ").append(shared.miniHudType)
            shared.y?.let { text.append(" y ").append(it) }
        } else if (shared.y != null) {
            text.append(SEP).append("y ").append(shared.y)
        }
        return text.toString()
    }

    /** Numbers as plain text, the same in every language: no ".0", and a dot for decimals. */
    private fun num(value: Double): String =
        if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

    private val LINE = Regex(
        """^(.*): (circle|square|diamond|octagon|rectangle|ellipse) """ +
            """(?:r(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)x(\d+(?:\.\d+)?)( from corner)?) """ +
            """at (-?\d+) (-?\d+) \(([^)]+)\)(.*)$"""
    )

    /**
     * A shared shape in a readable line of chat, and the part of the line to show beside its
     * button, or null when the line holds none.
     */
    fun readLine(text: String): Pair<String, Shared>? {
        val start = text.indexOf(START)
        if (start < 0) return null
        val m = LINE.find(text.substring(start + START.length).trimEnd()) ?: return null
        val g = m.groupValues
        val type = Shape.Type.entries.firstOrNull { it.title.lowercase() == g[2] } ?: return null
        val radius = g[3].toDoubleOrNull() ?: 0.0
        val width = g[4].toDoubleOrNull() ?: 0.0
        val length = g[5].toDoubleOrNull() ?: 0.0
        if (type.sized == Shape.Sized.RADIUS && radius <= 0) return null
        if (type.sized == Shape.Sized.WIDTH_LENGTH && (width <= 0 || length <= 0)) return null
        val dimension = when (g[9]) {
            "Overworld" -> "minecraft:overworld"
            "Nether" -> "minecraft:the_nether"
            "End" -> "minecraft:the_end"
            else -> g[9].let { if (':' in it) it else "minecraft:$it" }
        }

        var colour = Colours.RED.argb
        var fill = false
        var lineWidth = Config.DEFAULT_LINE_WIDTH
        var miniHudType: String? = null
        var y: Int? = null
        for (part in g[10].split(SEP.trim()).map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                part == "filled" -> fill = true
                part.endsWith("px") -> part.removeSuffix("px").toFloatOrNull()?.let {
                    lineWidth = it.coerceIn(Config.MIN_LINE_WIDTH, Config.MAX_LINE_WIDTH)
                }
                part.startsWith("MiniHUD ") -> {
                    val words = part.removePrefix("MiniHUD ").split(' ')
                    miniHudType = words.firstOrNull()?.takeIf { it.isNotBlank() }
                    if (words.size >= 3 && words[1] == "y") y = words[2].toIntOrNull()
                }
                part.startsWith("y ") -> y = part.removePrefix("y ").trim().toIntOrNull()
                part.startsWith("#") -> Colours.parse(part)?.let { colour = it }
                else -> Colours.entries.firstOrNull { it.title.equals(part, ignoreCase = true) }?.let { colour = it.argb }
            }
        }

        val name = g[1].trim()
        val shape = Shape(
            label = if (name == type.title) "" else name,
            dimension = dimension,
            type = type,
            x = g[7].toInt(),
            z = g[8].toInt(),
            radius = radius,
            width = width,
            length = length,
            anchor = if (g[6].isNotEmpty()) Shape.Anchor.CORNER else Shape.Anchor.CENTRE,
            colour = colour,
            fill = fill,
            lineWidth = lineWidth,
            y = y,
        )
        // What to show beside the button: the line up to its dimension, without the extras.
        val shown = text.substring(0, text.length - g[10].length).trimEnd()
        return shown to Shared(shape, miniHudType, y)
    }

    /** The code the add button's command carries. It never goes into chat. */
    fun encode(shared: Shared): String {
        val shape = shared.shape
        val json = JsonObject()
        shared.miniHudType?.let { json.addProperty("m", it) }
        shared.y?.let { json.addProperty("y", it) }
        json.addProperty("l", shape.label)
        json.addProperty("d", shape.dimension)
        json.addProperty("t", shape.type.name.lowercase())
        json.addProperty("x", shape.x)
        json.addProperty("z", shape.z)
        when (shape.type.sized) {
            Shape.Sized.RADIUS -> json.addProperty("r", shape.radius)
            Shape.Sized.WIDTH_LENGTH -> {
                json.addProperty("w", shape.width)
                json.addProperty("g", shape.length)
                if (shape.anchor == Shape.Anchor.CORNER) json.addProperty("a", "corner")
            }
        }
        json.addProperty("c", Colours.format(shape.colour))
        if (shape.fill) json.addProperty("f", 1)
        if (shape.lineWidth != Config.DEFAULT_LINE_WIDTH) json.addProperty("n", shape.lineWidth)
        return encoder.encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    /** A shared shape from a code, or null if it is not one of ours or is damaged. */
    fun decode(code: String): Shared? = try {
        val json = JsonParser.parseString(String(decoder.decode(code.trim()), Charsets.UTF_8)).asJsonObject
        val type = Shape.Type.valueOf(json.get("t").asString.uppercase())
        val y = json.get("y")?.asInt
        Shared(
            miniHudType = json.get("m")?.asString,
            y = y,
            shape = Shape(
                label = json.get("l")?.asString ?: "",
                dimension = json.get("d").asString,
                type = type,
                x = json.get("x").asInt,
                z = json.get("z").asInt,
                radius = json.get("r")?.asDouble ?: 0.0,
                width = json.get("w")?.asDouble ?: 0.0,
                length = json.get("g")?.asDouble ?: 0.0,
                anchor = if (json.get("a")?.asString == "corner") Shape.Anchor.CORNER else Shape.Anchor.CENTRE,
                colour = Colours.parse(json.get("c")?.asString) ?: Colours.RED.argb,
                fill = json.get("f") != null,
                lineWidth = json.get("n")?.asFloat?.coerceIn(Config.MIN_LINE_WIDTH, Config.MAX_LINE_WIDTH) ?: Config.DEFAULT_LINE_WIDTH,
                y = y,
            ),
        )
    } catch (e: Exception) {
        null
    }

    /** The code on the end of a line from 1.2.0 or earlier, or null when there is none. */
    fun codeIn(text: String): String? {
        val start = text.indexOf(TAG)
        if (start < 0) return null
        return text.substring(start + TAG.length).trim().takeWhile { !it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }

    /** Sends [shape] to everyone as one line of chat. Only called from the Share screen, by a click. */
    fun share(shape: MapShape): Boolean {
        val connection = Minecraft.getInstance().connection ?: return false
        val line = message(shape)
        if (line.isEmpty()) {
            MapMenus.say("${shape.name} is a kind of shape that cannot be shared.")
            return false
        }
        if (line.length > MAX_CHAT) {
            MapMenus.say("That shape's name is too long to share; shorten it a little.")
            return false
        }
        connection.sendChat(line)
        Log.info("Shared {} with everyone", shape.name)
        return true
    }

    /**
     * Called for every line of chat. A shared shape is shown as a tidy message with an add
     * button in place of the line; anything else is left alone.
     *
     * @return whether the original line should still be shown.
     */
    fun onChat(text: String): Boolean {
        if (!Config.enabled || !Config.shareInChat) return true
        val (said, shared) = readLine(text) ?: run {
            // A line from 1.2.0 or earlier, with the code on the end: shown without it.
            val old = codeIn(text)?.let(::decode) ?: return true
            text.substringBefore(TAG).trim().removeSuffix("·").trim() to old
        }
        val shape = shared.shape
        Minecraft.getInstance().gui.chatListener().handleSystemMessage(
            Component.literal("$said  ").append(
                Component.literal("[Add to my map]")
                    .withStyle {
                        it.withColor(shape.colour and 0xFFFFFF).withBold(true)
                            .withClickEvent(ClickEvent.RunCommand("/$COMMAND ${encode(shared)}"))
                            .withHoverEvent(HoverEvent.ShowText(Component.literal(
                                "${shape.name}\n${shape.describeSize()}\n${shape.describePosition()}\n${dimensionName(shape.dimension)}" +
                                    (if (shared.miniHudType != null && MiniHudShapes.installed) "\nMade in MiniHUD as well, if you want" else "") +
                                    "\nClick to look at it before adding"
                            )))
                    }
            ),
            false,
        )
        return false
    }

    /** Opens the shared shape in the add-a-shape screen, so it is looked at before it is kept. */
    fun accept(code: String) {
        val minecraft = Minecraft.getInstance()
        val shared = decode(code) ?: return MapMenus.say("That shape code could not be read.")
        if (!ShapeStore.isOpen) return MapMenus.say("Join a world first.")
        minecraft.gui.setScreen(com.skystormer.skysmapshapes.gui.ShapeEditScreen.forShared(minecraft.gui.screen(), shared))
    }

    fun dimensionName(dimension: String): String = when (dimension) {
        "minecraft:overworld" -> "Overworld"
        "minecraft:the_nether" -> "Nether"
        "minecraft:the_end" -> "End"
        else -> dimension.removePrefix("minecraft:")
    }

    /** The client-side command the add button runs. */
    const val COMMAND = "skysmapshapes"

    /** Private messages still to send, one every [SEND_EVERY] ticks. */
    private val waiting = ArrayDeque<Pair<String, String>>()

    /**
     * Sends [shape] privately to each of [players]. They go one at a time rather than all at
     * once, because a burst of messages looks like spam to a server and can get you kicked.
     */
    fun shareWith(shape: MapShape, players: List<String>): Boolean {
        if (Minecraft.getInstance().connection == null) return false
        val line = message(shape).takeIf { it.isNotEmpty() } ?: return false
        for (player in players) waiting.addLast(player to line)
        return true
    }

    private var ticks = 0

    /** Called every client tick: sends the next waiting message. */
    fun tick() {
        if (waiting.isEmpty()) return
        if (++ticks < SEND_EVERY) return
        ticks = 0
        val connection = Minecraft.getInstance().connection ?: run { waiting.clear(); return }
        val (player, line) = waiting.removeFirst()
        val command = "${Config.privateShareCommand} $player $line"
        if (command.length > MAX_COMMAND) {
            MapMenus.say("That shape's name is too long to send privately; shorten it a little.")
            waiting.clear()
            return
        }
        connection.sendCommand(command)
        Log.info("Sent a shape to {}", player)
    }

    /** Nothing left to send when leaving a world. */
    fun clear() = waiting.clear()

    /** Says where a shared shape went, on the action bar. */
    fun saidWhereItWent(shape: MapShape, players: List<String>) {
        MapMenus.say(when {
            players.isEmpty() -> "Shared ${shape.name} with everyone"
            players.size == 1 -> "Sent ${shape.name} to ${players[0]}"
            else -> "Sending ${shape.name} to ${players.size} players"
        })
    }

    /** What a server will take in one line of chat, and in one command. */
    private const val MAX_CHAT = 256
    private const val MAX_COMMAND = 256

    /** Ticks between private messages: half a second, which no server counts as spam. */
    private const val SEND_EVERY = 10
}
