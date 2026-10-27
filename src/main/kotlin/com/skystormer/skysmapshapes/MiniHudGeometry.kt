package com.skystormer.skysmapshapes

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
/** MiniHUD's saved shape JSON to a footprint outline. */
internal object MiniHudGeometry {

    private val SPHERES = setOf(
        "sphere_blocky", "adjustable_spawn_sphere", "can_spawn_sphere", "can_despawn_sphere",
        "despawn_sphere", "clipped_spawn_sphere_y",
    )

    /** The cross-section of MiniHUD's prisms, cones and pyramids by type; null for any other type. */
    private fun sectionOf(type: String): Volume.Section? = when (type) {
        "circle", "cone" -> Volume.Section.CIRCLE
        "square", "pyramid" -> Volume.Section.SQUARE
        "rhombus", "diamond_pyramid" -> Volume.Section.RHOMBUS
        "octagon_pyramid" -> Volume.Section.OCTAGON
        else -> null
    }

    /** [section] around ([cx], [cz]), seen from above. */
    private fun outline(section: Volume.Section, cx: Double, cz: Double, radius: Double): Geometry = when (section) {
        Volume.Section.CIRCLE -> Geometry.Circle(cx, cz, radius)
        Volume.Section.SQUARE -> Geometry.rectangle(cx - radius, cz - radius, cx + radius, cz + radius)
        Volume.Section.RHOMBUS -> Geometry.rhombus(cx, cz, radius)
        Volume.Section.OCTAGON -> Geometry.octagon(cx, cz, radius)
    }

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
        val axis = Volume.Direction.of(json.get("main_axis")?.asString)
        val height = json.get("height")?.asDouble ?: 1.0
        if (axis.upright) return outline(sectionOf(type) ?: Volume.Section.CIRCLE, cx, cz, radius)
        return along(axis, cx, cz, radius, radius, height)
    }

    /**
     * The space a shape takes up in the world, from the same JSON; null for one with no inside,
     * such as a line. Sizes are read as MiniHUD reads them: a spawn sphere's margin adds to its
     * radius, prisms and pyramids stand [height] blocks from their centre block along their axis,
     * and a centre snapped to a block is moved to that block's middle (or corner).
     */
    fun volumeOf(type: String, json: JsonObject): Volume? {
        vec(json, "corner1")?.let { a ->
            val b = vec(json, "corner2") ?: return null
            return Volume.Box(minOf(a[0], b[0]), minOf(a[1], b[1]), minOf(a[2], b[2]), maxOf(a[0], b[0]), maxOf(a[1], b[1]), maxOf(a[2], b[2]))
        }
        if (json.has("origin_x")) {
            val origin = doubleArrayOf(json.get("origin_x").asDouble, json.get("origin_y")?.asDouble ?: return null, json.get("origin_z")?.asDouble ?: return null)
            return Volume.Tapered(
                Math.floor(origin[0]).toInt(), Math.floor(origin[1]).toInt(), Math.floor(origin[2]).toInt(),
                Volume.Direction.of(json.get("direction")?.asString), sectionOf(type) ?: Volume.Section.CIRCLE,
                json.get("bottom_radius")?.asDouble ?: 0.0, json.get("top_radius")?.asDouble ?: 0.0, json.get("height")?.asInt ?: 1,
            )
        }
        val centre = vec(json, "center")?.let { snapped(it, json.get("snap")?.asString) } ?: return null
        val radius = json.get("radius")?.asDouble ?: return null
        if (type == "ellipsoid_spawn") {
            return Volume.Ellipsoid(centre[0], centre[1], centre[2], radius, json.get("radius_y")?.asDouble ?: radius, json.get("radius_z")?.asDouble ?: radius)
        }
        if (type == "sphere_blocky") return Volume.Ellipsoid(centre[0], centre[1], centre[2], radius)
        if (type in SPHERES) {
            val margin = json.get("margin")?.asDouble ?: 0.0
            if (type != "clipped_spawn_sphere_y") return Volume.SpawnSphere(centre[0], centre[1], centre[2], radius, margin)
            val bottom = centre[1] - (radius - (json.get("bottom_trim")?.asDouble ?: 0.0))
            val top = centre[1] + (radius - (json.get("top_trim")?.asDouble ?: 0.0))
            return Volume.SpawnSphere(centre[0], centre[1], centre[2], radius, margin, bottom, top)
        }
        // Only the prisms are left with a centre and a radius; cones and pyramids were taken above.
        val section = sectionOf(type)?.takeIf { type in PRISMS } ?: return null
        return standing(Volume.Direction.of(json.get("main_axis")?.asString), centre, section, radius, json.get("height")?.asInt ?: 1)
    }

    private val PRISMS = setOf("circle", "square", "rhombus")

    /** A cross-section stood [height] blocks along [direction] from the block holding [centre]. */
    private fun standing(direction: Volume.Direction, centre: DoubleArray, section: Volume.Section, radius: Double, height: Int): Volume {
        val (along, u, v) = direction.axis.split(centre[0], centre[1], centre[2])
        return Volume.Extruded(direction.axis, Math.floor(along).toInt(), direction.step, maxOf(1, height), u, v, section, radius)
    }

    /**
     * [centre] moved as MiniHUD's block snap moves it (`getBlockSnappedPosition`): to the middle of
     * its block across but its bottom up and down, or to its corner. Moved to the middle up and
     * down as well, a sphere's light levels sat half a block above MiniHUD's own sphere.
     */
    private fun snapped(centre: DoubleArray, snap: String?): DoubleArray = when (snap) {
        "center" -> doubleArrayOf(Math.floor(centre[0]) + 0.5, Math.floor(centre[1]), Math.floor(centre[2]) + 0.5)
        "corner" -> DoubleArray(3) { Math.floor(centre[it]) }
        else -> centre
    }

    /** Cones and pyramids: an upright one is its wider end's outline; one lying down, a trapezoid. */
    private fun tapered(type: String, json: JsonObject): Geometry? {
        val cx = json.get("origin_x")?.asDouble ?: return null
        val cz = json.get("origin_z")?.asDouble ?: return null
        val bottom = json.get("bottom_radius")?.asDouble ?: 0.0
        val top = json.get("top_radius")?.asDouble ?: 0.0
        val height = json.get("height")?.asDouble ?: 1.0
        val direction = Volume.Direction.of(json.get("direction")?.asString)
        if (direction.upright) return outline(sectionOf(type) ?: Volume.Section.CIRCLE, cx, cz, maxOf(bottom, top))
        return along(direction, cx, cz, bottom, top, height)
    }

    /** A shape lying along [direction] from ([cx], [cz]): [startRadius] wide there, [endRadius] at [length]. */
    private fun along(direction: Volume.Direction, cx: Double, cz: Double, startRadius: Double, endRadius: Double, length: Double): Geometry {
        val dx = if (direction.axis == Volume.Axis.X) direction.step.toDouble() else 0.0
        val dz = if (direction.axis == Volume.Axis.Z) direction.step.toDouble() else 0.0
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

    /**
     * How a flat map shape stands up in the world once it is in MiniHUD. From above they all look
     * the same as the shape on the map; only the world shows the difference.
     */
    enum class Form { UPRIGHT, SPHERE, POINTED }

    /** The forms MiniHUD has for [type], the first being the one picked unless asked otherwise. */
    fun forms(type: Shape.Type): List<Form> = when (type) {
        Shape.Type.CIRCLE -> listOf(Form.UPRIGHT, Form.SPHERE, Form.POINTED)
        Shape.Type.SQUARE, Shape.Type.RHOMBUS, Shape.Type.OCTAGON -> listOf(Form.UPRIGHT, Form.POINTED)
        Shape.Type.RECTANGLE -> listOf(Form.UPRIGHT)
        Shape.Type.ELLIPSE -> listOf(Form.SPHERE)
    }

    /** What [form] of a [type] is called, as MiniHUD's own shape list would put it. */
    fun formName(type: Shape.Type, form: Form): String = when (form) {
        Form.UPRIGHT -> when (type) {
            Shape.Type.CIRCLE -> "Cylinder"
            Shape.Type.RECTANGLE -> "Box"
            else -> "Prism"
        }
        Form.SPHERE -> if (type == Shape.Type.ELLIPSE) "Ellipsoid" else "Sphere"
        Form.POINTED -> if (type == Shape.Type.CIRCLE) "Cone" else "Pyramid"
    }

    /** Where [form] puts a shape made at your height, for a tooltip. */
    fun formTip(form: Form): String = when (form) {
        Form.UPRIGHT -> "${MiniHudShapes.PRISM_HEIGHT} blocks tall, from ${MiniHudShapes.PRISM_HEIGHT / 2} below your feet to ${MiniHudShapes.PRISM_HEIGHT / 2} above."
        Form.SPHERE -> "Centred on your feet."
        Form.POINTED -> "Its base at your feet, coming to a point as high above as the shape is wide from the middle."
    }

    /*
     * MiniHUD's size limits, as of 0.40.7. A size over its limit is not refused but ignored, so the
     * shape keeps MiniHUD's small default. Spheres, circles, squares and rhombuses take a higher
     * limit in "max_radius" (below 1,000,000), cones and pyramids in "max_radius" and "max_height";
     * an ellipsoid's north-south and up-down radii are held to 1024 whatever is asked.
     */
    private const val DEFAULT_MAX_RADIUS = 1024
    private const val DEFAULT_MAX_HEIGHT = 384
    private const val MAX_RADIUS_LIMIT = 1_000_000
    private const val ELLIPSOID_MAX_RADIUS = 1024.0

    /**
     * The most blocks' worth of shape this mod puts into MiniHUD. MiniHUD draws every block face of
     * a shape into one buffer that holds about four million faces, and goes over it with no check:
     * a sphere of radius 3,791 (some 180 million blocks) crashed the game. Kept well under.
     */
    private const val MAX_BLOCKS = 1_500_000.0

    /** About how many blocks' worth of MiniHUD shape slows the game; a guess, not measured. */
    private const val LAG_BLOCKS = 500_000.0

    /** The form a MiniHUD kind of shape stands up in, from its name, such as `sphere_blocky`. */
    fun formOfType(typeId: String): Form = when {
        "sphere" in typeId || "ellipsoid" in typeId -> Form.SPHERE
        "cone" in typeId || "pyramid" in typeId -> Form.POINTED
        else -> Form.UPRIGHT
    }

    /**
     * Why MiniHUD cannot hold [shape] as [form] at its size, for the player; null when it can.
     * Without a form, the first MiniHUD has for that kind, as when making one.
     */
    fun whyTooBig(shape: Shape, form: Form? = null): String? {
        val blocks = blocks(shape, form)
        return when {
            blocks != null && blocks > MAX_BLOCKS ->
                "Too big for MiniHUD: it would draw about ${millions(blocks)} million blocks of it, and much over " +
                    "${millions(MAX_BLOCKS)} million can crash the game. ${largest(shape, form)} It stays on this mod's map."
            shape.type == Shape.Type.ELLIPSE && shape.length / 2 > ELLIPSOID_MAX_RADIUS ->
                "MiniHUD's ellipsoids are at most ${Shape.number(ELLIPSOID_MAX_RADIUS * 2)} blocks north to south, " +
                    "and this one is ${Shape.number(shape.length)}. It stays on this mod's map."
            shape.type != Shape.Type.RECTANGLE && Math.ceil(radiusOf(shape)) + 1 >= MAX_RADIUS_LIMIT ->
                "MiniHUD's shapes are at most ${Shape.number(MAX_RADIUS_LIMIT - 2.0)} blocks from the middle to the edge. It stays on this mod's map."
            else -> null
        }
    }

    /**
     * A warning when [shape] as [form] is big enough that MiniHUD drawing it block by block may
     * slow the game; null when it is not. A box is drawn as six flat sides, so it never is.
     */
    fun lagWarning(shape: Shape, form: Form? = null): String? {
        val blocks = blocks(shape, form) ?: return null
        if (blocks < LAG_BLOCKS) return null
        return "It is big: MiniHUD draws it block by block, about ${millions(blocks)} million blocks of it, " +
            "which may slow the game while it is near."
    }

    private fun millions(blocks: Double) = Shape.number(Math.round(blocks / 100_000) / 10.0)

    /** A count of blocks for the player, roughly: "4,800" or "12.6 million". */
    fun aboutBlocks(blocks: Double): String =
        if (blocks >= 1_000_000) "${millions(blocks)} million"
        else String.format(java.util.Locale.ROOT, "%,d", maxOf(1L, Math.round(blocks / 100) * 100))

    /** The biggest [shape] could be as [form] and still go into MiniHUD, said for the player. */
    private fun largest(shape: Shape, form: Form?): String {
        fun scaled(k: Double) = shape.copy(radius = shape.radius * k, width = shape.width * k, length = shape.length * k)
        var low = 0.0
        var high = 1.0
        repeat(40) {
            val k = (low + high) / 2
            if ((blocks(scaled(k), form) ?: 0.0) > MAX_BLOCKS) high = k else low = k
        }
        val most = scaled(low)
        return if (shape.type.sized == Shape.Sized.RADIUS) "As this, it can be at most about ${Math.floor(most.radius).toInt()} blocks from the middle to the edge."
        else "As this, it can be at most about ${Math.floor(most.width).toInt()} × ${Math.floor(most.length).toInt()} blocks."
    }

    /**
     * Why MiniHUD cannot hold the finished shape [json] at its size, for the player; null when it
     * can. Checked on the JSON itself, not the outline, because a shared MiniHUD shape carries
     * sizes of its own that the outline never shows: a line showing a circle of radius 5 could ask
     * MiniHUD for a sphere of radius 4,000, which would crash the game of whoever added it.
     */
    fun whyTooBig(json: JsonObject): String? {
        val type = json.get("type")?.asString
        val blocks = blocksOf(json)
        return when {
            json.entrySet().any { (_, value) -> !finite(value) } ->
                "MiniHUD cannot take those sizes. It stays on this mod's map."
            blocks != null && blocks > MAX_BLOCKS ->
                "Too big for MiniHUD: it would draw about ${millions(blocks)} million blocks of it, and much over " +
                    "${millions(MAX_BLOCKS)} million can crash the game. It stays on this mod's map."
            type == "ellipsoid_spawn" && (number(json, "radius_z") ?: 0.0) > ELLIPSOID_MAX_RADIUS ->
                "MiniHUD's ellipsoids are at most ${Shape.number(ELLIPSOID_MAX_RADIUS * 2)} blocks north to south, " +
                    "and this one is ${Shape.number(number(json, "radius_z")!! * 2)}. It stays on this mod's map."
            RADIUS_KEYS.any { key -> number(json, key)?.let { Math.ceil(it) + 1 >= MAX_RADIUS_LIMIT } == true } ||
                LIMIT_KEYS.any { key -> number(json, key)?.let { it >= MAX_RADIUS_LIMIT } == true } ->
                "MiniHUD's shapes are at most ${Shape.number(MAX_RADIUS_LIMIT - 2.0)} blocks from the middle to the edge. It stays on this mod's map."
            else -> null
        }
    }

    /** As [lagWarning] for a shape, but for the finished MiniHUD [json], shared sizes and all. */
    fun lagWarning(json: JsonObject): String? {
        val blocks = blocksOf(json) ?: return null
        if (blocks < LAG_BLOCKS) return null
        return "It is big: MiniHUD draws it block by block, about ${millions(blocks)} million blocks of it, " +
            "which may slow the game while it is near."
    }

    /**
     * About how many blocks MiniHUD draws for the finished shape [json], from its own sizes, by the
     * same reckoning as [blocks] gives for an outline; null for a box (six flat sides) or a kind
     * this cannot size.
     */
    fun blocksOf(json: JsonObject): Double? {
        val type = json.get("type")?.asString ?: return null
        if (json.has("corner1")) return null
        if (json.has("origin_x")) {
            // A cone's (or pyramid's) sloping side: the side of a cut cone from its base to its top.
            val bottom = number(json, "bottom_radius") ?: 0.0
            val top = number(json, "top_radius") ?: 0.0
            val height = number(json, "height") ?: 1.0
            return Math.PI * (bottom + top) * Math.sqrt((bottom - top) * (bottom - top) + height * height)
        }
        val r = number(json, "radius") ?: return null
        return when (type) {
            "ellipsoid_spawn" -> {
                val b = minOf(number(json, "radius_y") ?: r, ELLIPSOID_MAX_RADIUS)
                val c = minOf(number(json, "radius_z") ?: r, ELLIPSOID_MAX_RADIUS)
                4 * Math.PI * (r * b + r * c + b * c) / 3
            }
            in SPHERES -> {
                val outer = r + (number(json, "margin") ?: 0.0)
                4 * Math.PI * outer * outer
            }
            // Prisms: their wall, as tall as they are high.
            "square" -> (number(json, "height") ?: 1.0) * 8 * r
            "rhombus" -> (number(json, "height") ?: 1.0) * 4 * Math.sqrt(2.0) * r
            "circle" -> (number(json, "height") ?: 1.0) * 2 * Math.PI * r
            else -> null
        }
    }

    /** Sizes the finished JSON can ask MiniHUD for, measured from the middle; and the limits raised for them. */
    private val RADIUS_KEYS = listOf("radius", "radius_y", "radius_z", "bottom_radius", "top_radius", "height")
    private val LIMIT_KEYS = listOf("max_radius", "max_height")

    private fun number(json: JsonObject, key: String): Double? =
        json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    /** Whether every number in [value] is a real one, not NaN or infinity. */
    private fun finite(value: JsonElement): Boolean = when {
        value.isJsonArray -> value.asJsonArray.all(::finite)
        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asDouble.isFinite()
        else -> true
    }

    /** About how many blocks MiniHUD draws for [shape] as [form]; null for a box, drawn as six flat sides. */
    internal fun blocks(shape: Shape, form: Form?): Double? {
        val chosen = form?.takeIf { it in forms(shape.type) } ?: forms(shape.type).first()
        if (shape.type == Shape.Type.RECTANGLE) return null
        val r = shape.radius
        return when {
            shape.type == Shape.Type.ELLIPSE -> {
                val a = shape.width / 2; val c = shape.length / 2; val b = minOf(maxOf(a, c), ELLIPSOID_MAX_RADIUS)
                4 * Math.PI * (a * b + a * c + b * c) / 3
            }
            chosen == Form.SPHERE -> 4 * Math.PI * r * r
            // A cone's sloping side, as tall as it is wide from the middle.
            chosen == Form.POINTED -> Math.PI * r * r * Math.sqrt(2.0)
            else -> MiniHudShapes.PRISM_HEIGHT * when (shape.type) {
                Shape.Type.SQUARE -> 8 * r
                Shape.Type.RHOMBUS -> 4 * Math.sqrt(2.0) * r
                else -> 2 * Math.PI * r
            }
        }
    }

    /** The radius MiniHUD will be asked for. */
    private fun radiusOf(shape: Shape): Double =
        if (shape.type == Shape.Type.ELLIPSE) shape.width / 2 else shape.radius

    /**
     * Whether MiniHUD took [sent] at its size: a size over one of its limits is quietly dropped,
     * which [made] (the shape as MiniHUD now has it) shows as a different number.
     */
    fun tookSize(sent: JsonObject, made: JsonObject): Boolean =
        listOf("radius", "radius_y", "radius_z", "bottom_radius", "top_radius", "height").all { key ->
            val want = sent.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return@all true
            val got = made.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return@all false
            // Cones and pyramids hold whole blocks.
            Math.abs(want - got) <= 1.0
        }

    /**
     * One of this mod's shapes as MiniHUD's saved JSON, at height [y], standing up as [form]
     * (the first of [forms] when it is not one of them).
     */
    fun toMiniHudJson(shape: Shape, y: Int, form: Form? = null): JsonObject? {
        val b = shape.bounds
        val json = JsonObject()
        json.addProperty("display_name", shape.name)
        json.addProperty("enabled", true)
        // MiniHUD shades a shape's sides with this colour; its own new shapes are part transparent.
        json.addProperty("color", (shape.colour and 0xFFFFFF) or (0x60 shl 24))
        json.addProperty("color_lines", shape.colour)
        json.addProperty("render_type", "outer_edge")
        val centre = JsonArray().apply { add(b.centreX); add(y.toDouble()); add(b.centreZ) }
        // MiniHUD stands a prism up from its centre block, so it starts half its height lower.
        val prismBase = JsonArray().apply { add(b.centreX); add((y - MiniHudShapes.PRISM_HEIGHT / 2).toDouble()); add(b.centreZ) }
        val forms = forms(shape.type)
        val chosen = form?.takeIf { it in forms } ?: forms.first()

        // A size over MiniHUD's default limit is ignored unless the limit is raised with it.
        fun liftLimit(key: String, size: Double, default: Int) {
            if (size > default) json.addProperty(key, Math.ceil(size).toInt() + 1)
        }

        // Cones and pyramids: MiniHUD builds them up from a base, narrowing to [top].
        fun tapered(typeId: String, baseY: Double, top: Double, height: Int) {
            json.addProperty("type", typeId)
            json.addProperty("origin_x", b.centreX)
            json.addProperty("origin_y", baseY)
            json.addProperty("origin_z", b.centreZ)
            json.addProperty("bottom_radius", shape.radius)
            json.addProperty("top_radius", top)
            // MiniHUD reads a direction by its lower-case name; "UP" comes back as none and breaks the shape.
            json.addProperty("direction", "up")
            json.addProperty("height", height)
            liftLimit("max_radius", maxOf(shape.radius, top), DEFAULT_MAX_RADIUS)
            liftLimit("max_height", height.toDouble(), DEFAULT_MAX_HEIGHT)
        }
        if (chosen == Form.POINTED) {
            val typeId = when (shape.type) {
                Shape.Type.CIRCLE -> "cone"
                Shape.Type.SQUARE -> "pyramid"
                Shape.Type.RHOMBUS -> "diamond_pyramid"
                else -> "octagon_pyramid"
            }
            // As tall as it is wide from the middle: sides at 45 degrees.
            tapered(typeId, y.toDouble(), 0.0, maxOf(1, Math.round(shape.radius).toInt()))
            return json
        }
        when (shape.type) {
            Shape.Type.CIRCLE -> {
                json.addProperty("radius", shape.radius)
                liftLimit("max_radius", shape.radius, DEFAULT_MAX_RADIUS)
                json.addProperty("main_axis", "UP")
                if (chosen == Form.SPHERE) {
                    json.addProperty("type", "sphere_blocky")
                    json.add("center", centre)
                } else {
                    json.addProperty("type", "circle")
                    json.add("center", prismBase)
                    json.addProperty("height", MiniHudShapes.PRISM_HEIGHT)
                }
            }
            Shape.Type.ELLIPSE -> {
                json.addProperty("type", "ellipsoid_spawn")
                json.add("center", centre)
                json.addProperty("radius", shape.width / 2)
                liftLimit("max_radius", shape.width / 2, DEFAULT_MAX_RADIUS)
                // How tall it is is this mod's own pick, kept within what MiniHUD allows.
                json.addProperty("radius_y", minOf(maxOf(shape.width, shape.length) / 2, ELLIPSOID_MAX_RADIUS))
                json.addProperty("radius_z", shape.length / 2)
                json.addProperty("main_axis", "UP")
            }
            Shape.Type.SQUARE, Shape.Type.RHOMBUS -> {
                json.addProperty("type", if (shape.type == Shape.Type.SQUARE) "square" else "rhombus")
                json.add("center", prismBase)
                json.addProperty("radius", shape.radius)
                liftLimit("max_radius", shape.radius, DEFAULT_MAX_RADIUS)
                json.addProperty("main_axis", "UP")
                json.addProperty("height", MiniHudShapes.PRISM_HEIGHT)
            }
            // MiniHUD has no octagon prism, but a pyramid with the same radius top and bottom is one.
            Shape.Type.OCTAGON ->
                tapered("octagon_pyramid", (y - MiniHudShapes.PRISM_HEIGHT / 2).toDouble(), shape.radius, MiniHudShapes.PRISM_HEIGHT)
            Shape.Type.RECTANGLE -> {
                json.addProperty("type", "box")
                json.add("corner1", JsonArray().apply { add(b.minX); add((y - MiniHudShapes.PRISM_HEIGHT / 2).toDouble()); add(b.minZ) })
                json.add("corner2", JsonArray().apply { add(b.maxX); add((y + MiniHudShapes.PRISM_HEIGHT / 2).toDouble()); add(b.maxZ) })
            }
        }
        return json
    }

    /** Which fields each kind of MiniHUD shape is built from. */
    private val NEEDS: Map<String, List<String>> = buildMap {
        val sphere = listOf("center", "radius")
        for (type in SPHERES) put(type, sphere)
        put("ellipsoid_spawn", listOf("center", "radius", "radius_z"))
        for (type in listOf("circle", "square", "rhombus")) put(type, listOf("center", "radius", "main_axis", "height"))
        put("box", listOf("corner1", "corner2"))
        val tapered = listOf("origin_x", "origin_y", "origin_z", "bottom_radius", "top_radius", "direction", "height")
        for (type in listOf("cone", "pyramid", "diamond_pyramid", "octagon_pyramid")) put(type, tapered)
    }

    /**
     * Makes [json] MiniHUD's [typeId] instead, when that kind is built from the fields it already
     * has: a sphere can become another kind of sphere, but not a box. Says whether it did.
     */
    fun retype(json: JsonObject, typeId: String): Boolean {
        if (json.get("type")?.asString == typeId) return true
        val needs = NEEDS[typeId] ?: return false
        if (!needs.all { json.has(it) }) return false
        json.remove("type")
        json.addProperty("type", typeId)
        return true
    }

    /** The height at the middle of a shape, from whichever fields its kind uses. */
    fun heightOf(json: JsonObject): Int? {
        vec(json, "center")?.let { centre ->
            val base = Math.floor(centre[1]).toInt()
            val height = json.get("height")?.asInt ?: return base
            return when (json.get("main_axis")?.asString?.let(Volume.Direction::of)) {
                Volume.Direction.UP -> base + height / 2
                Volume.Direction.DOWN -> base - height / 2
                else -> base
            }
        }
        val corner1 = vec(json, "corner1")
        val corner2 = vec(json, "corner2")
        if (corner1 != null && corner2 != null) return Math.floor((corner1[1] + corner2[1]) / 2).toInt()
        json.get("origin_y")?.let { origin ->
            val height = json.get("height")?.asDouble ?: 0.0
            return Math.floor(origin.asDouble + height / 2).toInt()
        }
        vec(json, "start")?.let { return Math.floor(it[1]).toInt() }
        return null
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

    /**
     * Everything that makes a MiniHUD shape the size and shape it is, which a flat outline cannot
     * hold: its height and which way it stands, a box's top and bottom (a centred one's width and
     * depth), a cone's base and top, a spawn sphere's margin, how its centre snaps, and the limits
     * raised for a big one.
     */
    private val SHARED_KEYS = SIZE_KEYS - listOf("start", "end") + listOf("width", "depth", "margin", "snap", "max_radius", "max_height")

    /** [json]'s sizes ([SHARED_KEYS]), to share with a shape so the other end makes the same one. */
    fun sizes(json: JsonObject): JsonObject = JsonObject().also { out ->
        for (key in SHARED_KEYS) json.get(key)?.let { out.add(key, it.deepCopy()) }
    }

    /** [sizes] as words for a line of chat: `height=32 main_axis=UP center=10.5,70,-19.5`. */
    fun sizesText(sizes: JsonObject): String =
        sizes.entrySet().joinToString(" ") { (key, value) -> "$key=${text(value).replace(" ", "")}" }

    /**
     * The kinds of MiniHUD shape a shared line may ask for: those this mod knows how to make again.
     * Anything else from chat is not passed on to MiniHUD at all.
     */
    private val SHAREABLE_TYPES: Set<String> = NEEDS.keys + "centered_box"

    /** [type] when it is a kind of MiniHUD shape a shared line may ask for; null otherwise. */
    fun shareableType(type: String?): String? = type?.takeIf { it in SHAREABLE_TYPES }

    private val POSITION_KEYS = setOf("center", "corner1", "corner2", "origin_x", "origin_y", "origin_z")
    private val SIZE_LIMITED_KEYS = setOf("radius", "radius_y", "radius_z", "bottom_radius", "top_radius", "height", "width", "depth", "margin", "max_radius", "max_height")
    private val SNAPS = setOf("none", "center", "corner")

    /**
     * Only the shared sizes that are what MiniHUD expects: known keys, real numbers no bigger than
     * a world (positions) or MiniHUD's own limit (sizes), and directions and snaps by their names.
     * They come from other players' chat and end up in MiniHUD's own reader, so anything else is
     * dropped rather than passed on. Null when nothing is left.
     */
    fun checkedSizes(sizes: JsonObject?): JsonObject? {
        if (sizes == null) return null
        fun numberIn(value: JsonElement, range: ClosedFloatingPointRange<Double>) =
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asDouble.isFinite() && value.asDouble in range
        val world = -Shape.MAX_COORDINATE.toDouble()..Shape.MAX_COORDINATE.toDouble()
        val size = 0.0..MAX_RADIUS_LIMIT.toDouble()
        val out = JsonObject()
        for ((key, value) in sizes.entrySet()) {
            val ok = when (key) {
                "center", "corner1", "corner2" -> value.isJsonArray && value.asJsonArray.size() == 3 && value.asJsonArray.all { numberIn(it, world) }
                in POSITION_KEYS -> numberIn(value, world)
                in SIZE_LIMITED_KEYS -> numberIn(value, size)
                "main_axis", "direction" -> value.isJsonPrimitive && value.asJsonPrimitive.isString && Volume.Direction.isName(value.asString)
                "snap" -> value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString in SNAPS
                else -> false
            }
            if (ok) out.add(key, value.deepCopy())
        }
        return out.takeIf { it.size() > 0 }
    }

    /**
     * Sizes read back from [words] as [sizesText] writes them; words of anything else are left
     * out, and so are values MiniHUD should not be given ([checkedSizes]).
     */
    fun parseSizes(words: List<String>): JsonObject? {
        val out = JsonObject()
        for (word in words) {
            val key = word.substringBefore('=', "")
            val value = word.substringAfter('=', "")
            if (key !in SHARED_KEYS || value.isEmpty()) continue
            val parts = value.split(',')
            when {
                parts.size == 3 && parts.all { it.toDoubleOrNull() != null } ->
                    out.add(key, JsonArray().apply { parts.forEach { add(it.toDouble()) } })
                value.toDoubleOrNull() != null -> out.addProperty(key, value.toDouble())
                value.toBooleanStrictOrNull() != null -> out.addProperty(key, value.toBooleanStrict())
                else -> out.addProperty(key, value)
            }
        }
        return checkedSizes(out)
    }

    /**
     * What MiniHUD is asked to make for [shape] at height [y]: as [typeId] when it was shared as
     * one of MiniHUD's kinds, with its shared [sizes] when there are some, so it comes out the
     * same as the one shared; else stood up as [form].
     */
    fun forMiniHud(shape: Shape, y: Int, typeId: String?, form: Form?, sizes: JsonObject?): JsonObject? {
        val json = toMiniHudJson(shape, y, typeId?.let { formOfType(it) } ?: form) ?: return null
        if (typeId == null) return json
        if (sizes == null) {
            retype(json, typeId)
            return json
        }
        for (key in SHARED_KEYS) json.remove(key)
        for ((key, value) in sizes.entrySet()) json.add(key, value.deepCopy())
        json.remove("type")
        json.addProperty("type", typeId)
        return json
    }

    /** What makes a shape where and how big it is, as text, for an id that survives re-reading. */
    fun identity(json: JsonObject): String =
        SIZE_KEYS.mapNotNull { key -> json.get(key)?.let { value -> "$key=${text(value)}" } }.joinToString(";")

    private fun text(value: JsonElement): String =
        if (value is JsonArray) value.joinToString(",") { it.asString } else value.asString
}
