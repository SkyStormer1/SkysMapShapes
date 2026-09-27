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
