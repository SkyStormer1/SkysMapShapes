package com.skystormer.skysmapshapes

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path

/**
 * MiniHUD's shapes, as outlines on the map. Optional: without MiniHUD installed this does nothing.
 *
 * Read-only and one way. The dimension you are in comes from MiniHUD's live shape list, so a
 * shape added, changed or deleted in MiniHUD shows here within half a second; other dimensions
 * come from the files MiniHUD saves for them. Nothing is ever written to MiniHUD, and shapes
 * added on the map stay this mod's own. MiniHUD's own renderer being switched off makes no
 * difference: its shapes are still shown on the map.
 *
 * Each 3D shape becomes the outline of its footprint seen from above: a sphere its widest circle,
 * a prism or pyramid its base, a box its rectangle, a line its line.
 *
 * MiniHUD is reached by reflection, through its shape list, its own JSON export of a shape and
 * its Shape Editor screen, so this mod neither ships nor copies any of it, and reads every kind
 * of shape through the same format MiniHUD saves them in. Editing always happens in MiniHUD.
 */
object MiniHudShapes {

    val installed: Boolean by lazy { FabricLoader.getInstance().isModLoaded("minihud") }

    /** How tall a flat map shape is made in MiniHUD, centred on the height it is given. */
    const val PRISM_HEIGHT = 256

    /** The shapes last read, every dimension. Replaced whole, as the render thread reads it. */
    @Volatile
    var all: List<MiniHudShape> = emptyList()
        private set

    /** Ticks between reads of MiniHUD: twice a second. */
    private const val READ_EVERY = 10

    private var ticks = 0
    private var broken = false
    private val fileCache = HashMap<Path, Pair<Long, List<MiniHudShape>>>()

    private class Api(
        val manager: Any,
        val getAllShapes: Method,
        val export: Method,
        val storageName: Method,
        val addShape: Method,
        val shapeClass: Class<*>,
    )

    private val api: Api? by lazy {
        try {
            val managerClass = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeManager")
            val shapeClass = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeBase")
            val strings = Class.forName("fi.dy.masa.malilib.util.StringUtils")
            Api(
                manager = managerClass.getField("INSTANCE").get(null),
                getAllShapes = managerClass.getMethod("getAllShapes"),
                export = managerClass.getMethod("exportShapeToJson", shapeClass),
                storageName = strings.getMethod("getStorageFileName", Boolean::class.javaPrimitiveType, String::class.java, String::class.java, String::class.java),
                addShape = managerClass.getMethod("addShape", shapeClass),
                shapeClass = shapeClass,
            ).also { Log.info("MiniHUD found: its shapes will be shown on the map") }
        } catch (e: Throwable) {
            Log.error("This version of MiniHUD could not be read; its shapes will not be shown", e)
            null
        }
    }

    /** Called every client tick; reads MiniHUD twice a second. */
    fun tick(minecraft: Minecraft, force: Boolean = false) {
        if (!installed || broken || !Config.enabled || !Config.showMiniHud) {
            if (all.isNotEmpty()) all = emptyList()
            return
        }
        ticks = (ticks + 1) % READ_EVERY
        if (ticks != 0 && !force) return
        val level = minecraft.level ?: run { all = emptyList(); return }
        val api = api ?: run { broken = true; return }
        try {
            val here = level.dimension().identifier().toString()
            val live = (api.getAllShapes.invoke(api.manager) as List<*>).mapNotNull { shape ->
                (api.export.invoke(api.manager, shape) as? JsonElement)?.takeIf { it.isJsonObject }?.let { parse(it.asJsonObject, here, shape) }
            }
            all = live + otherDimensions(api, here)
        } catch (e: Throwable) {
            broken = true
            all = emptyList()
            Log.error("Could not read MiniHUD's shapes; they will not be shown until the game restarts", e)
        }
    }

    fun inDimension(dimension: String): List<MiniHudShape> = all.filter { it.dimension == dimension }

    /**
     * Forgotten on leaving a world: the shapes themselves, which hold MiniHUD's own shape objects,
     * and the files read for other dimensions, which belong to that server.
     */
    fun clear() {
        all = emptyList()
        fileCache.clear()
        ticks = 0
    }

    /**
     * The shapes MiniHUD saved for this server's other dimensions. Its file for the dimension you
     * are in is named `<server>_dim_<namespace>_<path>.json`; the others share the first part.
     */
    private fun otherDimensions(api: Api, here: String): List<MiniHudShape> {
        val current = api.storageName.invoke(null, true, "", ".json", "minihud_default") as? String ?: return emptyList()
        val suffix = "_dim_" + here.replace(':', '_') + ".json"
        if (!current.endsWith(suffix)) return emptyList()
        val prefix = current.removeSuffix(suffix) + "_dim_"
        val folder = FabricLoader.getInstance().configDir.resolve("minihud")
        if (!Files.isDirectory(folder)) return emptyList()
        val result = ArrayList<MiniHudShape>()
        Files.list(folder).use { files ->
            for (file in files) {
                val name = file.fileName.toString()
                if (!name.startsWith(prefix) || !name.endsWith(".json") || name == current) continue
                val dimension = dimensionFromFile(name.removePrefix(prefix).removeSuffix(".json"))
                result += readFile(file, dimension)
            }
        }
        return result
    }

    /** `minecraft_the_nether` back to `minecraft:the_nether`. */
    private fun dimensionFromFile(text: String): String =
        if (text.startsWith("minecraft_")) "minecraft:" + text.removePrefix("minecraft_")
        else text.replaceFirst('_', ':')

    private fun readFile(file: Path, dimension: String): List<MiniHudShape> {
        val modified = Files.getLastModifiedTime(file).toMillis()
        fileCache[file]?.let { (time, shapes) -> if (time == modified) return shapes }
        val shapes = try {
            val json = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
            json.getAsJsonObject("shapes")?.getAsJsonArray("shapes")?.mapNotNull {
                it.takeIf { e -> e.isJsonObject }?.let { e -> parse(e.asJsonObject, dimension) }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.warn("Could not read MiniHUD's {}: {}", file.fileName, e.toString())
            emptyList()
        }
        fileCache[file] = modified to shapes
        return shapes
    }

    private val unknownTypes = HashSet<String>()

    /** One of MiniHUD's shapes, from the JSON it saves, as an outline; null for one with nothing to draw. */
    fun parse(json: JsonObject, dimension: String, handle: Any? = null): MiniHudShape? {
        val type = json.get("type")?.asString ?: return null
        // Shapes switched off in MiniHUD are still listed, greyed out, so they can be switched
        // back on from here; whether they are drawn is up to [MiniHudShape.visible].
        val enabled = json.get("enabled")?.asBoolean ?: true
        val geometry = MiniHudGeometry.of(type, json) ?: run {
            if (unknownTypes.add(type)) Log.warn("MiniHUD shape type '{}' has no outline on the map yet", type)
            return null
        }
        val label = json.get("display_name")?.asString ?: type
        val colour = json.get("color")?.asInt?.let { it or 0xFF000000.toInt() } ?: Colours.WHITE.argb
        // The same shape keeps the same id while it is not changed, so hiding it here sticks.
        val id = "minihud:" + dimension + ":" + type + ":" + MiniHudGeometry.identity(json)
        return MiniHudShape(id, label, dimension, colour, geometry, type, enabled, handle)
    }

    /**
     * Makes [shape] in MiniHUD, at height [y], as if it had been added in MiniHUD's own shape
     * list: it is then a MiniHUD shape, in the world as well as on the map, and this mod keeps no
     * copy of it. Only for the dimension you are in, which is the only one MiniHUD has open.
     *
     * A flat map shape has to be given a height: spheres and ellipsoids are centred on [y], and
     * the upright prisms (square, diamond, octagon, rectangle) run 128 blocks above and below it.
     * All of that can be changed afterwards in MiniHUD's editor.
     */
    fun create(shape: Shape, y: Int): Boolean {
        val api = api ?: return false
        return try {
            val json = MiniHudGeometry.toMiniHudJson(shape, y) ?: return false
            val typeClass = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeType")
            val type = typeClass.getMethod("fromString", String::class.java).invoke(null, json.get("type").asString)
                ?: return false.also { Log.warn("MiniHUD has no shape type '{}'", json.get("type").asString) }
            val made = typeClass.getMethod("createShape").invoke(type)
            api.shapeClass.getMethod("fromJson", JsonObject::class.java).invoke(made, json)
            api.addShape.invoke(api.manager, made)
            tick(Minecraft.getInstance(), force = true)
            true
        } catch (e: Throwable) {
            Log.error("Could not make ${shape.name} in MiniHUD", e)
            false
        }
    }

    /**
     * Switches [shape] on or off in MiniHUD itself, as its own shape list does, so it goes from
     * the world as well as the map. Only for shapes in the dimension you are in.
     */
    fun setEnabled(shape: MiniHudShape, enabled: Boolean): Boolean {
        val handle = shape.handle ?: return false
        return try {
            if (handle.javaClass.getMethod("isEnabled").invoke(handle) as Boolean != enabled) {
                handle.javaClass.getMethod("toggleEnabled").invoke(handle)
            }
            tick(Minecraft.getInstance(), force = true)
            true
        } catch (e: Throwable) {
            Log.error("Could not switch a shape ${if (enabled) "on" else "off"} in MiniHUD", e)
            false
        }
    }

    /**
     * Opens MiniHUD's own Shape Editor on [shape], returning to [parent] when it closes. Only for
     * shapes in the dimension you are in: those in other dimensions are only in MiniHUD's files.
     */
    fun openEditor(shape: MiniHudShape, parent: Screen?): Boolean {
        val handle = shape.handle ?: return false
        return try {
            val shapeClass = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeBase")
            val editor = Class.forName("fi.dy.masa.minihud.gui.GuiShapeEditor").getConstructor(shapeClass).newInstance(handle) as Screen
            editor.javaClass.getMethod("setParent", Screen::class.java).invoke(editor, parent)
            Minecraft.getInstance().gui.setScreen(editor)
            true
        } catch (e: Throwable) {
            Log.error("Could not open MiniHUD's Shape Editor", e)
            false
        }
    }
}

/** A shape read from MiniHUD: shown, hidden or jumped to here, but edited only in MiniHUD. */
class MiniHudShape(
    override val id: String,
    override val label: String,
    override val dimension: String,
    override val colour: Int,
    override val geometry: Geometry,
    private val type: String,
    val enabledInMiniHud: Boolean,
    /** MiniHUD's own shape object, for shapes in the dimension you are in; null for ones read from a file. */
    val handle: Any? = null,
) : MapShape {
    /** Whether MiniHUD's editor can be opened on it from here. */
    val editable: Boolean get() = handle != null

    override val fill: Boolean get() = false
    override val visible: Boolean
        get() = (enabledInMiniHud || Config.miniHudIncludeDisabled) && id !in ShapeStore.hiddenMiniHud
    override val effectiveLineWidth: Float get() = Config.DEFAULT_LINE_WIDTH * Config.thicknessScale
    override val fromMiniHud: Boolean get() = true
    override val name: String get() = label.ifBlank { typeTitle }

    val typeTitle: String get() = type.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    override fun describeSize(): String {
        val b = bounds
        return when (val g = geometry) {
            is Geometry.Circle -> "MiniHUD $typeTitle, radius ${Shape.number(g.radius)}"
            else -> if (!g.closed) "MiniHUD $typeTitle"
            else "MiniHUD $typeTitle, ${Shape.number(b.maxX - b.minX)} × ${Shape.number(b.maxZ - b.minZ)}"
        }
    }

    override fun describePosition(): String {
        val b = bounds
        return "Centred on ${Math.floor(b.centreX).toInt()}, ${Math.floor(b.centreZ).toInt()}" +
            if (enabledInMiniHud) "" else " (off in MiniHUD)"
    }
}

/** MiniHUD's saved shape JSON to a footprint outline. */
internal object MiniHudGeometry {

    private val SPHERES = setOf(
        "sphere_blocky", "adjustable_spawn_sphere", "can_spawn_sphere", "can_despawn_sphere",
        "despawn_sphere", "clipped_spawn_sphere_y",
    )

    fun of(type: String, json: JsonObject): Geometry? {
        // Boxes, centred boxes included, save their two corners.
        vec(json, "corner1")?.let { a ->
            val b = vec(json, "corner2") ?: return null
            return Geometry.rectangle(minOf(a[0], b[0]), minOf(a[2], b[2]), maxOf(a[0], b[0]), maxOf(a[2], b[2]))
        }
        if (type == "block_line") {
            val start = vec(json, "start") ?: return null
            val end = vec(json, "end") ?: return null
            // Through the middles of the end blocks, as MiniHUD draws it.
            return Geometry.Polygon(doubleArrayOf(start[0] + 0.5, start[2] + 0.5, end[0] + 0.5, end[2] + 0.5), closed = false)
        }
        if (json.has("origin_x")) return tapered(type, json)

        val centre = vec(json, "center") ?: return null
        val cx = centre[0]
        val cz = centre[2]
        val radius = json.get("radius")?.asDouble ?: return null
        if (type == "ellipsoid_spawn") {
            return Geometry.Ellipse(cx, cz, radius, json.get("radius_z")?.asDouble ?: radius)
        }
        if (type in SPHERES) return Geometry.Circle(cx, cz, radius)

        // Circles, squares and rhombuses are prisms along their main axis: seen from above, an
        // upright one is its outline and one lying down is a rectangle.
        val axis = json.get("main_axis")?.asString ?: "UP"
        val height = json.get("height")?.asDouble ?: 1.0
        if (axis == "UP" || axis == "DOWN") {
            return when (type) {
                "square" -> Geometry.rectangle(cx - radius, cz - radius, cx + radius, cz + radius)
                "rhombus" -> Geometry.rhombus(cx, cz, radius)
                else -> Geometry.Circle(cx, cz, radius)
            }
        }
        return along(axis, cx, cz, radius, radius, height)
    }

    /** Cones and pyramids: an upright one is its wider end's outline; one lying down, a trapezoid. */
    private fun tapered(type: String, json: JsonObject): Geometry? {
        val cx = json.get("origin_x")?.asDouble ?: return null
        val cz = json.get("origin_z")?.asDouble ?: return null
        val bottom = json.get("bottom_radius")?.asDouble ?: 0.0
        val top = json.get("top_radius")?.asDouble ?: 0.0
        val height = json.get("height")?.asDouble ?: 1.0
        val direction = json.get("direction")?.asString ?: "UP"
        if (direction == "UP" || direction == "DOWN") {
            val r = maxOf(bottom, top)
            return when (type) {
                "pyramid" -> Geometry.rectangle(cx - r, cz - r, cx + r, cz + r)
                "diamond_pyramid" -> Geometry.rhombus(cx, cz, r)
                "octagon_pyramid" -> Geometry.octagon(cx, cz, r)
                else -> Geometry.Circle(cx, cz, r)
            }
        }
        return along(direction, cx, cz, bottom, top, height)
    }

    /** A shape lying along [direction] from ([cx], [cz]): [startRadius] wide there, [endRadius] at [length]. */
    private fun along(direction: String, cx: Double, cz: Double, startRadius: Double, endRadius: Double, length: Double): Geometry {
        val (dx, dz) = when (direction) {
            "NORTH" -> 0.0 to -1.0
            "SOUTH" -> 0.0 to 1.0
            "WEST" -> -1.0 to 0.0
            else -> 1.0 to 0.0
        }
        // Sideways from the direction.
        val sx = -dz
        val sz = dx
        val ex = cx + dx * length
        val ez = cz + dz * length
        return Geometry.Polygon(doubleArrayOf(
            cx + sx * startRadius, cz + sz * startRadius,
            ex + sx * endRadius, ez + sz * endRadius,
            ex - sx * endRadius, ez - sz * endRadius,
            cx - sx * startRadius, cz - sz * startRadius,
        ))
    }

    /** One of this mod's shapes as MiniHUD's saved JSON, at height [y]; null for one MiniHUD has no type for. */
    fun toMiniHudJson(shape: Shape, y: Int): JsonObject? {
        val b = shape.bounds
        val json = JsonObject()
        json.addProperty("display_name", shape.name)
        json.addProperty("enabled", true)
        // MiniHUD shades a shape's sides with this colour; its own new shapes are part transparent.
        json.addProperty("color", (shape.colour and 0xFFFFFF) or (0x60 shl 24))
        json.addProperty("color_lines", shape.colour)
        json.addProperty("render_type", "outer_edge")
        val centre = JsonArray().apply { add(b.centreX); add(y.toDouble()); add(b.centreZ) }
        when (shape.type) {
            Shape.Type.CIRCLE -> {
                json.addProperty("type", "sphere_blocky")
                json.add("center", centre)
                json.addProperty("radius", shape.radius)
                json.addProperty("main_axis", "UP")
            }
            Shape.Type.ELLIPSE -> {
                json.addProperty("type", "ellipsoid_spawn")
                json.add("center", centre)
                json.addProperty("radius", shape.width / 2)
                json.addProperty("radius_y", maxOf(shape.width, shape.length) / 2)
                json.addProperty("radius_z", shape.length / 2)
                json.addProperty("main_axis", "UP")
            }
            Shape.Type.SQUARE, Shape.Type.RHOMBUS -> {
                json.addProperty("type", if (shape.type == Shape.Type.SQUARE) "square" else "rhombus")
                json.add("center", centre)
                json.addProperty("radius", shape.radius)
                json.addProperty("main_axis", "UP")
                json.addProperty("height", MiniHudShapes.PRISM_HEIGHT)
            }
            // MiniHUD has no octagon prism, but a pyramid with the same radius top and bottom is one.
            Shape.Type.OCTAGON -> {
                json.addProperty("type", "octagon_pyramid")
                json.addProperty("origin_x", b.centreX)
                json.addProperty("origin_y", (y - MiniHudShapes.PRISM_HEIGHT / 2).toDouble())
                json.addProperty("origin_z", b.centreZ)
                json.addProperty("bottom_radius", shape.radius)
                json.addProperty("top_radius", shape.radius)
                json.addProperty("direction", "UP")
                json.addProperty("height", MiniHudShapes.PRISM_HEIGHT)
            }
            Shape.Type.RECTANGLE -> {
                json.addProperty("type", "box")
                json.add("corner1", JsonArray().apply { add(b.minX); add((y - MiniHudShapes.PRISM_HEIGHT / 2).toDouble()); add(b.minZ) })
                json.add("corner2", JsonArray().apply { add(b.maxX); add((y + MiniHudShapes.PRISM_HEIGHT / 2).toDouble()); add(b.maxZ) })
            }
        }
        return json
    }

    private fun vec(json: JsonObject, key: String): DoubleArray? {
        val array = json.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        if (array.size() < 3) return null
        return doubleArrayOf(array[0].asDouble, array[1].asDouble, array[2].asDouble)
    }

    private val SIZE_KEYS = listOf(
        "center", "radius", "radius_y", "radius_z", "main_axis", "height", "corner1", "corner2", "start", "end",
        "origin_x", "origin_y", "origin_z", "bottom_radius", "top_radius", "direction",
    )

    /** What makes a shape where and how big it is, as text, for an id that survives re-reading. */
    fun identity(json: JsonObject): String =
        SIZE_KEYS.mapNotNull { key -> json.get(key)?.let { value -> "$key=${text(value)}" } }.joinToString(";")

    private fun text(value: JsonElement): String =
        if (value is JsonArray) value.joinToString(",") { it.asString } else value.asString
}
