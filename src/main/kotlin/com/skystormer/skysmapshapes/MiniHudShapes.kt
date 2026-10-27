package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * MiniHUD's shapes, as outlines on the map. Optional: without MiniHUD installed this does nothing.
 *
 * The dimension you are in comes from MiniHUD's live shape list, so a shape added, changed or
 * deleted in MiniHUD shows here within half a second; other dimensions come from the files
 * MiniHUD saves for them. MiniHUD's own renderer being switched off makes no difference: its
 * shapes are still shown on the map.
 *
 * Shapes are only changed in MiniHUD when asked: made, switched on or off, or deleted. In the
 * dimension you are in that goes through MiniHUD's live list. In another it goes into that
 * dimension's file, which is safe because MiniHUD only writes the file of the dimension it is
 * leaving and reads a dimension's file on arriving there, so the change is what it then loads.
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
        private set(value) { field = value; version++ }

    /** Goes up whenever [all] is replaced, so lists made from it can be kept until it changes. */
    @Volatile
    var version = 0
        private set

    /**
     * Ticks between reads that nothing asked for: a safety net for what the triggers miss, such as
     * MiniHUD changing a shape from a hotkey. Ten seconds.
     */
    private const val SAFETY_NET_EVERY = 200

    /** How many ticks the other dimensions stay read after the shapes list last asked for them. */
    private const val ASKED_FOR_TICKS = 40

    private val safetyNet = Every(SAFETY_NET_EVERY)
    private val guard = Guard("MiniHUD's shapes are not shown")

    /** Set by [markDirty]: the next tick reads MiniHUD. Volatile, as MiniHUD may call it from another thread. */
    @Volatile
    private var dirty = true

    /** The dimension the player was in at the last tick, to notice a change. */
    private var lastDimension: String? = null

    /** The dimension the world map was showing at the last tick, while other dimensions were wanted. */
    private var lastMapDimension: String? = null

    /** Whether [all] holds the other dimensions' shapes from MiniHUD's files as well as the live ones. */
    private var othersLoaded = false

    /** Ticks left that the shapes list's request for every dimension still counts. */
    private var askedFor = 0

    /** Above 0 while [batch] runs: file edits and the read after a change wait until it ends. */
    private var batching = 0

    /** MiniHUD's files changed inside a [batch], by file, written once when it ends. */
    private val pendingFiles = LinkedHashMap<Path, JsonObject>()

    private var readAfterBatch = false

    /** Shapes that failed to read, said once each. */
    private val reportedBad = HashSet<String>()
    private val fileCache = HashMap<Path, Pair<Long, List<MiniHudShape>>>()

    private class Api(
        val manager: Any,
        val getAllShapes: Method,
        val export: Method,
        val storageName: Method,
        val addShape: Method,
        val removeShape: Method,
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
                removeShape = managerClass.getMethod("removeShape", shapeClass),
                shapeClass = shapeClass,
            ).also { Log.info("MiniHUD found: its shapes will be shown on the map") }
        } catch (e: Throwable) {
            Log.error("This version of MiniHUD could not be read; its shapes will not be shown", e)
            null
        }
    }

    /**
     * Says MiniHUD's shapes may have changed, so the next tick reads them. Called when a shape is
     * added or removed (see `ShapeManagerMixin`), when one of MiniHUD's shape screens closes, and
     * whenever this mod changes a shape there.
     */
    @JvmStatic
    fun markDirty() {
        dirty = true
    }

    /**
     * Runs [block], in which any number of shapes can be switched on or off, with each of
     * MiniHUD's files written once and MiniHUD read once at the end rather than after every shape.
     */
    fun <T> batch(block: () -> T): T {
        batching++
        try {
            return block()
        } finally {
            batching--
            if (batching == 0) finishBatch()
        }
    }

    private fun finishBatch() {
        for ((file, root) in pendingFiles) {
            try {
                SafeFiles.writeString(file, GSON.toJson(root))
            } catch (e: Exception) {
                Log.error("Could not change MiniHUD's file $file", e)
            }
            fileCache.remove(file)
        }
        pendingFiles.clear()
        if (readAfterBatch) {
            readAfterBatch = false
            tick(Minecraft.getInstance(), force = true)
        }
    }

    /** Reads MiniHUD now, or once the running [batch] ends. */
    private fun readSoon() {
        if (batching > 0) readAfterBatch = true else tick(Minecraft.getInstance(), force = true)
    }

    /**
     * Asked by anything that needs shapes from every dimension, such as the shapes list. Those
     * come from MiniHUD's files, which are only read while something wants them.
     */
    fun wantAllDimensions() {
        if (askedFor == 0 && !othersLoaded) dirty = true
        askedFor = ASKED_FOR_TICKS
    }

    /**
     * Called every client tick. The dimension you are in is read from MiniHUD's live list when
     * something says it changed ([markDirty]), when you change dimension, and every ten seconds as
     * a safety net. The other dimensions' files are read only while the world map shows another
     * dimension or the shapes list asks for them.
     */
    fun tick(minecraft: Minecraft, force: Boolean = false) {
        if (!installed || guard.broken || !Config.enabled || !Config.showMiniHud) {
            if (all.isNotEmpty()) all = emptyList()
            return
        }
        val level = minecraft.level ?: run { if (all.isNotEmpty()) all = emptyList(); return }
        val here = level.dimension().identifier().toString()
        if (here != lastDimension) {
            lastDimension = here
            dirty = true
        }
        val mapDimension = Dimensions.ofMap()
        if (askedFor > 0) askedFor--
        val wantOthers = askedFor > 0 || (mapDimension != null && mapDimension != here)
        // The world map being switched to another dimension needs that dimension's file read.
        val mapMoved = wantOthers && mapDimension != lastMapDimension
        lastMapDimension = if (wantOthers) mapDimension else null
        if (wantOthers != othersLoaded || mapMoved) dirty = true
        if (safetyNet.due()) dirty = true
        if (!dirty && !force) return
        dirty = false
        val api = api ?: run { guard.stop(); return }
        guard.run("read MiniHUD's shapes", Unit) {
            val live = ArrayList<MiniHudShape>()
            for (shape in api.getAllShapes.invoke(api.manager) as List<*>) {
                // One odd shape is skipped and said once; it does not stop the rest being read.
                try {
                    (api.export.invoke(api.manager, shape) as? JsonElement)?.takeIf { it.isJsonObject }
                        ?.let { parse(it.asJsonObject, here, shape) }?.let { live += it }
                } catch (e: Throwable) {
                    val why = (e.cause ?: e).toString()
                    if (reportedBad.add(why)) Log.error("Could not read one of MiniHUD's shapes; it is left out ($why)", e)
                }
            }
            othersLoaded = wantOthers
            all = if (wantOthers) live + otherDimensions(api, here) else live
        }
        if (guard.broken) all = emptyList()
    }

    /**
     * The ids of every shape MiniHUD has for this world, other dimensions' files included whether
     * or not they were being read, for dropping notes about shapes that are gone. Empty when that
     * cannot be told.
     */
    fun idsForPruning(): Set<String> {
        val api = api ?: return emptySet()
        val here = lastDimension ?: return emptySet()
        if (all.isEmpty()) return emptySet()
        return try {
            val others = if (othersLoaded) all.filter { it.file != null } else otherDimensions(api, here)
            (all.filter { it.file == null } + others).mapTo(HashSet()) { it.id }
        } catch (e: Throwable) {
            emptySet()
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
        safetyNet.restart()
        dirty = true
        lastDimension = null
        lastMapDimension = null
        othersLoaded = false
        askedFor = 0
        reportedPrefix = null
    }

    /**
     * The shapes MiniHUD saved for this server's other dimensions, so the world map shows them
     * whichever dimension it is switched to. MiniHUD keeps one file per dimension, named
     * `<server>_dim_<namespace>_<path>.json`, and only holds the one you are in.
     */
    private fun otherDimensions(api: Api, here: String): List<MiniHudShape> {
        val folder = FabricLoader.getInstance().configDir.resolve("minihud")
        if (!Files.isDirectory(folder)) return emptyList()
        val prefix = filePrefix(api) ?: return emptyList()
        val hereName = prefix + here.replace(':', '_') + ".json"
        val result = ArrayList<MiniHudShape>()
        var files = 0
        Files.list(folder).use { paths ->
            for (file in paths) {
                val name = file.fileName.toString()
                // The dimension you are in comes from MiniHUD itself, live, so its file is skipped.
                if (!name.startsWith(prefix) || !name.endsWith(".json") || name == hereName) continue
                files++
                result += readFile(file, dimensionFromFile(name.removePrefix(prefix).removeSuffix(".json")))
            }
        }
        if (reportedPrefix != prefix) {
            reportedPrefix = prefix
            Log.info("MiniHUD shapes for other dimensions: {} file(s) beginning {}, {} shape(s)", files, prefix, result.size)
        }
        return result
    }

    /** The prefix already reported to the log, so it is said once per world rather than twice a second. */
    private var reportedPrefix: String? = null

    /**
     * What MiniHUD's files for this world are called, up to and including `_dim_`: asked of MaLiLib,
     * which names them, and worked out from the server address if that ever stops answering.
     */
    private fun filePrefix(api: Api): String? {
        // MaLiLib's flag asks for the world's *global* name; false gives the one per dimension,
        // `<server>_dim_<namespace>_<path>.json`, which is how the shape files are named.
        val named = try {
            api.storageName.invoke(null, false, "", ".json", "minihud_default") as? String
        } catch (e: Throwable) {
            Log.warn("Could not ask MiniHUD what its files are called: {}", e.toString())
            null
        }
        if (named != null) {
            // Normally `<server>_dim_<dimension>.json`; a MiniHUD that stops adding the dimension
            // would give `<server>.json`, and the files beside it still carry it.
            val base = if ("_dim_" in named) named.substringBefore("_dim_") else named.removeSuffix(".json")
            if (base.isNotEmpty()) return base + "_dim_"
        }
        return ShapeStore.worldKey?.let { it + "_dim_" }
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
                it.takeIf { e -> e.isJsonObject }?.let { e -> parse(e.asJsonObject, dimension, file = file) }
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
    fun parse(json: JsonObject, dimension: String, handle: Any? = null, file: Path? = null): MiniHudShape? {
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
        return MiniHudShape(id, label, dimension, colour, geometry, type, enabled, MiniHudGeometry.heightOf(json), handle, file,
            MiniHudGeometry.volumeOf(type, json), MiniHudGeometry.sizes(json))
    }

    /**
     * Makes [shape] in MiniHUD, at height [y], as if it had been added in MiniHUD's own shape
     * list: it is then a MiniHUD shape, in the world as well as on the map, and this mod keeps no
     * copy of it. In the dimension you are in it goes straight into MiniHUD; in another, into
     * that dimension's file, and MiniHUD has it when you next go there.
     *
     * A flat map shape has to be given a height, and [form] says how it stands up: spheres and
     * ellipsoids are centred on [y], cylinders, prisms and boxes run 128 blocks above and below
     * it, and cones and pyramids rise from it. All of that can be changed afterwards in MiniHUD's editor.
     *
     * Says why not when MiniHUD did not take it, so the caller keeps its own copy; null when it did.
     * MiniHUD quietly drops a size over its limits, so the shape is read back before it is added,
     * and one that came out a different size is not added at all.
     */
    internal fun create(shape: Shape, y: Int, typeId: String? = null, form: MiniHudGeometry.Form? = null, sizes: JsonObject? = null): String? {
        val api = api ?: return "This version of MiniHUD could not be read."
        // Checked and made as the kind MiniHUD will really make, which for a shared shape may be
        // another form: a shared sphere made as a circle and then turned into one kept the circle's
        // height and base, so MiniHUD's sphere seemed the wrong size and stood 128 blocks low.
        val asForm = typeId?.let { MiniHudGeometry.formOfType(it) } ?: form
        MiniHudGeometry.whyTooBig(shape, asForm)?.let { return it }
        val here = Dimensions.ofPlayer() == shape.dimension
        val file = if (here) null else fileFor(shape.dimension)
            ?: return "MiniHUD's files for this world could not be found, so it stays on this mod's map."
        return try {
            // A shared MiniHUD shape is made again as the kind it was, at the sizes it had.
            val json = MiniHudGeometry.forMiniHud(shape, y, typeId, form, sizes) ?: return "MiniHUD has no shape like that."
            // Checked again as it will really be made: shared sizes can make it far bigger than its
            // outline, and MiniHUD crashes the game on a shape too big rather than refusing it.
            MiniHudGeometry.whyTooBig(json)?.let { return it }
            val kind = json.get("type").asString
            val typeClass = Class.forName("fi.dy.masa.minihud.renderer.shapes.ShapeType")
            val type = typeClass.getMethod("fromString", String::class.java).invoke(null, kind)
                ?: return "This MiniHUD has no $kind shape.".also { Log.warn("MiniHUD has no shape type '{}'", kind) }
            val made = typeClass.getMethod("createShape").invoke(type)
            api.shapeClass.getMethod("fromJson", JsonObject::class.java).invoke(made, json)
            val took = api.shapeClass.getMethod("toJson").invoke(made) as JsonObject
            if (!MiniHudGeometry.tookSize(json, took)) {
                Log.warn("MiniHUD did not take {} at its size: asked for {}, got {}", shape.name, json, took)
                return "MiniHUD would not take it at its full size, so it stays on this mod's map."
            }
            if (file == null) {
                api.addShape.invoke(api.manager, made)
            } else {
                // Saved as MiniHUD itself would save it, so it loads the same as one made there.
                if (!editFile(file) { shapes -> shapes.add(took); true }) return "MiniHUD's file could not be written; the log says why."
                Log.info("Added {} to MiniHUD's file for the {}", shape.name, Dimensions.name(shape.dimension))
            }
            readSoon()
            null
        } catch (e: Throwable) {
            Log.error("Could not make ${shape.name} in MiniHUD", e)
            "MiniHUD would not take that shape; the log says why."
        }
    }

    /** What came of [tryCreate]. */
    sealed interface CreateResult {
        /** It is in MiniHUD now. */
        object Made : CreateResult

        /** It was not made, and [why] says so in a line for the screen. */
        class Refused(val why: String) : CreateResult

        /** Big enough to slow the game: say [warning], and make it only when asked again. */
        class WarnFirst(val warning: String) : CreateResult
    }

    /**
     * The add window's and the full edit screen's "make it in MiniHUD": checked as typed and as
     * MiniHUD will really make it (shared [sizes] can make it far bigger than its outline), then a
     * shape big enough to slow the game is only warned about unless [warned] says that was already
     * done, then [create]. The screens keep their own "press again" state.
     */
    internal fun tryCreate(shape: Shape, y: Int, form: MiniHudGeometry.Form?, typeId: String?, sizes: JsonObject?, warned: Boolean): CreateResult {
        val asForm = typeId?.let { MiniHudGeometry.formOfType(it) } ?: form
        MiniHudGeometry.whyTooBig(shape, asForm)?.let { return CreateResult.Refused(shortened(it)) }
        val json = MiniHudGeometry.forMiniHud(shape, y, typeId, form, sizes)
        json?.let(MiniHudGeometry::whyTooBig)?.let { return CreateResult.Refused(shortened(it)) }
        val warning = json?.let(MiniHudGeometry::lagWarning) ?: MiniHudGeometry.lagWarning(shape, form)
        if (warning != null && !warned) return CreateResult.WarnFirst(warning)
        create(shape, y, typeId, form, sizes)?.let { return CreateResult.Refused(shortened(it)) }
        return CreateResult.Made
    }

    /** A refusal without its "it stays on this mod's map" ending, which does not hold in a screen still open. */
    private fun shortened(why: String): String = why.substringBefore(" It stays").substringBefore(", so it stays")

    /** Whether shapes can be made in MiniHUD for [dimension]: live where you are, else through its file. */
    fun canReach(dimension: String): Boolean =
        installed && api != null && (Dimensions.ofPlayer() == dimension || fileFor(dimension) != null)

    /** MiniHUD's file for [dimension] on this server, whether or not it exists yet. */
    private fun fileFor(dimension: String): Path? {
        val api = api ?: return null
        val prefix = filePrefix(api) ?: return null
        if (!Dimensions.isId(dimension)) return null
        val folder = FabricLoader.getInstance().configDir.resolve("minihud").normalize()
        val file = folder.resolve(prefix + dimension.replace(':', '_') + ".json").normalize()
        // Never anywhere but MiniHUD's own folder, whatever the names it is built from.
        return file.takeIf { it.parent == folder }
    }

    private val GSON = GsonBuilder().setPrettyPrinting().create()

    /**
     * Changes the shapes in one of MiniHUD's dimension files, keeping everything else in it.
     * [change] gets the list of shapes and says whether it changed anything; only then is the
     * file written, through [SafeFiles] so a failure never leaves half a file.
     */
    private fun editFile(file: Path, change: (JsonArray) -> Boolean): Boolean = try {
        // Inside a batch the file is read once and changed in memory, then written when it ends.
        val root = pendingFiles[file]
            ?: if (Files.exists(file)) Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject else JsonObject()
        val holder = root.getAsJsonObject("shapes") ?: JsonObject().also { root.add("shapes", it) }
        val shapes = holder.getAsJsonArray("shapes") ?: JsonArray().also { holder.add("shapes", it) }
        if (change(shapes)) {
            if (batching > 0) {
                pendingFiles[file] = root
            } else {
                SafeFiles.writeString(file, GSON.toJson(root))
                fileCache.remove(file)
            }
        }
        true
    } catch (e: Exception) {
        Log.error("Could not change MiniHUD's file $file", e)
        false
    }

    /** Where [shape] is in a list of MiniHUD's saved shapes, by its id; -1 when it is not there. */
    private fun indexIn(shapes: JsonArray, shape: MiniHudShape): Int = shapes.indexOfFirst { element ->
        element.isJsonObject && parse(element.asJsonObject, shape.dimension)?.id == shape.id
    }

    /**
     * Deletes [shape] from MiniHUD, which takes it off the map too: from MiniHUD's live list in
     * the dimension you are in, from that dimension's file in another.
     */
    fun delete(shape: MiniHudShape): Boolean {
        val api = api ?: return false
        return try {
            val handle = shape.handle
            val file = shape.file
            when {
                handle != null -> api.removeShape.invoke(api.manager, handle)
                file != null -> {
                    var found = false
                    val written = editFile(file) { shapes ->
                        val i = indexIn(shapes, shape)
                        if (i >= 0) { shapes.remove(i); found = true }
                        found
                    }
                    if (!written) return false
                    if (!found) return false.also { Log.warn("{} was not in MiniHUD's file any more", shape.name) }
                }
                else -> return false
            }
            readSoon()
            true
        } catch (e: Throwable) {
            Log.error("Could not delete ${shape.name} from MiniHUD", e)
            false
        }
    }

    /**
     * Switches [shape] on or off in MiniHUD itself, as its own shape list does, so it goes from
     * the world too: live in the dimension you are in, in that dimension's file in another.
     */
    fun setEnabled(shape: MiniHudShape, enabled: Boolean): Boolean {
        val handle = shape.handle
        val file = shape.file
        return try {
            when {
                handle != null -> if (handle.javaClass.getMethod("isEnabled").invoke(handle) as Boolean != enabled) {
                    handle.javaClass.getMethod("toggleEnabled").invoke(handle)
                }
                file != null -> {
                    var found = false
                    val written = editFile(file) { shapes ->
                        val i = indexIn(shapes, shape)
                        if (i >= 0) {
                            found = true
                            val json = shapes[i].asJsonObject
                            json.remove("enabled")
                            json.addProperty("enabled", enabled)
                        }
                        found
                    }
                    if (!written || !found) return false
                }
                else -> return false
            }
            readSoon()
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
