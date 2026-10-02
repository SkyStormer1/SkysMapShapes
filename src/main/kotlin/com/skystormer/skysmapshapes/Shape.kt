package com.skystormer.skysmapshapes

import java.util.UUID

/**
 * One of this mod's own shapes on the map, in one dimension.
 *
 * Positions are whole blocks. A shape is centred on the middle of its centre block, where a
 * player standing on that block is, which is what a despawn sphere is measured from. A rectangle
 * can instead start at the north-west corner of its block, for lining up with block or chunk
 * edges.
 *
 * @property radius for a circle, the radius; for a square, diamond or octagon, the distance from
 *   the centre to each side (to each corner, for a diamond). In blocks.
 * @property width a rectangle's or ellipse's size east–west (X), in blocks.
 * @property length a rectangle's or ellipse's size north–south (Z), in blocks.
 * @property lineWidth outline thickness in screen pixels, before the global thickness scale.
 * @property y not used yet: kept so that shapes saved now can later be given a height, for a
 *   version that also shows them in the world.
 */
data class Shape(
    override val id: String = UUID.randomUUID().toString(),
    override val label: String,
    override val dimension: String,
    val type: Type,
    val x: Int,
    val z: Int,
    val radius: Double = 0.0,
    val width: Double = 0.0,
    val length: Double = 0.0,
    val anchor: Anchor = Anchor.CENTRE,
    override val colour: Int,
    override val fill: Boolean = false,
    override val visible: Boolean = true,
    val y: Int? = null,
    val lineWidth: Float = Config.DEFAULT_LINE_WIDTH,
) : MapShape {

    enum class Type(val title: String, val sized: Sized) {
        CIRCLE("Circle", Sized.RADIUS),
        SQUARE("Square", Sized.RADIUS),
        RECTANGLE("Rectangle", Sized.WIDTH_LENGTH),
        RHOMBUS("Diamond", Sized.RADIUS),
        OCTAGON("Octagon", Sized.RADIUS),
        ELLIPSE("Ellipse", Sized.WIDTH_LENGTH),
    }

    /** Which size fields a kind of shape uses. */
    enum class Sized { RADIUS, WIDTH_LENGTH }

    enum class Anchor(val title: String) { CENTRE("Centre"), CORNER("North-west corner") }

    override val fromMiniHud: Boolean get() = false

    override val name: String get() = label.ifBlank { type.title }

    /** How thick this shape's outline is drawn: its own thickness times the global scale. */
    override val effectiveLineWidth: Float get() = lineWidth * Config.thicknessScale

    private val cx get() = x + 0.5
    private val cz get() = z + 0.5

    override val geometry: Geometry
        get() = when (type) {
            Type.CIRCLE -> Geometry.Circle(cx, cz, radius)
            Type.SQUARE -> Geometry.rectangle(cx - radius, cz - radius, cx + radius, cz + radius)
            Type.RHOMBUS -> Geometry.rhombus(cx, cz, radius)
            Type.OCTAGON -> Geometry.octagon(cx, cz, radius)
            Type.ELLIPSE -> Geometry.Ellipse(cx, cz, width / 2, length / 2)
            Type.RECTANGLE -> when (anchor) {
                Anchor.CENTRE -> Geometry.rectangle(cx - width / 2, cz - length / 2, cx + width / 2, cz + length / 2)
                Anchor.CORNER -> Geometry.rectangle(x.toDouble(), z.toDouble(), x + width, z + length)
            }
        }

    /** Whether the middle of block ([blockX], [blockZ]) is inside the shape or on its edge. */
    fun contains(blockX: Int, blockZ: Int): Boolean = geometry.contains(blockX + 0.5, blockZ + 0.5)

    /** How far the point ([px], [pz]) is from the outline, in blocks, inside or out. */
    fun distanceToOutline(px: Double, pz: Double): Double = geometry.distanceTo(px, pz)

    /** "Circle, radius 128", "Rectangle, 48 × 32": for tooltips and the shapes list. */
    override fun describeSize(): String = when (type) {
        Type.CIRCLE -> "Circle, radius ${number(radius)}"
        Type.SQUARE -> "Square, ${number(radius)} from centre to edge"
        Type.RHOMBUS -> "Diamond, ${number(radius)} from centre to each point"
        Type.OCTAGON -> "Octagon, ${number(radius)} from centre to edge"
        Type.ELLIPSE -> "Ellipse, ${number(width)} × ${number(length)}"
        Type.RECTANGLE -> "Rectangle, ${number(width)} × ${number(length)}" +
            if (anchor == Anchor.CORNER) " from its north-west corner" else ""
    }

    /** "circle r128", "48 × 32": short enough for a row in the shapes list. */
    override fun describeSizeShort(): String = when (type) {
        Type.CIRCLE -> "circle r${number(radius)}"
        Type.SQUARE -> "square ${number(radius * 2)}"
        Type.RHOMBUS -> "diamond r${number(radius)}"
        Type.OCTAGON -> "octagon r${number(radius)}"
        Type.RECTANGLE -> "${number(width)} × ${number(length)}"
        Type.ELLIPSE -> "ellipse ${number(width)} × ${number(length)}"
    }

    /** "Centred on 100, -40", or where a corner-anchored rectangle starts. */
    override fun describePosition(): String =
        if (type == Type.RECTANGLE && anchor == Anchor.CORNER) "North-west corner at $x, $z" else "Centred on $x, $z"

    /**
     * Whether every value is one a shape can really have. Shapes arrive from chat and from files
     * as well as from this mod's own screens, so each is checked before it is kept: a size that
     * is not a number, or far bigger than any world, or a dimension that is not an id, is turned
     * away rather than drawn or written anywhere.
     */
    fun isValid(): Boolean {
        fun size(value: Double) = value.isFinite() && value > 0 && value <= MAX_SIZE
        val sized = when (type.sized) {
            Sized.RADIUS -> size(radius)
            Sized.WIDTH_LENGTH -> size(width) && size(length)
        }
        return sized && Math.abs(x) <= MAX_COORDINATE && Math.abs(z) <= MAX_COORDINATE &&
            (y == null || y in -MAX_HEIGHT..MAX_HEIGHT) && label.length <= MAX_LABEL &&
            lineWidth.isFinite() && Dimensions.isId(dimension)
    }

    companion object {
        /** Well past the world border, in blocks; nothing real is bigger or further out. */
        const val MAX_SIZE = 60_000_000.0
        const val MAX_COORDINATE = 30_000_000
        const val MAX_HEIGHT = 2048
        const val MAX_LABEL = 128

        /**
         * A number as plain text: no ".0" on whole ones, a dot for decimals whatever the
         * language, since these are read by people and by other copies of this mod.
         */
        fun number(value: Double): String =
            if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
    }
}
