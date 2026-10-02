package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.AddShapeWindow

/** This mod's own shapes and MiniHUD's together: what the map, the labels and the list show. */
object MapShapes {

    /**
     * The shapes shown in [dimension], largest first: everything is drawn in this order, so a
     * smaller shape always shows on top of a larger one it overlaps.
     */
    fun visibleIn(dimension: String): List<MapShape> {
        // A shape being edited on the map is drawn as its draft instead.
        val editing = AddShapeWindow.editingId()
        val draft = AddShapeWindow.draft()?.takeIf { it.dimension == dimension }
        return (ShapeStore.inDimension(dimension).filter { it.id != editing } + MiniHudShapes.inDimension(dimension))
            .filter { it.visible }
            .plus(listOfNotNull(draft))
            .sortedByDescending { it.area }
    }

    /** Every shape known for this server, hidden ones too. */
    fun all(): List<MapShape> = ShapeStore.all + MiniHudShapes.all
}
