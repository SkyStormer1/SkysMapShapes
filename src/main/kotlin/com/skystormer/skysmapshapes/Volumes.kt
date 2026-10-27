package com.skystormer.skysmapshapes

/**
 * The space each shape takes up in the world, for the things that work block by block: light
 * levels and spawn proofing. Only shapes that are there count: MiniHUD's while they are switched
 * on in MiniHUD, and this mod's own while they are shown on the map. This mod's shapes have no
 * height yet, so they count at every height inside their outline.
 */
object Volumes {

    /** The id and [Volume] of each shape in [dimension] that is there and that [keep] says yes to. */
    fun inDimension(dimension: String, keep: (String) -> Boolean = { true }): List<Pair<String, Volume>> {
        val out = ArrayList<Pair<String, Volume>>()
        for (shape in ShapeStore.inDimension(dimension)) {
            if (shape.visible && keep(shape.id)) out += shape.id to Volume.Column(shape.geometry)
        }
        for (shape in MiniHudShapes.inDimension(dimension)) {
            if (shape.enabledInMiniHud && keep(shape.id)) shape.volume?.let { out += shape.id to it }
        }
        return out
    }
}
