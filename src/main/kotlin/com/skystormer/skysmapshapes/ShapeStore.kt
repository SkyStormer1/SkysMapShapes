package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

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
        private set(value) { field = value; version++ }

    /**
     * Goes up whenever the shapes or the hidden and shown notes are replaced, so lists made from
     * them can be kept until it changes.
     */
    @Volatile
    var version = 0
        private set

    /** Ids of MiniHUD shapes hidden on the map, whether or not they are on in MiniHUD. */
    @Volatile
    var hiddenMiniHud: Set<String> = emptySet()
        private set(value) { field = value; version++ }

    /** Ids of MiniHUD shapes shown on the map although they are switched off in MiniHUD. */
    @Volatile
    var shownMiniHud: Set<String> = emptySet()
        private set(value) { field = value; version++ }

    /**
     * What Hide all hid, so Show all brings back only those: each shape's id, with "map" when it
     * was hidden on the map and "minihud" when it was switched off in MiniHUD (or both).
     */
    var hiddenByHideAll: Map<String, Set<String>> = emptyMap()
        private set

    /**
     * What each shape does to MiniHUD's light levels, by id, for shapes not left at
     * [LightLevels.Role.SHOW]. Read on the render thread, so replaced whole.
     */
    @Volatile
    private var lightRoles: Map<String, LightLevels.Role> = emptyMap()

    /** The ids of shapes whose spawnable floors [SpawnProofing] covers. Read on the render thread, so replaced whole. */
    @Volatile
    private var spawnProofIds: Set<String> = emptySet()

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
        val path = FabricLoader.getInstance().configDir.resolve("skysmapshapes").resolve("$key.json")
        file = path
        val json = parse(path)
        all = read(json, path)
        readNotes(json, path)
        Log.info("Loaded {} shape(s) for {} from {}", all.size, name, path)
    }

    /**
     * Drops notes about hidden MiniHUD shapes that are no longer there. A shape's id says where
     * and how big it is, so editing it in MiniHUD gives it a new one and leaves the old note
     * behind; without this they would pile up in the file forever.
     */
    fun pruneHidden(known: Set<String>) {
        if (known.isEmpty()) return
        val hidden = hiddenMiniHud.intersect(known)
        val shown = shownMiniHud.intersect(known)
        // Hide all's notes on this mod's own shapes are pruned as those shapes are deleted.
        val remembered = hiddenByHideAll.filterKeys { !it.startsWith(MINIHUD_ID) || it in known }
        val roles = lightRoles.filterKeys { !it.startsWith(MINIHUD_ID) || it in known }
        val proofed = spawnProofIds.filterTo(HashSet()) { !it.startsWith(MINIHUD_ID) || it in known }
        if (hidden.size != hiddenMiniHud.size || shown.size != shownMiniHud.size || remembered.size != hiddenByHideAll.size ||
            roles.size != lightRoles.size || proofed.size != spawnProofIds.size
        ) {
            hiddenMiniHud = hidden
            shownMiniHud = shown
            hiddenByHideAll = remembered
            lightRoles = roles
            spawnProofIds = proofed
            save()
        }
    }

    fun close() {
        file = null
        worldName = null
        worldKey = null
        all = emptyList()
        hiddenMiniHud = emptySet()
        shownMiniHud = emptySet()
        hiddenByHideAll = emptyMap()
        lightRoles = emptyMap()
        spawnProofIds = emptySet()
    }

    /** Whether the shape with [id] is spawn-proofed. */
    fun spawnProof(id: String): Boolean = id in spawnProofIds

    fun setSpawnProof(id: String, on: Boolean) {
        spawnProofIds = if (on) spawnProofIds + id else spawnProofIds - id
        save()
        SpawnProofing.refresh()
    }

    /** What the shape with [id] does to MiniHUD's light levels. */
    fun lightRole(id: String): LightLevels.Role = lightRoles[id] ?: LightLevels.Role.SHOW

    fun setLightRole(id: String, role: LightLevels.Role) {
        lightRoles = if (role == LightLevels.Role.SHOW) lightRoles - id else lightRoles + (id to role)
        save()
        LightLevels.refresh()
    }

    /**
     * Whether a MiniHUD shape is drawn on the map: as it was last shown or hidden here, or else
     * as it is in MiniHUD (shapes switched off there only drawn when the settings ask).
     */
    fun miniHudOnMap(id: String, enabledInMiniHud: Boolean): Boolean = when (id) {
        in hiddenMiniHud -> false
        in shownMiniHud -> true
        else -> enabledInMiniHud || Config.miniHudIncludeDisabled
    }

    /**
     * Shows or hides [shape] on the map only. One of MiniHUD's is left as it is in MiniHUD, so
     * it stays in (or out of) the world.
     */
    fun setVisible(shape: MapShape, visible: Boolean) {
        when (shape) {
            is Shape -> byId(shape.id)?.let { put(it.copy(visible = visible)) }
            is MiniHudShape -> {
                noteOnMap(shape.id, visible, shape.enabledInMiniHud)
                save()
            }
            else -> {}
        }
    }

    /** Remembers whether a MiniHUD shape is on the map, noting only what differs from MiniHUD. */
    private fun noteOnMap(id: String, visible: Boolean, enabledInMiniHud: Boolean) {
        hiddenMiniHud = hiddenMiniHud - id
        shownMiniHud = shownMiniHud - id
        if (visible == miniHudOnMap(id, enabledInMiniHud)) return
        if (visible) shownMiniHud = shownMiniHud + id else hiddenMiniHud = hiddenMiniHud + id
    }

    /**
     * Switches [shape] on or off in MiniHUD, so it comes into or goes from the world, leaving it
     * on the map or off it as it was. Says whether MiniHUD took the change.
     */
    fun setInMiniHud(shape: MiniHudShape, on: Boolean): Boolean {
        val onMap = shape.visible
        // Switching on a shape MiniHUD cannot draw would crash the game, however it was made.
        if (on) shape.asOwnShape()?.let { MiniHudGeometry.whyTooBig(it, MiniHudGeometry.formOfType(shape.typeId)) }?.let { why ->
            Log.info("Left {} off in MiniHUD: {}", shape.name, why)
            MapMenus.say(why.substringBefore(" It stays") + " It stays off in MiniHUD.")
            return false
        }
        if (!MiniHudShapes.setEnabled(shape, on)) return false
        noteOnMap(shape.id, onMap, on)
        save()
        return true
    }

    /** Takes a deleted MiniHUD shape's notes away with it. */
    fun forgetMiniHud(id: String) {
        hiddenMiniHud = hiddenMiniHud - id
        shownMiniHud = shownMiniHud - id
        hiddenByHideAll = hiddenByHideAll - id
        lightRoles = lightRoles - id
        spawnProofIds = spawnProofIds - id
        save()
    }

    /**
     * Hides every one of [shapes] that is showing, and remembers which, so [showAll] brings back
     * only those. With [inMiniHudToo], MiniHUD's shapes are switched off in MiniHUD as well.
     */
    fun hideAll(shapes: List<MapShape>, inMiniHudToo: Boolean) = batch {
        val remembered = hiddenByHideAll.toMutableMap()
        for (shape in shapes) {
            val did = HashSet<String>()
            var switched: MapShape? = null
            if (shape is MiniHudShape && inMiniHudToo && shape.enabledInMiniHud && shape.changeable &&
                setInMiniHud(shape, false)) {
                did += IN_MINIHUD
                switched = shape.withEnabled(false)
            }
            // Switching it off in MiniHUD gave it a new state; MiniHUD is not read again until the end.
            val now = switched ?: latest(shape)
            if (now.visible) {
                setVisible(now, false)
                did += ON_MAP
            }
            if (did.isNotEmpty()) remembered[shape.id] = remembered[shape.id].orEmpty() + did
        }
        hiddenByHideAll = remembered
        save()
    }

    /**
     * Brings back what [hideAll] hid among [shapes], and only that. When it hid none of them,
     * shows every one of them on the map instead (and in MiniHUD with [inMiniHudToo]).
     * Says how many it brought back.
     */
    fun showAll(shapes: List<MapShape>, inMiniHudToo: Boolean): Int = batch {
        val remembered = shapes.filter { it.id in hiddenByHideAll }
        if (remembered.isEmpty()) {
            for (shape in shapes) {
                var switched: MapShape? = null
                if (shape is MiniHudShape && inMiniHudToo && !shape.enabledInMiniHud && shape.changeable &&
                    setInMiniHud(shape, true)) switched = shape.withEnabled(true)
                val now = switched ?: latest(shape)
                if (!now.visible) setVisible(now, true)
            }
            return@batch shapes.size
        }
        for (shape in remembered) {
            val did = hiddenByHideAll[shape.id].orEmpty()
            var switched: MapShape? = null
            if (shape is MiniHudShape && IN_MINIHUD in did && setInMiniHud(shape, true)) switched = shape.withEnabled(true)
            val now = switched ?: latest(shape)
            if (ON_MAP in did) setVisible(now, true)
        }
        hiddenByHideAll = hiddenByHideAll - remembered.map { it.id }.toSet()
        save()
        remembered.size
    }

    /** [shape] as last read: one of MiniHUD's gets a new state each time it is switched on or off there. */
    private fun latest(shape: MapShape): MapShape =
        if (shape is MiniHudShape) MiniHudShapes.all.firstOrNull { it.id == shape.id } ?: shape else shape

    /** How many of [shapes] Hide all hid and Show all would bring back. */
    fun hiddenByHideAll(shapes: List<MapShape>): Int = shapes.count { it.id in hiddenByHideAll }

    /** The shapes in [dimension], in no particular order; [MapShapes.visibleIn] gives the drawing order. */
    fun inDimension(dimension: String): List<Shape> = all.filter { it.dimension == dimension }

    fun byId(id: String): Shape? = all.firstOrNull { it.id == id }

    /** Adds [shape], or replaces the one with its id. */
    fun put(shape: Shape) {
        val index = all.indexOfFirst { it.id == shape.id }
        all = if (index < 0) all + shape else all.toMutableList().also { it[index] = shape }
        save()
    }

    fun remove(id: String) {
        all = all.filter { it.id != id }
        hiddenByHideAll = hiddenByHideAll - id
        lightRoles = lightRoles - id
        spawnProofIds = spawnProofIds - id
        save()
    }

    /**
     * A display name and a file name for the world: the server address as typed in the server
     * list, or the single-player world's name.
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

    private const val MINIHUD_ID = "minihud:"
    private const val ON_MAP = "map"
    private const val IN_MINIHUD = "minihud"

    private fun safe(text: String): String = text.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "_" }

    /**
     * The world file at [path], read once for both [read] and [readNotes]; null when there is none
     * yet, or when it cannot be read at all, which first copies it aside.
     */
    private fun parse(path: Path): JsonObject? {
        if (!Files.exists(path)) return null
        return try {
            Files.newBufferedReader(path).use { JsonParser.parseReader(it) }.asJsonObject
        } catch (e: Exception) {
            Log.error("Could not read $path", e)
            keepCopy(path)
            null
        }
    }

    /**
     * The shapes in [json], read from [path]. Anything that cannot be read is skipped, and the file
     * is first copied aside, because the next save writes only what was read and would lose the
     * rest for good.
     */
    private fun read(json: JsonObject?, path: Path): List<Shape> {
        json ?: return emptyList()
        return try {
            var skipped = false
            val shapes = json.getAsJsonArray("shapes")?.mapNotNull { element ->
                try {
                    fromJson(element.asJsonObject).also { require(it.isValid()) { "a size, place or dimension no shape can have" } }
                } catch (e: Exception) {
                    Log.warn("Skipping a shape in {} that could not be read: {}", path, e.toString())
                    skipped = true
                    null
                }
            } ?: emptyList()
            if (skipped) keepCopy(path)
            shapes
        } catch (e: Exception) {
            Log.error("Could not read $path", e)
            keepCopy(path)
            emptyList()
        }
    }

    /** Copies [path] beside itself, named for when, so nothing in it is lost. */
    private fun keepCopy(path: Path) {
        try {
            val copy = path.resolveSibling("${path.fileName}.unreadable-${System.currentTimeMillis()}")
            Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING)
            Log.warn("Kept a copy of {} as {}", path.fileName, copy.fileName)
        } catch (e: Exception) {
            Log.error("Could not keep a copy of $path", e)
        }
    }

    /**
     * The notes kept beside the shapes: which MiniHUD shapes are shown or hidden, what Hide all
     * hid, what shapes do to light levels, and which are spawn-proofed.
     */
    private fun readNotes(json: JsonObject?, path: Path) {
        hiddenMiniHud = emptySet()
        shownMiniHud = emptySet()
        hiddenByHideAll = emptyMap()
        lightRoles = emptyMap()
        spawnProofIds = emptySet()
        json ?: return
        try {
            hiddenMiniHud = json.getAsJsonArray("hiddenMiniHud")?.map { it.asString }?.toSet() ?: emptySet()
            shownMiniHud = json.getAsJsonArray("shownMiniHud")?.map { it.asString }?.toSet() ?: emptySet()
            hiddenByHideAll = json.getAsJsonObject("hiddenByHideAll")?.entrySet()
                ?.associate { (id, did) -> id to did.asJsonArray.map { it.asString }.toSet() } ?: emptyMap()
            lightRoles = json.getAsJsonObject("lightLevels")?.entrySet()
                ?.mapNotNull { (id, role) -> LightLevels.Role.fromSaved(role.asString)?.let { id to it } }?.toMap() ?: emptyMap()
            spawnProofIds = json.getAsJsonArray("spawnProof")?.map { it.asString }?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.warn("Could not read the shown and hidden notes in {}: {}", path, e.toString())
        }
    }

    /** Above 0 while [batch] runs: saving waits until it ends. */
    private var batching = 0
    private var savePending = false

    /**
     * Runs [block], in which any number of shapes can be changed, saving the file once and
     * reading MiniHUD once at the end rather than after every shape.
     */
    private fun <T> batch(block: () -> T): T {
        batching++
        try {
            return MiniHudShapes.batch(block)
        } finally {
            batching--
            if (batching == 0 && savePending) {
                savePending = false
                save()
            }
        }
    }

    private fun save() {
        if (batching > 0) {
            savePending = true
            return
        }
        val path = file ?: return
        try {
            val array = JsonArray()
            all.forEach { array.add(toJson(it)) }
            val json = JsonObject()
            json.addProperty("version", 1)
            json.add("shapes", array)
            if (hiddenMiniHud.isNotEmpty()) json.add("hiddenMiniHud", JsonArray().also { a -> hiddenMiniHud.sorted().forEach(a::add) })
            if (shownMiniHud.isNotEmpty()) json.add("shownMiniHud", JsonArray().also { a -> shownMiniHud.sorted().forEach(a::add) })
            if (hiddenByHideAll.isNotEmpty()) json.add("hiddenByHideAll", JsonObject().also { o ->
                hiddenByHideAll.toSortedMap().forEach { (id, did) -> o.add(id, JsonArray().also { a -> did.sorted().forEach(a::add) }) }
            })
            if (lightRoles.isNotEmpty()) json.add("lightLevels", JsonObject().also { o ->
                lightRoles.toSortedMap().forEach { (id, role) -> o.addProperty(id, role.saved) }
            })
            if (spawnProofIds.isNotEmpty()) json.add("spawnProof", JsonArray().also { a -> spawnProofIds.sorted().forEach(a::add) })
            SafeFiles.writeString(path, GSON.toJson(json))
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
        id = json.get("id")?.asString ?: UUID.randomUUID().toString(),
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
