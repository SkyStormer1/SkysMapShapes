package com.skystormer.skysmapshapes

/** A shape read from MiniHUD: shown, hidden or jumped to here, but edited only in MiniHUD. */
class MiniHudShape(
    override val id: String,
    override val label: String,
    override val dimension: String,
    override val colour: Int,
    override val geometry: Geometry,
    /** MiniHUD's own name for the kind of shape, such as `despawn_sphere`. */
    val typeId: String,
    val enabledInMiniHud: Boolean,
    /** The height it sits at in the world, for sharing it or making it again elsewhere. */
    val centreY: Int? = null,
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

    val typeTitle: String get() = typeId.split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /**
     * The same outline as one of this mod's own shapes, for sharing it or keeping a copy. Null for
     * an outline this mod has no shape for, such as a line.
     */
    fun asOwnShape(): Shape? {
        val b = bounds
        val x = Math.floor(b.centreX).toInt()
        val z = Math.floor(b.centreZ).toInt()
        val common = { type: Shape.Type, radius: Double, width: Double, length: Double ->
            Shape(
                label = name, dimension = dimension, type = type, x = x, z = z,
                radius = radius, width = width, length = length, colour = colour, y = centreY,
            )
        }
        return when (val g = geometry) {
            is Geometry.Circle -> common(Shape.Type.CIRCLE, g.radius, 0.0, 0.0)
            is Geometry.Ellipse -> common(Shape.Type.ELLIPSE, 0.0, g.rx * 2, g.rz * 2)
            is Geometry.Polygon -> {
                if (!g.closed) return null
                val points = g.points(1f)
                val width = b.maxX - b.minX
                val length = b.maxZ - b.minZ
                when {
                    points.size / 2 == 8 -> common(Shape.Type.OCTAGON, width / 2, 0.0, 0.0)
                    // A diamond's corners sit in the middle of each side of its box.
                    points.size / 2 == 4 && isDiamond(points, b) -> common(Shape.Type.RHOMBUS, width / 2, 0.0, 0.0)
                    else -> common(Shape.Type.RECTANGLE, 0.0, width, length)
                }
            }
        }
    }

    private fun isDiamond(points: DoubleArray, b: Geometry.Bounds): Boolean =
        (0 until 4).all { i ->
            val x = points[i * 2]
            val z = points[i * 2 + 1]
            (Math.abs(x - b.centreX) < 1e-6) != (Math.abs(z - b.centreZ) < 1e-6)
        }

    override fun describeSize(): String {
        val b = bounds
        return when (val g = geometry) {
            is Geometry.Circle -> "MiniHUD $typeTitle, radius ${Shape.number(g.radius)}"
            else -> if (!g.closed) "MiniHUD $typeTitle"
            else "MiniHUD $typeTitle, ${Shape.number(b.maxX - b.minX)} × ${Shape.number(b.maxZ - b.minZ)}"
        }
    }

    override fun describeSizeShort(): String {
        val g = geometry
        if (g is Geometry.Circle) return "circle r${Shape.number(g.radius)}"
        val b = bounds
        return "${Shape.number(b.maxX - b.minX)} × ${Shape.number(b.maxZ - b.minZ)}"
    }

    override fun describePosition(): String {
        val b = bounds
        return "Centred on ${Math.floor(b.centreX).toInt()}, ${Math.floor(b.centreZ).toInt()}" +
            if (enabledInMiniHud) "" else " (off in MiniHUD)"
    }
}
