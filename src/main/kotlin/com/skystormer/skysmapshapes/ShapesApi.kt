package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.ShapeEditScreen
import net.minecraft.client.Minecraft

/**
 * For other mods: open the add-a-shape screen centred on a block, over whatever screen is open.
 * Sky's Map Exposer can offer this on BlueMap markers that are not waypoints. Call it only when
 * `skysmapshapes` is loaded.
 */
object ShapesApi {

    /** Returns false when not in a world, where there is nowhere to keep a shape. */
    @JvmStatic
    fun openNewShape(dimension: String, x: Int, z: Int, label: String): Boolean {
        if (!ShapeStore.isOpen) return false
        val minecraft = Minecraft.getInstance()
        val parent = minecraft.gui.screen()
        minecraft.gui.setScreen(ShapeEditScreen.forNew(parent, dimension, x, z, label))
        return true
    }
}
