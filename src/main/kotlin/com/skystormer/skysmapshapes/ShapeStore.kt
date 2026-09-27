package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path

/**
 * The shapes for the server or single-player world you are in: one file per server in
 * `config/skysmapshapes/`, named after its address, holding every dimension's shapes. Loaded on
 * joining, saved on every change.
 */
object ShapeStore {

    private val GSON = GsonBuilder().setPrettyPrinting().create()

    /** The file for the world you are in, or null when not in one. */
    private var file: Path? = null

    /** Which server or world, for the shapes list's title. */
    var worldName: String? = null
        private set

    /**
     * The world as a file name: the server address with anything awkward replaced, which is how
     * MiniHUD names its files too. Null when not in a world.
     */
    var worldKey: String? = null
        private set

    /** Read by the render thread every frame, so replaced whole rather than changed in place. */
    @Volatile
    var all: List<Shape> = emptyList()
        private set

    /** Ids of MiniHUD shapes hidden on the map. MiniHUD itself is never changed. */
    @Volatile
    var hiddenMiniHud: Set<String> = emptySet()
        private set

    val isOpen: Boolean get() = file != null

    fun open(minecraft: Minecraft) {
        val world = worldOf(minecraft)
        if (world == null) {
            Log.warn("Joined a world with no server address or single-player folder; shapes are off")
            return close()
        }
        val (name, key) = world
        worldName = name
        worldKey = key
        file = FabricLoader.getInstance().configDir.resolve("skysmapshapes").resolve("$key.json")
        all = read(file!!)
        hiddenMiniHud = readHidden(file!!)
        Log.info("Loaded {} shape(s) for {} from {}", all.size, name, file)
    }

    /**
     * Drops notes about hidden MiniHUD shapes that are no longer there. A shape's id says where
     * and how big it is, so editing it in MiniHUD gives it a new one and leaves the old note
     * behind; without this they would pile up in the file forever.
     */
    fun pruneHidden(known: Set<String>) {
        if (hiddenMiniHud.isEmpty() || known.isEmpty()) return
        val kept = hiddenMiniHud.intersect(known)
        if (kept.size != hiddenMiniHud.size) {
            hiddenMiniHud = kept
            save()
        }
    }

    fun close() {
        file = null
        worldName = null
        worldKey = null
        all = emptyList()
        hiddenMiniHud = emptySet()
    }

    /** Shows or hides [shape] on the map: for one of ours, its own setting; for MiniHUD's, a note kept here. */
    fun setVisible(shape: MapShape, visible: Boolean) {
        when (shape) {
            is Shape -> byId(shape.id)?.let { put(it.copy(visible = visible)) }
            // Hiding one of MiniHUD's normally switches it off in MiniHUD too, so it goes from the
            // world as well; if that is off, or it is in another dimension, it is hidden here only.
            is MiniHudShape -> {
                val inMiniHud = Config.hideInMiniHud && MiniHudShapes.setEnabled(shape, visible)
                if (inMiniHud) {
                    if (visible) hiddenMiniHud = hiddenMiniHud - shape.id
                } else {
                    hiddenMiniHud = if (visible) hiddenMiniHud - shape.id else hiddenMiniHud + shape.id
                }
                save()
            }
            else -> {}
        }
    }

    /** Hides a MiniHUD shape on the map only, leaving MiniHUD alone. */
    fun hideOnMapOnly(shape: MapShape) {
        hiddenMiniHud = hiddenMiniHud + shape.id
        save()
    }

    /**
     * The shapes in [dimension], largest first: everything is drawn in this order, so a smaller
     * shape always shows on top of a larger one it overlaps.
     */
    fun inDimension(dimension: String): List<Shape> = all.filter { it.dimension == dimension }.sortedByDescending { it.area }

    fun byId(id: String): Shape? = all.firstOrNull { it.id == id }

    /** Adds [shape], or replaces the one with its id. */
    fun put(shape: Shape) {
        val index = all.indexOfFirst { it.id == shape.id }
        all = if (index < 0) all + shape else all.toMutableList().also { it[index] = shape }
        save()
    }

    fun remove(id: String) {
        all = all.filter { it.id != id }
        save()
    }

    /**
     * A display name and a file name for the world: the server address as typed in the server
     * list, or the single-player world's folder.
     */
    private fun worldOf(minecraft: Minecraft): Pair<String, String>? {
        minecraft.currentServer?.ip?.let { address ->
            val name = address.trim().lowercase()
            return name to safe(name)
        }
        val server = minecraft.singleplayerServer ?: return null
        val folder = server.worldData.levelName
        return folder to "singleplayer-${safe(folder)}"
    }

    private fun safe(text: String): String = text.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "_" }

    private fun read(path: Path): List<Shape> {
        if (!Files.exists(path)) return emptyList()
        return try {
            val json = Files.newBufferedReader(path).use { JsonParser.parseReader(it) }.asJsonObject
            json.getAsJsonArray("shapes")?.mapNotNull { element ->
                try {
                    fromJson(element.asJsonObject)
                } catch (e: Exception) {
                    Log.warn("Skipping a shape in {} that could not be read: {}", path, e.toString())
                    null
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.error("Could not read $path", e)
            emptyList()
        }
    }

    private fun readHidden(path: Path): Set<String> = try {
        if (!Files.exists(path)) emptySet()
        else Files.newBufferedReader(path).use { JsonParser.parseReader(it) }.asJsonObject
            .getAsJsonArray("hiddenMiniHud")?.map { it.asString }?.toSet() ?: emptySet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun save() {
        val path = file ?: return
        try {
            val array = JsonArray()
            all.forEach { array.add(toJson(it)) }
            val json = JsonObject()
            json.addProperty("version", 1)
            json.add("shapes", array)
            if (hiddenMiniHud.isNotEmpty()) json.add("hiddenMiniHud", JsonArray().also { a -> hiddenMiniHud.sorted().forEach(a::add) })
            Files.createDirectories(path.parent)
            Files.writeString(path, GSON.toJson(json))
        } catch (e: Exception) {
            Log.error("Could not save $path", e)
        }
    }

    private fun toJson(shape: Shape) = JsonObject().apply {
        addProperty("id", shape.id)
        addProperty("label", shape.label)
        addProperty("dimension", shape.dimension)
        addProperty("type", shape.type.name.lowercase())
        addProperty("x", shape.x)
        addProperty("z", shape.z)
        shape.y?.let { addProperty("y", it) }
        when (shape.type.sized) {
            Shape.Sized.RADIUS -> addProperty("radius", shape.radius)
            Shape.Sized.WIDTH_LENGTH -> {
                addProperty("width", shape.width)
                addProperty("length", shape.length)
                if (shape.type == Shape.Type.RECTANGLE) addProperty("anchor", shape.anchor.name.lowercase())
            }
        }
        addProperty("colour", Colours.format(shape.colour))
        addProperty("fill", shape.fill)
        addProperty("visible", shape.visible)
        addProperty("lineWidth", shape.lineWidth)
    }

    private fun fromJson(json: JsonObject) = Shape(
        id = json.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
        label = json.get("label")?.asString ?: "",
        dimension = json.get("dimension").asString,
        type = Shape.Type.valueOf(json.get("type").asString.uppercase()),
        x = json.get("x").asInt,
        z = json.get("z").asInt,
        y = json.get("y")?.takeIf { !it.isJsonNull }?.asInt,
        radius = json.get("radius")?.asDouble ?: 0.0,
        width = json.get("width")?.asDouble ?: 0.0,
        length = json.get("length")?.asDouble ?: 0.0,
        anchor = json.get("anchor")?.asString?.let { Shape.Anchor.valueOf(it.uppercase()) } ?: Shape.Anchor.CENTRE,
        colour = Colours.parse(json.get("colour")?.asString) ?: Colours.RED.argb,
        fill = json.get("fill")?.asBoolean ?: false,
        visible = json.get("visible")?.asBoolean ?: true,
        lineWidth = (json.get("lineWidth")?.takeIf { !it.isJsonNull }?.asFloat ?: Config.DEFAULT_LINE_WIDTH)
            .coerceIn(Config.MIN_LINE_WIDTH, Config.MAX_LINE_WIDTH),
    )
}
