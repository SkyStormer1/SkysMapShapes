package com.skystormer.skysmapshapes

import com.google.gson.JsonObject
import java.util.UUID

/**
 * What both shape editors do with the shape they have built: the add window on the map
 * (`AddShapeWindow`) and the full edit screen (`ShapeEditScreen`). Each keeps its own fields and
 * layout; the checks, MiniHUD's "press again" warning and the saving are here, so the two cannot
 * drift apart again, as they did when the full screen saved shapes the add window refused.
 */
internal class ShapeDraft {

    /** Where a new shape goes in MiniHUD: its height, and what it becomes there. */
    class InMiniHud(
        val y: Int,
        val form: MiniHudGeometry.Form?,
        /** A shared MiniHUD shape's own type id, or null for one made from the outline. */
        val typeId: String? = null,
        /** A shared MiniHUD shape's own sizes, while its outline is still the one shared. */
        val sizes: JsonObject? = null,
    )

    /** The shape last warned about as slow to draw, so a second press makes it. */
    private var warned: String? = null

    /** Anything changed since the warning: the next press warns again. */
    fun forget() {
        warned = null
    }

    /**
     * Saves [shape] over [existing], or as a new shape: on the map, or in MiniHUD when [miniHud] is
     * given (new shapes only). Null once done; otherwise what to tell the player, the shape still
     * unsaved. [button] names the press that makes it anyway, after a warning.
     */
    fun save(shape: Shape, existing: Shape?, miniHud: InMiniHud?, button: String): String? {
        // It may have been deleted or moved into MiniHUD while the editor stayed open.
        if (existing != null && ShapeStore.byId(existing.id) == null) return "${existing.name} is not on your map any more."
        // A place far past the world border would be thrown away, with the whole file, on the next join.
        if (!shape.isValid()) return "That size or place is beyond any world."
        if (existing != null) {
            ShapeStore.put(shape.copy(id = existing.id, visible = existing.visible))
            return null
        }
        val made = shape.copy(id = UUID.randomUUID().toString())
        if (miniHud == null) {
            ShapeStore.put(made)
            return null
        }
        // A shape big enough to slow the game is made on the second press, once that has been said.
        val asked = "${made.type} ${made.radius} ${made.width} ${made.length} ${miniHud.y} ${miniHud.form} ${miniHud.typeId}"
        return when (val result = MiniHudShapes.tryCreate(made, miniHud.y, miniHud.form, miniHud.typeId, miniHud.sizes, warned = warned == asked)) {
            is MiniHudShapes.CreateResult.Refused -> result.why
            is MiniHudShapes.CreateResult.WarnFirst -> {
                warned = asked
                "${result.warning} Press $button again to make it anyway."
            }
            MiniHudShapes.CreateResult.Made -> null
        }
    }

    companion object {
        /** Whether [size] in blocks can be typed: more than 0, and no more than [Shape.MAX_TYPED_SIZE]. */
        fun fits(size: Double) = size > 0 && size <= Shape.MAX_TYPED_SIZE

        /** A typed size in blocks, or null if it is not a number or does not [fits]. */
        fun size(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf(::fits)
    }
}
