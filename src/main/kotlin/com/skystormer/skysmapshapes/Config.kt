package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

/**
 * `config/skysmapshapes.json`: how shapes are drawn, and the presets offered when adding one.
 * Edited in game through the settings screen. The shapes themselves are kept per server by
 * [ShapeStore].
 *
 * Read and written with Gson, which Minecraft already ships. Each setting is declared once, below:
 * its default, its bounds and how it is kept in the file ([Setting]), under its own name. Loading,
 * saving and the screens' Reset to defaults all go through that.
 */
object Config {

    private val GSON = GsonBuilder().setPrettyPrinting().create()

    /** Every setting, by name, in the order declared (which is the order a later one may depend on). */
    private val settings = LinkedHashMap<String, Setting<*>>()

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve("skysmapshapes.json")

    /** Off turns the whole mod off: nothing drawn, no menu options, no Shapes panel. Shapes are kept. */
    var enabled by bool(true)

    var showOnWorldMap by bool(true)
    var showOnMinimap by bool(true)
    var showLabels by bool(true)

    /** Whether MiniHUD's shapes are shown on the map, when MiniHUD is installed. */
    var showMiniHud by bool(true)

    /** Whether MiniHUD shapes switched off in MiniHUD are drawn on the map anyway. */
    var miniHudIncludeDisabled by bool(false)

    /**
     * Whether Hide all and Show all in the Shapes list also switch MiniHUD's shapes off and on in
     * MiniHUD, so they go from the world too. Off means they only affect the map. (The name is
     * kept from when it applied to hiding one shape, so existing settings carry over.)
     */
    var hideInMiniHud by bool(true)

    /**
     * Whether MiniHUD's light level overlay only shows inside shapes, each shape's own light
     * setting deciding how it counts (see [LightLevels]).
     */
    var lightInsideShapes by bool(false)

    /**
     * Whether MiniHUD's light level overlay marks floors in the Nether by what spawns in each
     * biome, not by whether a creeper could stand there (see [NetherSpawns]).
     */
    var netherBiomeSpawns by bool(true)

    /**
     * Whether floors MiniHUD's light level overlay marks get a Litematica block to Easy Place on
     * them, where you said they are a problem (see [SpawnProofing]). Needs MiniHUD and Litematica.
     */
    var spawnProofing by bool(true)

    /** Whether every floor the overlay marks counts for spawn proofing, not only those in spawn-proofed shapes. */
    var spawnProofEverywhere by bool(false)

    /**
     * How far from you, in blocks each way, spawn proofing reaches. At [MAX_SPAWN_PROOF_RANGE] it
     * reaches as far as MiniHUD's light levels do; MiniHUD's own range limits it either way.
     */
    var spawnProofRange by int(MAX_SPAWN_PROOF_RANGE, MIN_SPAWN_PROOF_RANGE, MAX_SPAWN_PROOF_RANGE)

    /** Whether each patch of spawn proofing spots gets an outline of this mod's own, drawn through blocks. */
    var spawnProofRenderThrough by bool(true)

    /**
     * The colour of the spawn proofing overlay: the layer on each spot still to cover, drawn in
     * place of Litematica's own overlay there, and the through-blocks outline.
     */
    var spawnProofColour by colour(Colours.AQUA.argb)

    /** How see-through that layer is: 0 for not drawn at all, 1 for solid. */
    var spawnProofOpacity by float(0.25f, 0f, 1f)

    /**
     * Whether Litematica's own ghost blocks show on the spots. Off, only this mod's layer marks
     * them; Easy Place puts the blocks down just the same.
     */
    var spawnProofGhostBlocks by bool(true)

    /** How tall that layer is, in sixteenths of a block, from [MIN_SPAWN_PROOF_HEIGHT] to a whole block. */
    var spawnProofHeight by int(2, MIN_SPAWN_PROOF_HEIGHT, MAX_SPAWN_PROOF_HEIGHT)

    /** The accepted blocks: the block ids spawn proofing may put down, or null for every slab. */
    var spawnProofBlocks: Set<String>? by setting(null,
        { e -> e.asJsonArray.map { it.asString }.toSet() },
        { ids -> ids?.let { JsonArray().also { a -> it.sorted().forEach(a::add) } } })

    /** Whether shapes shared in chat by other players are offered as a clickable add button. */
    var shareInChat by bool(true)

    /**
     * The command a private share uses, without the slash. Vanilla and most servers take `tell`
     * (or `msg`, or `w`); change it if yours uses something else.
     */
    var privateShareCommand by setting("tell", ::readShareCommand, { JsonPrimitive(it) })

    /** The Shapes panel on the world map: whether it is there at all, and whether it is open or folded. */
    var showPanel by bool(true)
    var panelOpen by bool(true)

    /** Where the panel sits: its right edge this far in from the screen's, its top this far down. Moved by dragging its title. */
    var panelRight by int(4)
    var panelTop by int(60)

    /** How many lines the panel shows before scrolling. Changed by dragging its bottom edge. */
    var panelRows by int(8, 1, 40)

    /** The panel's size: 1 is the game's own. */
    var panelScale by float(1f, 0.5f, 4f)

    /** The panel's width when its place was saved, so it reads back to the same spot. */
    var panelWidth by int(0)

    /** Width added by dragging the panel's right edge. */
    var panelExtra by int(0, 0, 1000)

    /** The panel it is docked under, or "" for none. */
    var panelUnder by string("")

    /**
     * The add shape window on the world map, which is a map panel too: where it sits on its own,
     * whether it is folded, its size and extra width, and the panel it is docked under. It starts
     * docked under the Shapes panel's stack.
     */
    var addRight by int(4)
    var addTop by int(60)
    var addOpen by bool(true)
    var addScale by float(1f, 0.5f, 4f)
    var addWidth by int(0)
    var addExtra by int(0, 0, 1000)
    var addUnder by string("skysmapshapes:panel")

    /** The biggest the map panels may be made, before the letters look too blocky. */
    var panelMaxScale by float(2f, 1f, 4f)

    /** Multiplies every shape's own outline thickness, to make them all thicker or thinner at once. */
    var thicknessScale by float(1f, MIN_SCALE, MAX_SCALE)

    /** How opaque a filled shape's inside is, 0 to 1. */
    var fillOpacity by float(0.15f, 0f, 1f)

    /** Experimental: a beam drawn up from every beacon near you, lit or not ([BeaconBeams]). */
    var beaconBeams by bool(false)

    /** Experimental: the blocks in each beacon's way outlined, so you know what to break ([BeaconBeams]). */
    var beaconOutlines by bool(false)

    /** How many beacons get a beam at most, the nearest first. */
    var beaconLimit by int(200, MIN_BEACON_LIMIT, MAX_BEACON_LIMIT)

    /** How far out, in chunks, beacons are looked for; never past the chunks the game has loaded. */
    var beaconDistance by int(12, MIN_BEACON_DISTANCE, MAX_BEACON_DISTANCE)

    /** Experimental: the game's own beacon beams are not drawn, so only this mod's show. */
    var hideGameBeams by bool(false)

    /** The blocks in a beam's way are outlined through walls, rather than only where in sight. */
    var beaconThroughWalls by bool(true)

    /** Beams and the blocks in their way are only drawn between these heights, inclusive. */
    var beaconMinY by int(LOWEST_Y, LOWEST_Y, HIGHEST_Y)
    /** Read after [beaconMinY], so it is never below it. */
    var beaconMaxY by setting(HIGHEST_Y, { it.asInt }, { JsonPrimitive(it) }, { it.coerceIn(beaconMinY, HIGHEST_Y) })

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

    /**
     * Whether a preset can make a shape that can be kept: a real radius no bigger than the add
     * screens take, and a name that fits its box. Any other one is skipped.
     */
    fun validPreset(name: String, radius: Double): Boolean =
        radius.isFinite() && radius > 0 && radius <= Shape.MAX_TYPED_SIZE && name.length <= MAX_PRESET_NAME

    /** What a private share command may be: `tell`, `msg`, `w`, `minecraft:tell`, a plugin's `pm`. */
    private val SHARE_COMMAND = Regex("[a-z0-9_:-]{1,32}")
    private var warnedShareCommand = false

    const val MAX_PRESET_NAME = 32
    const val MIN_BEACON_LIMIT = 50
    const val MAX_BEACON_LIMIT = 1000
    const val MIN_BEACON_DISTANCE = 1
    const val MAX_BEACON_DISTANCE = 32

    /** The heights beacon beams can be limited to: the lowest and highest any dimension builds at. */
    const val LOWEST_Y = -64
    const val HIGHEST_Y = 320

    const val MAX_PRESETS = 4
    const val MIN_SPAWN_PROOF_RANGE = 4
    const val MAX_SPAWN_PROOF_RANGE = 64
    const val MIN_SPAWN_PROOF_HEIGHT = 1
    const val MAX_SPAWN_PROOF_HEIGHT = 16
    const val MIN_LINE_WIDTH = 0.5f
    const val MAX_LINE_WIDTH = 8f
    const val DEFAULT_LINE_WIDTH = 2f
    const val MIN_SCALE = 0.25f
    const val MAX_SCALE = 4f

    /** The value [property] (one of the settings above) has as the mod comes, for Reset to defaults. */
    @Suppress("UNCHECKED_CAST")
    fun <T> default(property: KProperty0<T>): T = (settings.getValue(property.name) as Setting<T>).default

    /** It is put in front of every private share, so only a plain command name will do; anything else is `tell`. */
    private fun readShareCommand(element: JsonElement): String? {
        val command = element.asString.trim().removePrefix("/").takeIf { it.isNotEmpty() } ?: return null
        if (SHARE_COMMAND.matches(command)) return command
        if (!warnedShareCommand) Log.warn("privateShareCommand '{}' is not a plain command name; using tell", command)
        warnedShareCommand = true
        return "tell"
    }

    private fun <T> setting(default: T, read: (JsonElement) -> T?, write: (T) -> JsonElement?, clamp: (T) -> T = { it }) =
        PropertyDelegateProvider<Any?, Setting<T>> { _, property ->
            Setting(property.name, default, read, write, clamp).also { settings[property.name] = it }
        }

    private fun bool(default: Boolean) = setting(default, { it.asBoolean }, { JsonPrimitive(it) })
    private fun int(default: Int) = setting(default, { it.asInt }, { JsonPrimitive(it) })
    private fun int(default: Int, min: Int, max: Int) = setting(default, { it.asInt }, { JsonPrimitive(it) }, { it.coerceIn(min, max) })
    private fun float(default: Float, min: Float, max: Float) = setting(default, { it.asFloat }, { JsonPrimitive(it) }, { it.coerceIn(min, max) })
    private fun string(default: String) = setting(default, { it.asString }, { JsonPrimitive(it) })
    private fun colour(default: Int) = setting(default, { Colours.parse(it.asString) }, { JsonPrimitive(Colours.format(it)) })

    fun load() {
        try {
            if (!Files.exists(file)) {
                save()
                return
            }
            val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
            for (setting in settings.values) setting.load(json)
            json.getAsJsonArray("presets")?.let { array ->
                presets = array.mapNotNull { element ->
                    val preset = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val name = preset.get("name")?.asString ?: return@mapNotNull null
                    val radius = preset.get("radius")?.asDouble ?: return@mapNotNull null
                    if (!validPreset(name, radius)) return@mapNotNull null
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
            for (setting in settings.values) setting.save(json)
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

/**
 * One setting in `config/skysmapshapes.json`, kept under [key]: [default] as the mod comes, [read]
 * from the file (null, or a value that cannot be read, keeps what it has), kept in bounds by
 * [clamp], and [write]n back (null leaves it out of the file). [Config] declares each one with `by`.
 */
class Setting<T>(
    val key: String,
    val default: T,
    private val read: (JsonElement) -> T?,
    private val write: (T) -> JsonElement?,
    private val clamp: (T) -> T,
) : ReadWriteProperty<Any?, T> {

    private var value: T = default

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    fun load(json: JsonObject) {
        val element = json.get(key)?.takeUnless { it.isJsonNull } ?: return
        try {
            read(element)?.let { value = clamp(it) }
        } catch (e: Exception) {
            Log.warn("Setting {} in skysmapshapes.json could not be read; keeping {}", key, value)
        }
    }

    fun save(json: JsonObject) {
        write(value)?.let { json.add(key, it) }
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
