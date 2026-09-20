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

    /** Sends [shape] to everyone in chat. Only ever called from the Share option. */
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
        Log.info("Shared {} in chat", shape.name)
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

    /** What a server will take in one line of chat. */
    private const val MAX_CHAT = 256
}
