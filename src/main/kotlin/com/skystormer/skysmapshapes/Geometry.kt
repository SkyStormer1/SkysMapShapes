package com.skystormer.skysmapshapes

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A shape's outline on the map, in block coordinates: what is drawn, hovered and measured. Both
 * this mod's own shapes and MiniHUD's are turned into one of these.
 */
sealed class Geometry {

    /** The extent: west, north, east, south edges. */
    class Bounds(val minX: Double, val minZ: Double, val maxX: Double, val maxZ: Double) {
        val centreX get() = (minX + maxX) / 2
        val centreZ get() = (minZ + maxZ) / 2
    }

    abstract val bounds: Bounds

    /** Square blocks covered; a line covers none. */
    abstract val area: Double

    /** False for a line, which has no inside to fill. */
    open val closed: Boolean get() = true

    /** The outline as points (x, z, x, z, …), enough of them for a curve to look smooth at this zoom. */
    abstract fun points(blocksPerUnit: Float): DoubleArray

    /** How far ([px], [pz]) is from the outline, inside or out. */
    abstract fun distanceTo(px: Double, pz: Double): Double

    abstract fun contains(px: Double, pz: Double): Boolean

    class Circle(val cx: Double, val cz: Double, val radius: Double) : Geometry() {
        override val bounds get() = Bounds(cx - radius, cz - radius, cx + radius, cz + radius)
        override val area get() = PI * radius * radius
        override fun points(blocksPerUnit: Float) = ring(cx, cz, radius, radius, segments(radius, blocksPerUnit))
        override fun distanceTo(px: Double, pz: Double) = abs(hypot(px - cx, pz - cz) - radius)
        override fun contains(px: Double, pz: Double) = hypot(px - cx, pz - cz) <= radius
    }

    /** An ellipse with radii [rx] east–west and [rz] north–south. */
    class Ellipse(val cx: Double, val cz: Double, val rx: Double, val rz: Double) : Geometry() {
        override val bounds get() = Bounds(cx - rx, cz - rz, cx + rx, cz + rz)
        override val area get() = PI * rx * rz
        override fun points(blocksPerUnit: Float) = ring(cx, cz, rx, rz, segments(maxOf(rx, rz), blocksPerUnit))
        override fun distanceTo(px: Double, pz: Double) = polygonDistance(ring(cx, cz, rx, rz, 256), true, px, pz)
        override fun contains(px: Double, pz: Double): Boolean {
            val dx = (px - cx) / rx
            val dz = (pz - cz) / rz
            return dx * dx + dz * dz <= 1
        }
    }

    /** Straight sides through [corners] (x, z, x, z, …); a line when not [closed]. */
    class Polygon(private val corners: DoubleArray, override val closed: Boolean = true) : Geometry() {
        override val bounds: Bounds
            get() {
                var minX = Double.MAX_VALUE; var minZ = Double.MAX_VALUE
                var maxX = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
                for (i in 0 until corners.size / 2) {
                    minX = minOf(minX, corners[i * 2]); maxX = maxOf(maxX, corners[i * 2])
                    minZ = minOf(minZ, corners[i * 2 + 1]); maxZ = maxOf(maxZ, corners[i * 2 + 1])
                }
                return Bounds(minX, minZ, maxX, maxZ)
            }

        override val area: Double
            get() {
                if (!closed) return 0.0
                var sum = 0.0
                val n = corners.size / 2
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    sum += corners[i * 2] * corners[j * 2 + 1] - corners[j * 2] * corners[i * 2 + 1]
                }
                return abs(sum) / 2
            }

        override fun points(blocksPerUnit: Float) = corners
        override fun distanceTo(px: Double, pz: Double) = polygonDistance(corners, closed, px, pz)

        override fun contains(px: Double, pz: Double): Boolean {
            if (!closed) return false
            // Points on an edge count as inside, as for a circle.
            if (polygonDistance(corners, true, px, pz) < 1e-9) return true
            var inside = false
            val n = corners.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = corners[i * 2]; val zi = corners[i * 2 + 1]
                val xj = corners[j * 2]; val zj = corners[j * 2 + 1]
                if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) inside = !inside
                j = i
            }
            return inside
        }
    }

    companion object {
        fun rectangle(minX: Double, minZ: Double, maxX: Double, maxZ: Double) =
            Polygon(doubleArrayOf(minX, minZ, maxX, minZ, maxX, maxZ, minX, maxZ))

        /** A diamond: corners [radius] north, east, south and west of the centre. */
        fun rhombus(cx: Double, cz: Double, radius: Double) =
            Polygon(doubleArrayOf(cx, cz - radius, cx + radius, cz, cx, cz + radius, cx - radius, cz))

        /** A regular octagon with flat sides [radius] north, east, south and west of the centre. */
        fun octagon(cx: Double, cz: Double, radius: Double): Polygon {
            val corner = radius / cos(PI / 8)
            return Polygon(ring(cx, cz, corner, corner, 8, PI / 8))
        }

        /** Enough points that a circle looks round at this zoom, without thousands for a small one. */
        fun segments(radius: Double, blocksPerUnit: Float): Int =
            (2 * PI * radius / blocksPerUnit / 4).toInt().coerceIn(24, 1024)

        fun ring(cx: Double, cz: Double, rx: Double, rz: Double, segments: Int, offset: Double = 0.0): DoubleArray =
            DoubleArray(segments * 2).also { points ->
                for (i in 0 until segments) {
                    val angle = offset + 2 * PI * i / segments
                    points[i * 2] = cx + cos(angle) * rx
                    points[i * 2 + 1] = cz + sin(angle) * rz
                }
            }

        fun polygonDistance(points: DoubleArray, closed: Boolean, px: Double, pz: Double): Double {
            val n = points.size / 2
            if (n == 1) return hypot(px - points[0], pz - points[1])
            var best = Double.MAX_VALUE
            val sides = if (closed) n else n - 1
            for (i in 0 until sides) {
                val j = (i + 1) % n
                best = minOf(best, segmentDistance(points[i * 2], points[i * 2 + 1], points[j * 2], points[j * 2 + 1], px, pz))
            }
            return best
        }

        private fun segmentDistance(x1: Double, z1: Double, x2: Double, z2: Double, px: Double, pz: Double): Double {
            val dx = x2 - x1
            val dz = z2 - z1
            val lengthSquared = dx * dx + dz * dz
            if (lengthSquared == 0.0) return hypot(px - x1, pz - z1)
            val t = (((px - x1) * dx + (pz - z1) * dz) / lengthSquared).coerceIn(0.0, 1.0)
            return sqrt((x1 + t * dx - px).let { it * it } + (z1 + t * dz - pz).let { it * it })
        }
    }
}

/**
 * Anything drawn on the map: this mod's own [Shape]s, which can be edited here, and shapes read
 * from MiniHUD, which are edited in MiniHUD and only shown (or hidden) here.
 */
interface MapShape {
    val id: String
    val label: String
    val dimension: String
    val colour: Int
    val fill: Boolean
    val visible: Boolean
    val effectiveLineWidth: Float
    val geometry: Geometry

    /** True for a shape read from MiniHUD. */
    val fromMiniHud: Boolean

    /** The label, or what kind of shape it is when it has none. */
    val name: String

    /** "Circle, radius 128": for tooltips and details. */
    fun describeSize(): String

    /** "circle r128": short enough for a row in the shapes list. */
    fun describeSizeShort(): String

    fun describePosition(): String

    val bounds: Geometry.Bounds get() = geometry.bounds
    val area: Double get() = geometry.area
}
