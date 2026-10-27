package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.AddShapeWindow

/** This mod's own shapes and MiniHUD's together: what the map, the labels and the list show. */
object MapShapes {

    /** What a list was made from; it is kept for as long as none of these change. */
    private class Made(
        val storeVersion: Int,
        val miniHudVersion: Int,
        val includeDisabled: Boolean,
        val editing: String?,
        val shapes: List<MapShape>,
    )

    private val made = HashMap<String, Made>()

    /**
     * The shapes shown in [dimension], largest first: everything is drawn in this order, so a
     * smaller shape always shows on top of a larger one it overlaps.
     *
     * Several parts of the map ask for this several times a frame, so the list is kept until the
     * shapes change. The draft of a shape being made moves with the mouse, so it is not kept: it
     * is added to a copy while the add window is open.
     */
    fun visibleIn(dimension: String): List<MapShape> {
        // A shape being edited on the map is drawn as its draft instead.
        val editing = AddShapeWindow.editingId()
        val base = shapesIn(dimension, editing)
        val draft = AddShapeWindow.draft()?.takeIf { it.dimension == dimension } ?: return base
        return (base + draft).sortedByDescending { it.area }
    }

    private fun shapesIn(dimension: String, editing: String?): List<MapShape> {
        val storeVersion = ShapeStore.version
        val miniHudVersion = MiniHudShapes.version
        val includeDisabled = Config.miniHudIncludeDisabled
        made[dimension]?.let {
            if (it.storeVersion == storeVersion && it.miniHudVersion == miniHudVersion &&
                it.includeDisabled == includeDisabled && it.editing == editing) return it.shapes
        }
        val shapes = (ShapeStore.inDimension(dimension).filter { it.id != editing } + MiniHudShapes.inDimension(dimension))
            .filter { it.visible }
            .sortedByDescending { it.area }
        made[dimension] = Made(storeVersion, miniHudVersion, includeDisabled, editing, shapes)
        return shapes
    }

    /** Every shape known for this server, hidden ones too. */
    fun all(): List<MapShape> = ShapeStore.all + MiniHudShapes.all
}
