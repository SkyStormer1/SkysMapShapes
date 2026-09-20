package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * `config/skysmapshapes.json`: how shapes are drawn, and the presets offered when adding one.
 * Edited in game through the settings screen. The shapes themselves are kept per server by
 * [ShapeStore].
 *
 * Read and written by hand with Gson, which Minecraft already ships.
 */
object Config {

    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve("skysmapshapes.json")

    /** Off turns the whole mod off: nothing drawn, no menu options, no Shapes button. Shapes are kept. */
    var enabled = true

    var showOnWorldMap = true
    var showOnMinimap = true
    var showLabels = true

    /** Whether MiniHUD's shapes are shown on the map, when MiniHUD is installed. */
    var showMiniHud = true

    /** Whether MiniHUD shapes switched off in MiniHUD are drawn on the map anyway. */
    var miniHudIncludeDisabled = false

    /**
     * Whether hiding a MiniHUD shape on the map also switches it off in MiniHUD, so it goes from
     * the world too. Off means hiding only affects the map.
     */
    var hideInMiniHud = true

    /** Whether shapes shared in chat by other players are offered as a clickable add button. */
    var shareInChat = true

    /**
     * The command a private share uses, without the slash. Vanilla and most servers take `tell`
     * (or `msg`, or `w`); change it if yours uses something else.
     */
    var privateShareCommand = "tell"

    /** Multiplies every shape's own outline thickness, to make them all thicker or thinner at once. */
    var thicknessScale = 1f

    /** How opaque a filled shape's inside is, 0 to 1. */
    var fillOpacity = 0.15f

    /**
     * A radius offered as a button when adding a shape. The defaults are vanilla's despawn
     * distances; every server can change them, so they are only starting values.
     */
    class Preset(val name: String, val radius: Double, val colour: Int)

    var presets: List<Preset> = defaultPresets()

    fun defaultPresets() = listOf(
        Preset("Instant despawn", 128.0, Colours.RED.argb),
        Preset("No despawn", 32.0, Colours.GREEN.argb),
    )

    const val MAX_PRESETS = 4
    const val MIN_LINE_WIDTH = 0.5f
    const val MAX_LINE_WIDTH = 8f
    const val DEFAULT_LINE_WIDTH = 2f
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 4f

    fun load() {
        try {
            if (!Files.exists(file)) {
                save()
                return
            }
            val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
            enabled = json.get("enabled")?.asBoolean ?: enabled
            showOnWorldMap = json.get("showOnWorldMap")?.asBoolean ?: showOnWorldMap
            showOnMinimap = json.get("showOnMinimap")?.asBoolean ?: showOnMinimap
            showLabels = json.get("showLabels")?.asBoolean ?: showLabels
            showMiniHud = json.get("showMiniHud")?.asBoolean ?: showMiniHud
            miniHudIncludeDisabled = json.get("miniHudIncludeDisabled")?.asBoolean ?: miniHudIncludeDisabled
            hideInMiniHud = json.get("hideInMiniHud")?.asBoolean ?: hideInMiniHud
            shareInChat = json.get("shareInChat")?.asBoolean ?: shareInChat
            privateShareCommand = json.get("privateShareCommand")?.asString?.trim()?.removePrefix("/")
                ?.takeIf { it.isNotEmpty() } ?: privateShareCommand
            thicknessScale = (json.get("thicknessScale")?.asFloat ?: thicknessScale).coerceIn(MIN_SCALE, MAX_SCALE)
            fillOpacity = (json.get("fillOpacity")?.asFloat ?: fillOpacity).coerceIn(0f, 1f)
            json.getAsJsonArray("presets")?.let { array ->
                presets = array.mapNotNull { element ->
                    val preset = element.asJsonObject
                    val name = preset.get("name")?.asString ?: return@mapNotNull null
                    val radius = preset.get("radius")?.asDouble ?: return@mapNotNull null
                    Preset(name, radius, Colours.parse(preset.get("colour")?.asString) ?: Colours.RED.argb)
                }.take(MAX_PRESETS)
            }
        } catch (e: Exception) {
            Log.error("Could not read $file; using the defaults", e)
        }
    }

    fun save() {
        try {
            val json = JsonObject()
            json.addProperty("enabled", enabled)
            json.addProperty("showOnWorldMap", showOnWorldMap)
            json.addProperty("showOnMinimap", showOnMinimap)
            json.addProperty("showLabels", showLabels)
            json.addProperty("showMiniHud", showMiniHud)
            json.addProperty("miniHudIncludeDisabled", miniHudIncludeDisabled)
            json.addProperty("hideInMiniHud", hideInMiniHud)
            json.addProperty("shareInChat", shareInChat)
            json.addProperty("privateShareCommand", privateShareCommand)
            json.addProperty("thicknessScale", thicknessScale)
            json.addProperty("fillOpacity", fillOpacity)
            val array = JsonArray()
            for (preset in presets) {
                array.add(JsonObject().apply {
                    addProperty("name", preset.name)
                    addProperty("radius", preset.radius)
                    addProperty("colour", Colours.format(preset.colour))
                })
            }
            json.add("presets", array)
            Files.createDirectories(file.parent)
            Files.writeString(file, GSON.toJson(json))
        } catch (e: Exception) {
            Log.error("Could not save $file", e)
        }
    }
}

/** The colours a shape can be. Stored in files as `#RRGGBB`, so any colour typed there works too. */
enum class Colours(val title: String, rgb: Int) {
    RED("Red", 0xFF5555),
    ORANGE("Orange", 0xFFAA00),
    YELLOW("Yellow", 0xFFFF55),
    GREEN("Green", 0x55FF55),
    AQUA("Aqua", 0x55FFFF),
    BLUE("Blue", 0x5577FF),
    PURPLE("Purple", 0xCC66FF),
    PINK("Pink", 0xFF77CC),
    WHITE("White", 0xFFFFFF),
    BLACK("Black", 0x202020);

    val argb: Int = rgb or 0xFF000000.toInt()

    companion object {
        /** The named colour, or null for one only set in a file. */
        fun of(argb: Int): Colours? = entries.firstOrNull { it.argb == (argb or 0xFF000000.toInt()) }

        fun name(argb: Int): String = of(argb)?.title ?: format(argb)

        fun format(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

        fun parse(text: String?): Int? {
            val hex = text?.trim()?.removePrefix("#") ?: return null
            if (hex.length != 6) return null
            return hex.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
        }
    }
}
