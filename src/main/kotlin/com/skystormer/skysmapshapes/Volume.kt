package com.skystormer.skysmapshapes

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

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

    /** An outline seen from above, at every height. */
    class Column(private val footprint: Geometry) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean = footprint.contains(x + HALF, z + HALF)
    }

    /**
     * A cross-section stood along an axis for [length] blocks, from block [start] on that axis
     * going [step] (1 or -1) each layer: a prism when [startRadius] and [endRadius] are equal,
     * a cone or pyramid when they are not. ([cu], [cv]) is its middle on the other two axes.
     */
    class Extruded(
        private val axis: Axis,
        private val start: Int,
        private val step: Int,
        private val length: Int,
        private val cu: Double,
        private val cv: Double,
        private val section: Section,
        private val startRadius: Double,
        private val endRadius: Double,
    ) : Volume {
        override fun contains(x: Int, y: Int, z: Int): Boolean {
            val (along, u, v) = when (axis) {
                Axis.X -> Triple(x, y, z)
                Axis.Y -> Triple(y, x, z)
                Axis.Z -> Triple(z, x, y)
            }
            val layer = (along - start) * step
            if (layer !in 0 until length) return false
            val radius = if (length <= 1) startRadius else startRadius + (endRadius - startRadius) * layer / (length - 1)
            return section.contains(u + HALF - cu, v + HALF - cv, radius)
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
            override fun contains(du: Double, dv: Double, radius: Double) = abs(du) + abs(dv) <= radius
        },

        /** Flat sides [radius] from the middle, square-on and diagonal alike. */
        OCTAGON {
            override fun contains(du: Double, dv: Double, radius: Double) =
                SQUARE.contains(du, dv, radius) && abs(du) + abs(dv) <= radius * SQRT_2
        };

        abstract fun contains(du: Double, dv: Double, radius: Double): Boolean
    }
}

/** From a block's corner to its middle. */
private const val HALF = 0.5
private val SQRT_2 = sqrt(2.0)
