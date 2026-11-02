package com.skystormer.skysmapshapes

import java.lang.reflect.Field

/**
 * MiniHUD's light level overlay, read by reflection: the list of spots it will mark, and each
 * spot's packed block position and block light. Shared by [LightLevels] and [SpawnProofing], which
 * both work on that list just after MiniHUD gathers it.
 */
object MiniHudLight {

    private var spotsField: Field? = null
    private var posField: Field? = null
    private var blockField: Field? = null

    /** MiniHUD's list of spots to mark, which can be changed in place. */
    @Suppress("UNCHECKED_CAST")
    fun spots(overlay: Any): MutableList<Any?> {
        val field = spotsField ?: overlay.javaClass.getDeclaredField(SPOTS).also { it.isAccessible = true; spotsField = it }
        return field.get(overlay) as MutableList<Any?>
    }

    /** [spot]'s block position, packed as `BlockPos.asLong` does. */
    fun pos(spot: Any): Long = (posField ?: spot.javaClass.getField(POS).also { posField = it }).getLong(spot)

    /** The block light at [spot]. */
    fun blockLight(spot: Any): Int = (blockField ?: spot.javaClass.getField(BLOCK_LIGHT).also { blockField = it }).getByte(spot).toInt()

    private const val SPOTS = "lightInfos"
    private const val POS = "pos"
    private const val BLOCK_LIGHT = "block"
}
