package com.skystormer.skysmapshapes

/**
 * Added to Xaero's world map screen by `GuiMapMixin`, so the shapes list can move the map's
 * camera to a shape, the way Xaero's own waypoint list does.
 */
interface MapCamera {
    /** Glides the map's camera to block ([x], [z]). */
    fun skysmapshapesCentreOn(x: Int, z: Int)
}
