package com.skystormer.skysmapshapes

import net.minecraft.client.Minecraft

/**
 * What other mods can ask of this one: offer to make a shape somewhere, with the add-a-shape
 * window opened over whatever is already on screen (docked beside the Shapes panel on the world
 * map), so the player still decides. Sky's Map Exposer uses it to put "Add shape here" on BlueMap
 * markers, and Sky's Structure Map on structures.
 *
 * Nothing is ever added without the player pressing Add, and nothing here needs this mod to be a
 * dependency: the methods are static, so a caller can find them only when this mod is installed,
 * without compiling against it.
 *
 * ```java
 * if (FabricLoader.getInstance().isModLoaded("skysmapshapes")) {
 *     Class.forName("com.skystormer.skysmapshapes.ShapesApi")
 *          .getMethod("openNewShape", String.class, int.class, int.class, String.class)
 *          .invoke(null, dimension, x, z, label);
 * }
 * ```
 */
object ShapesApi {

    /**
     * Opens the add-a-shape screen for a shape centred on block ([x], [z]) of [dimension], named
     * [label]. Returns false when not in a world, where there is nowhere to keep a shape.
     */
    @JvmStatic
    fun openNewShape(dimension: String, x: Int, z: Int, label: String): Boolean = open(dimension, x, null, z, label)

    /**
     * The same, at height [y] as well, so a shape later moved into MiniHUD is centred on it.
     */
    @JvmStatic
    fun openNewShape(dimension: String, x: Int, y: Int, z: Int, label: String): Boolean = open(dimension, x, y, z, label)

    /**
     * The same, for the dimension Xaero's world map is showing, which is what a marker on that map
     * belongs to. Returns false when the map has not opened yet.
     */
    @JvmStatic
    fun openNewShapeOnMap(x: Int, z: Int, label: String): Boolean {
        val dimension = Dimensions.ofMap() ?: return false
        return open(dimension, x, null, z, label)
    }

    /** The same, at height [y] as well. */
    @JvmStatic
    fun openNewShapeOnMap(x: Int, y: Int, z: Int, label: String): Boolean {
        val dimension = Dimensions.ofMap() ?: return false
        return open(dimension, x, y, z, label)
    }

    private fun open(dimension: String, x: Int, y: Int?, z: Int, label: String): Boolean {
        if (!Config.enabled || !ShapeStore.isOpen) return false
        MapMenus.add(Minecraft.getInstance().gui.screen(), dimension, x, z, label, y)
        return true
    }
}
