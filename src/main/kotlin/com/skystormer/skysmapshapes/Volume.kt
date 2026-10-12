package com.skystormer.skysmapshapes

import kotlin.math.abs
import kotlin.math.max

/**
 * The space a shape takes up in the world, for asking whether a block is in it. MiniHUD's shapes
 * have real heights; this mod's own have none yet, so they stand as [Column]s.
 */
sealed interface Volume {

    /** Whether the middle of block ([x], [y], [z]) is inside, or on the edge. */
    fun contains(x: Int, y: Int, z: Int): Boolean

    /** Everything between two corners, as MiniHUD's boxes are. */
    class Box(
        private val minX: Double, private val minY: Double, private val minZ: Double,
        private val maxX: Double, private val maxY: Double, private val maxZ: Double,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean =
            x + HALF in minX..maxX && y + HALF in minY..maxY && z + HALF in minZ..maxZ
    }

    /** A sphere, or one stretched to radii [rx], [ry] and [rz]. */
    class Ellipsoid(
        private val cx: Double, private val cy: Double, private val cz: Double,
        private val rx: Double, private val ry: Double = rx, private val rz: Double = rx,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean {
            val dx = (x + HALF - cx) / rx
            val dy = (y + HALF - cy) / ry
            val dz = (z + HALF - cz) / rz
            return dx * dx + dy * dy + dz * dz <= 1
        }
    }

    /**
     * One of MiniHUD's spawn spheres, as it decides which blocks are in it (`ShapeSpawnSphere.
     * isPositionOnOrInsideRing`): a block is in when the middle of its top is closer than [radius]
     * to the square [margin] blocks each way around the centre, at the centre's height; a clipped
     * one ([low], [high]) also keeps only blocks whose middle is between those heights. Not a plain
     * sphere [margin] bigger, which put the light levels a block or so off at its edge.
     */
    class SpawnSphere(
        private val cx: Double, private val cy: Double, private val cz: Double,
        private val radius: Double, private val margin: Double,
        private val low: Double = Double.NEGATIVE_INFINITY, private val high: Double = Double.POSITIVE_INFINITY,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean {
            if (y + HALF < low || y + HALF > high) return false
            val bx = x + HALF
            val bz = z + HALF
            val dx = bx - bx.coerceIn(cx - margin, cx + margin)
            val dy = y + 1 - cy
            val dz = bz - bz.coerceIn(cz - margin, cz + margin)
            return dx * dx + dy * dy + dz * dz < radius * radius
        }
    }

    /** An outline seen from above, at every height. */
    class Column(private val footprint: Geometry) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean = footprint.contains(x + HALF, z + HALF)
    }

    /**
     * A cross-section stood along an axis for [length] blocks, from block [start] on that axis
     * going [step] (1 or -1) each layer: a prism when [startRadius] and [endRadius] are equal,
     * a cone or pyramid when they are not. ([cu], [cv]) is its middle on the other two axes.
     */
    /** A prism: [section] the same all along [length] layers from block [start], stepping [step] along [axis]. */
    class Extruded(
        private val axis: Axis,
        private val start: Int,
        private val step: Int,
        private val length: Int,
        private val cu: Double,
        private val cv: Double,
        private val section: Section,
        private val radius: Double,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean {
            val (along, u, v) = when (axis) {
                Axis.X -> Triple(x, y, z)
                Axis.Y -> Triple(y, x, z)
                Axis.Z -> Triple(z, x, y)
            }
            if ((along - start) * step !in 0 until length) return false
            return section.contains(u + HALF - cu, v + HALF - cv, radius)
        }
    }

    /**
     * A cone or pyramid as MiniHUD lays it (`ShapeTaperedBase.generateShapeBlocks`): [height] + 1
     * layers from the [origin] block along [direction], each [section] around that block's line in
     * whole blocks, its radius going from [bottom] to [top] in steps of (bottom - top) / height.
     */
    class Tapered(
        private val ox: Int, private val oy: Int, private val oz: Int,
        private val direction: String,
        private val section: Section,
        private val bottom: Double,
        private val top: Double,
        private val height: Int,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean {
            val dx = x - ox
            val dy = y - oy
            val dz = z - oz
            // The layer, and the two offsets across it, as MiniHUD's `remapToBlockPos` lays them.
            val (layer, u, v) = when (direction) {
                "DOWN" -> Triple(-dy, dx, dz)
                "NORTH" -> Triple(-dz, dx, dy)
                "SOUTH" -> Triple(dz, dx, dy)
                "WEST" -> Triple(-dx, dy, dz)
                "EAST" -> Triple(dx, dy, dz)
                else -> Triple(dy, dx, dz)
            }
            val layers = maxOf(1, height)
            if (layer !in 0..layers) return false
            val radius = bottom - (bottom - top) * layer / layers
            return section.containsBlocks(u, v, radius)
        }
    }

    enum class Axis { X, Y, Z }

    /** The shape across an [Extruded] volume, [du] and [dv] from its middle. */
    enum class Section {
        CIRCLE {
            override fun contains(du: Double, dv: Double, radius: Double) = du * du + dv * dv <= radius * radius
        },
        SQUARE {
            override fun contains(du: Double, dv: Double, radius: Double) = max(abs(du), abs(dv)) <= radius
        },
        RHOMBUS {
            // MiniHUD's own allowance, so a block exactly on the edge is not lost to rounding.
            override fun contains(du: Double, dv: Double, radius: Double) = abs(du) + abs(dv) <= radius + 1e-6
        },

        /** A square with its corners cut where the two offsets add up to half as much again (MiniHUD's octagon). */
        OCTAGON {
            override fun contains(du: Double, dv: Double, radius: Double) =
                SQUARE.contains(du, dv, radius) && abs(du) + abs(dv) <= radius * 1.5
        };

        /** Whether a point [du], [dv] from the middle is inside at [radius]. */
        abstract fun contains(du: Double, dv: Double, radius: Double): Boolean

        /**
         * The same for a block [u], [v] whole blocks from the middle block, as MiniHUD tests each
         * layer of a cone or pyramid (`isInsideLayer`): no allowance at the edge.
         */
        fun containsBlocks(u: Int, v: Int, radius: Double): Boolean = when (this) {
            CIRCLE -> u * u + v * v <= radius * radius
            SQUARE -> abs(u) <= radius && abs(v) <= radius
            RHOMBUS -> abs(u) + abs(v) <= radius
            OCTAGON -> abs(u) <= radius && abs(v) <= radius && abs(u) + abs(v) <= radius * 1.5
        }
    }
}

/** From a block's corner to its middle. */
private const val HALF = 0.5
