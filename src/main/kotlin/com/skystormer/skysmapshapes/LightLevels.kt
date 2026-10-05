package com.skystormer.skysmapshapes

import java.lang.reflect.Field
import java.lang.reflect.Method
import net.minecraft.core.BlockPos

/**
 * Keeps MiniHUD's light level overlay to inside your shapes, when the setting asks for it.
 *
 * MiniHUD gathers every spot it will mark near you into a list, then draws the list.
 * `OverlayRendererLightLevelMixin` calls [filter] just after the list is gathered, and spots
 * outside the shapes are taken out of it. Each shape has a [Role]: spots inside a
 * [Role.NO_LIGHT] shape always go, even where it sits inside a [Role.SHOW] one, so a build you
 * mean to dig out can be left dark inside the area you are lighting up.
 *
 * Only shapes that are there count: MiniHUD's while they are switched on in MiniHUD, and this
 * mod's own while they are shown on the map. This mod's shapes have no height yet, so they count
 * at every height inside their outline.
 *
 * MiniHUD is reached by reflection and one optional hook, so nothing of it is copied here; if a
 * MiniHUD update changes what is hooked, its light levels simply show as MiniHUD has them.
 */
object LightLevels {

    /** What a shape does to the light levels inside it. */
    enum class Role(val saved: String, val title: String, val tip: String) {
        SHOW("show", "show", "Light levels show inside this shape."),
        NO_LIGHT("none", "none", "No light levels inside this shape, even where it is inside another shape that shows them."),
        IGNORE("ignore", "ignore", "This shape makes no difference to where light levels show.");

        /** The role a click in the right-click menu moves on to. */
        val next: Role get() = entries[(ordinal + 1) % entries.size]

        companion object {
            fun fromSaved(text: String): Role? = entries.firstOrNull { it.saved == text }
        }
    }

    /** Whether light levels are being kept to inside shapes right now. */
    val active: Boolean
        get() = Config.enabled && Config.lightInsideShapes && Config.showMiniHud && MiniHudShapes.installed

    private var broken = false
    private var hookSeen = false
    private var spotsField: Field? = null
    private var posField: Field? = null
    private var needsUpdate: Pair<Any, Method>? = null

    /** What the shapes looked like at the last [tick], to notice a change that MiniHUD would not. */
    private var lastState = 0
    private var ticks = 0

    /**
     * Takes the spots outside the shapes out of [overlay]'s list, when [active]. Says whether it
     * did, so the hook can then tell MiniHUD whether [anyLeft] to draw.
     */
    @JvmStatic
    fun filter(overlay: Any): Boolean {
        if (broken || !active) return false
        if (!hookSeen) {
            hookSeen = true
            Log.info("Keeping MiniHUD's light levels to inside shapes")
        }
        return try {
            val spots = spots(overlay)
            if (spots.isEmpty()) return true
            val pos = posField ?: (spots.first() ?: return false).javaClass.getField(POS).also { posField = it }
            val (lit, dark) = volumes(Dimensions.ofPlayer() ?: return false)
            spots.removeIf { spot ->
                val packed = pos.getLong(spot)
                val x = BlockPos.getX(packed)
                val y = BlockPos.getY(packed)
                val z = BlockPos.getZ(packed)
                dark.any { it.contains(x, y, z) } || lit.none { it.contains(x, y, z) }
            }
            true
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not keep MiniHUD's light levels to inside shapes; they show as MiniHUD has them until the game restarts", e)
            false
        }
    }

    /** Whether [overlay] still has spots to mark after [filter]. */
    @JvmStatic
    fun anyLeft(overlay: Any): Boolean = spots(overlay).isNotEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun spots(overlay: Any): MutableList<Any?> {
        val field = spotsField ?: overlay.javaClass.getDeclaredField(SPOTS).also { it.isAccessible = true; spotsField = it }
        return field.get(overlay) as MutableList<Any?>
    }

    /** The volumes in [dimension] that light levels show in, and the ones they never show in. */
    private fun volumes(dimension: String): Pair<List<Volume>, List<Volume>> {
        val lit = ArrayList<Volume>()
        val dark = ArrayList<Volume>()
        fun add(id: String, volume: Volume) {
            when (ShapeStore.lightRole(id)) {
                Role.SHOW -> lit += volume
                Role.NO_LIGHT -> dark += volume
                Role.IGNORE -> {}
            }
        }
        for (shape in ShapeStore.inDimension(dimension)) if (shape.visible) add(shape.id, Volume.Column(shape.geometry))
        for (shape in MiniHudShapes.inDimension(dimension)) {
            if (shape.enabledInMiniHud) shape.volume?.let { add(shape.id, it) }
        }
        return lit to dark
    }

    /**
     * Called every client tick. MiniHUD only gathers its spots again when you move, so a shape
     * added, changed, shown or hidden, or the setting switched, would otherwise not show until then.
     */
    fun tick() {
        if (broken || !MiniHudShapes.installed) return
        ticks = (ticks + 1) % CHECK_EVERY
        if (ticks != 0) return
        val state = state()
        if (state != lastState) {
            lastState = state
            refresh()
        }
    }

    private fun state(): Int {
        if (!active) return 0
        val dimension = Dimensions.ofPlayer() ?: return 0
        var hash = 1
        for (shape in ShapeStore.inDimension(dimension)) hash = hash * 31 + (shape.hashCode() xor ShapeStore.lightRole(shape.id).hashCode())
        for (shape in MiniHudShapes.inDimension(dimension)) {
            hash = hash * 31 + (shape.id.hashCode() xor shape.enabledInMiniHud.hashCode() xor ShapeStore.lightRole(shape.id).hashCode())
        }
        return hash
    }

    /** Asks MiniHUD to gather its light level spots again, so a change shows straight away. */
    fun refresh() {
        if (broken || !MiniHudShapes.installed) return
        try {
            val (overlay, method) = needsUpdate ?: run {
                val overlayClass = Class.forName(OVERLAY)
                (overlayClass.getField("INSTANCE").get(null) to overlayClass.getMethod("setNeedsUpdate")).also { needsUpdate = it }
            }
            method.invoke(overlay)
        } catch (e: Throwable) {
            broken = true
            Log.error("Could not ask MiniHUD to redraw its light levels", e)
        }
    }

    private const val OVERLAY = "fi.dy.masa.minihud.renderer.OverlayRendererLightLevel"

    /** MiniHUD's list of spots to mark, and each spot's packed block position. */
    private const val SPOTS = "lightInfos"
    private const val POS = "pos"

    /** Ticks between looks at whether the shapes changed: twice a second, as MiniHUD's are read. */
    private const val CHECK_EVERY = 10
}
