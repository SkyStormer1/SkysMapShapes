package com.skystormer.skysmapshapes

/** This mod's own shapes and MiniHUD's together: what the map, the labels and the list show. */
object MapShapes {

    /**
     * The shapes shown in [dimension], largest first: everything is drawn in this order, so a
     * smaller shape always shows on top of a larger one it overlaps.
     */
    fun visibleIn(dimension: String): List<MapShape> =
        (ShapeStore.inDimension(dimension) + MiniHudShapes.inDimension(dimension))
            .filter { it.visible }
            .sortedByDescending { it.area }

    /** Every shape known for this server, hidden ones too. */
    fun all(): List<MapShape> = ShapeStore.all + MiniHudShapes.all
}
