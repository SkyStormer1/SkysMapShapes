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

    /** Off turns the whole mod off: nothing drawn, no menu options, no Shapes panel. Shapes are kept. */
    var enabled = true

    var showOnWorldMap = true
    var showOnMinimap = true
    var showLabels = true

    /** Whether MiniHUD's shapes are shown on the map, when MiniHUD is installed. */
    var showMiniHud = true

    /** Whether MiniHUD shapes switched off in MiniHUD are drawn on the map anyway. */
    var miniHudIncludeDisabled = false

    /**
     * Whether Hide all and Show all in the Shapes list also switch MiniHUD's shapes off and on in
     * MiniHUD, so they go from the world too. Off means they only affect the map. (The name is
     * kept from when it applied to hiding one shape, so existing settings carry over.)
     */
    var hideInMiniHud = true

    /** Whether shapes shared in chat by other players are offered as a clickable add button. */
    var shareInChat = true

    /**
     * The command a private share uses, without the slash. Vanilla and most servers take `tell`
     * (or `msg`, or `w`); change it if yours uses something else.
     */
    var privateShareCommand = "tell"

    /** The Shapes panel on the world map: whether it is there at all, and whether it is open or folded. */
    var showPanel = true
    var panelOpen = true

    /** Where the panel sits: its right edge this far in from the screen's, its top this far down. Moved by dragging its title. */
    var panelRight = 4
    var panelTop = 60

    /** How many lines the panel shows before scrolling. Changed by dragging its bottom edge. */
    var panelRows = 8

    /** The panel's size (1 = the game's own), the width added by dragging its right edge, and the panel it is docked under ("" for none). */
    var panelScale = 1f
    /** The panel's width when its place was saved, so it reads back to the same spot. */
    var panelWidth = 0
    var panelExtra = 0
    var panelUnder = ""

    /**
     * The add shape window on the world map, which is a map panel too: where it sits on its own,
     * whether it is folded, its size and extra width, and the panel it is docked under. It starts
     * docked under the Shapes panel's stack.
     */
    var addRight = 4
    var addTop = 60
    var addOpen = true
    var addScale = 1f
    var addWidth = 0
    var addExtra = 0
    var addUnder = "skysmapshapes:panel"

    /** The biggest the map panels may be made, before the letters look too blocky. */
    var panelMaxScale = 2f

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
            showPanel = json.get("showPanel")?.asBoolean ?: showPanel
            panelOpen = json.get("panelOpen")?.asBoolean ?: panelOpen
            panelRight = json.get("panelRight")?.asInt ?: panelRight
            panelTop = json.get("panelTop")?.asInt ?: panelTop
            panelRows = (json.get("panelRows")?.asInt ?: panelRows).coerceIn(1, 40)
            panelWidth = json.get("panelWidth")?.asInt ?: panelWidth
            addWidth = json.get("addWidth")?.asInt ?: addWidth
            panelScale = (json.get("panelScale")?.asFloat ?: panelScale).coerceIn(0.5f, 4f)
            panelExtra = (json.get("panelExtra")?.asInt ?: panelExtra).coerceIn(0, 1000)
            panelUnder = json.get("panelUnder")?.asString ?: panelUnder
            addRight = json.get("addRight")?.asInt ?: addRight
            addTop = json.get("addTop")?.asInt ?: addTop
            addOpen = json.get("addOpen")?.asBoolean ?: addOpen
            addScale = (json.get("addScale")?.asFloat ?: addScale).coerceIn(0.5f, 4f)
            addExtra = (json.get("addExtra")?.asInt ?: addExtra).coerceIn(0, 1000)
            addUnder = json.get("addUnder")?.asString ?: addUnder
            panelMaxScale = (json.get("panelMaxScale")?.asFloat ?: panelMaxScale).coerceIn(1f, 4f)
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
            json.addProperty("showPanel", showPanel)
            json.addProperty("panelOpen", panelOpen)
            json.addProperty("panelRight", panelRight)
            json.addProperty("panelTop", panelTop)
            json.addProperty("panelRows", panelRows)
            json.addProperty("panelWidth", panelWidth)
            json.addProperty("addWidth", addWidth)
            json.addProperty("panelScale", panelScale)
            json.addProperty("panelExtra", panelExtra)
            json.addProperty("panelUnder", panelUnder)
            json.addProperty("addRight", addRight)
            json.addProperty("addTop", addTop)
            json.addProperty("addOpen", addOpen)
            json.addProperty("addScale", addScale)
            json.addProperty("addExtra", addExtra)
            json.addProperty("addUnder", addUnder)
            json.addProperty("panelMaxScale", panelMaxScale)
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
            SafeFiles.writeString(file, GSON.toJson(json))
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
