package com.skystormer.skysmapshapes

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.util.Base64

/**
 * Sharing a shape with the people you play with, the way Xaero shares a waypoint: the shape is
 * sent as a line of chat holding a short code, and anyone else with this mod sees a message they
 * can click to add it.
 *
 * The code carries the shape's own dimension, so it always lands on the right map: a Nether shape
 * shared while the other player is in the Overworld is added to their Nether map, at the Nether
 * coordinates it had here. Nothing is scaled and nothing is added without the other player
 * clicking, and a player without the mod just sees an ordinary line of chat.
 */
object ShapeShare {

    /** The marker in a shared line of chat, followed by the code. */
    private const val TAG = "SMS1:"

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** A shared shape: the outline for the map, and what MiniHUD needs to make it in the world. */
    class Shared(val shape: Shape, val miniHudType: String?, val y: Int?)

    /** The chat line for [shape]: readable to everyone, with the code on the end. */
    fun message(shape: MapShape): String {
        val own = asShape(shape) ?: return ""
        return "Map shape: ${shape.name} · ${own.describeSize()} · ${dimensionName(shape.dimension)} · $TAG${encode(shape)}"
    }

    /** The outline as one of our own shapes; null for one that cannot be shared, such as a line. */
    fun asShape(shape: MapShape): Shape? = when (shape) {
        is Shape -> shape
        is MiniHudShape -> shape.asOwnShape()
        else -> null
    }

    /**
     * Sends [shape] as a line of chat: to everyone, or privately to [player] when one is named.
     * Only ever called from the Share screen, by a click.
     */
    fun share(shape: MapShape): Boolean {
        val connection = Minecraft.getInstance().connection ?: return false
        if (asShape(shape) == null) {
            MapMenus.say("${shape.name} is a kind of shape that cannot be shared.")
            return false
        }
        val line = message(shape)
        if (line.length > MAX_CHAT) {
            MapMenus.say("That shape's name is too long to share; shorten it a little.")
            return false
        }
        connection.sendChat(line)
        Log.info("Shared {} with everyone", shape.name)
        return true
    }

    /**
     * The shape as a short code: the same fields the shapes file keeps, with short names, plus
     * what MiniHUD needs (its own kind of shape and the height it sits at) when it came from
     * MiniHUD. Someone without MiniHUD simply ignores those and gets the outline on their map.
     */
    fun encode(source: MapShape): String {
        val shape = asShape(source) ?: return ""
        val json = JsonObject()
        if (source is MiniHudShape) {
            json.addProperty("m", source.typeId)
            source.centreY?.let { json.addProperty("y", it) }
        } else if (shape.y != null) {
            json.addProperty("y", shape.y)
        }
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

    /** The code in a line of chat, or null when there is none. */
    fun codeIn(text: String): String? {
        val start = text.indexOf(TAG)
        if (start < 0) return null
        return text.substring(start + TAG.length).trim().takeWhile { !it.isWhitespace() }.takeIf { it.isNotEmpty() }
    }

    /**
     * Called for every line of chat. A shared shape is replaced by a tidy message with an add
     * button; anything else is left alone.
     *
     * @return whether the original line should still be shown.
     */
    fun onChat(text: String): Boolean {
        if (!Config.enabled || !Config.shareInChat) return true
        val code = codeIn(text) ?: return true
        val shared = decode(code) ?: return true
        val shape = shared.shape
        // Who sent it, as chat shows it, without our code on the end.
        val said = text.substringBefore(TAG).trim().removeSuffix("·").trim()
        // Shown as a system message of our own, in place of the line that carried the code.
        Minecraft.getInstance().gui.chatListener().handleSystemMessage(
            Component.literal("$said  ").append(
                Component.literal("[Add to my map]")
                    .withStyle {
                        it.withColor(shape.colour and 0xFFFFFF).withBold(true)
                            .withClickEvent(ClickEvent.RunCommand("/$COMMAND $code"))
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
