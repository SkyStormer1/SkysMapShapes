package com.skystormer.skysmapshapes

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
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
        return MiniHudShape(id, label, dimension, colour, geometry, type, enabled, MiniHudGeometry.heightOf(json), handle, file)
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
    internal fun create(shape: Shape, y: Int, typeId: String? = null, form: MiniHudGeometry.Form? = null): String? {
        val api = api ?: return "This version of MiniHUD could not be read."
        // Checked as the kind MiniHUD will really make, which for a shared shape may be another form.
        MiniHudGeometry.whyTooBig(shape, typeId?.let { MiniHudGeometry.formOfType(it) } ?: form)?.let { return it }
        val here = Dimensions.ofPlayer() == shape.dimension
        val file = if (here) null else fileFor(shape.dimension)
            ?: return "MiniHUD's files for this world could not be found, so it stays on this mod's map."
        return try {
            val json = MiniHudGeometry.toMiniHudJson(shape, y, form) ?: return "MiniHUD has no shape like that."
            // A shared MiniHUD shape is made again as the kind it was, when that kind fits these fields.
            if (typeId != null) MiniHudGeometry.retype(json, typeId)
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
            tick(Minecraft.getInstance(), force = true)
            null
        } catch (e: Throwable) {
            Log.error("Could not make ${shape.name} in MiniHUD", e)
            "MiniHUD would not take that shape; the log says why."
        }
    }

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
     * file written, by a new file replacing the old so a failure never leaves half a file.
     */
    private fun editFile(file: Path, change: (JsonArray) -> Boolean): Boolean = try {
        val root = if (Files.exists(file)) Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject else JsonObject()
        val holder = root.getAsJsonObject("shapes") ?: JsonObject().also { root.add("shapes", it) }
        val shapes = holder.getAsJsonArray("shapes") ?: JsonArray().also { holder.add("shapes", it) }
        if (change(shapes)) {
            Files.createDirectories(file.parent)
            val temp = file.resolveSibling(file.fileName.toString() + ".skysmapshapes.tmp")
            Files.writeString(temp, GSON.toJson(root))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            fileCache.remove(file)
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
            tick(Minecraft.getInstance(), force = true)
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
