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

    /** About how many blocks MiniHUD draws for [shape] as [form]; null for a box, drawn as six flat sides. */
    private fun blocks(shape: Shape, form: Form?): Double? {
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
            json.addProperty("direction", "UP")
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
                json.add("center", centre)
                json.addProperty("radius", shape.radius)
                liftLimit("max_radius", shape.radius, DEFAULT_MAX_RADIUS)
                json.addProperty("main_axis", "UP")
                if (chosen == Form.SPHERE) {
                    json.addProperty("type", "sphere_blocky")
                } else {
                    json.addProperty("type", "circle")
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
                json.add("center", centre)
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
        vec(json, "center")?.let { return Math.floor(it[1]).toInt() }
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

    /** What makes a shape where and how big it is, as text, for an id that survives re-reading. */
    fun identity(json: JsonObject): String =
        SIZE_KEYS.mapNotNull { key -> json.get(key)?.let { value -> "$key=${text(value)}" } }.joinToString(";")

    private fun text(value: JsonElement): String =
        if (value is JsonArray) value.joinToString(",") { it.asString } else value.asString
}
