package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.AddShapeWindow
import com.skystormer.skysmapshapes.gui.ShapeEditScreen
import com.skystormer.skysmapshapes.gui.MoveToMiniHudScreen
import com.skystormer.skysmapshapes.gui.ShareScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import xaero.map.gui.IRightClickableElement
import xaero.map.gui.dropdown.rightclick.RightClickOption
import xaero.map.mods.gui.Waypoint
import kotlin.math.floor

/**
 * What this mod adds to Xaero's world map right-click menus: on the map itself, on a waypoint,
 * and on a shape's label. Called from the mixins; a failure here costs the options, not the menu.
 */
object MapMenus {

    /** The map itself, right-clicked at block ([x], [z]) of [dimension]. */
    @JvmStatic
    fun addMapOptions(options: ArrayList<RightClickOption>, target: IRightClickableElement, screen: Screen, x: Int, z: Int, dimension: ResourceKey<Level>?) {
        if (!Config.enabled) return
        guard {
            val dim = dimension?.identifier()?.toString() ?: Dimensions.ofMap()
                ?: return@guard Log.warn("Map right-click: no dimension, so no shape options")
            if (!ShapeStore.isOpen) return@guard Log.warn("Map right-click: no shapes file open (not in a world?)")
            options.add(option("Add shape here", options.size, target) { parent -> add(parent, dim, x, z, "") })
            // Every shape under the click can be edited from here, even one whose label is off screen.
            val under = ShapeStore.inDimension(dim).filter { it.contains(x, z) }
            for (shape in under.take(MAX_UNDER_CLICK)) {
                options.add(option("Edit shape: ${shape.name}", options.size, target) { parent -> edit(parent, shape) })
            }
        }
    }

    /** A waypoint on the world map: a shape centred on it, named after it. */
    @JvmStatic
    fun addWaypointOptions(options: ArrayList<RightClickOption>, target: IRightClickableElement, waypoint: Waypoint) {
        if (!Config.enabled) return
        guard {
            val dim = Dimensions.ofMap() ?: return@guard Log.warn("Waypoint right-click: no map dimension, so no shape option")
            if (!ShapeStore.isOpen) return@guard Log.warn("Waypoint right-click: no shapes file open (not in a world?)")
            // The waypoint's position on the map being shown, which for one from another
            // dimension is already scaled (overworld ↔ nether) by Xaero.
            val x = floor(waypoint.renderX).toInt()
            val z = floor(waypoint.renderZ).toInt()
            val name = waypoint.name
            // The waypoint's height, when it has one, so a MiniHUD shape made here is centred on it.
            val y = if (waypoint.isyIncluded()) waypoint.y else null
            options.add(option("Add shape here", options.size, target) { parent -> add(parent, dim, x, z, name, y) })
        }
    }

    /**
     * A shape's label or outline on the world map, or its line in the Shapes panel, where it may be
     * hidden. MiniHUD's are edited in MiniHUD.
     */
    fun addShapeOptions(options: ArrayList<RightClickOption>, target: IRightClickableElement, shape: MapShape) {
        if (shape is MiniHudShape) {
            options.add(option(
                "Edit in MiniHUD…", options.size, target,
                tip = if (shape.editable) "Opens this shape in MiniHUD's own Shape Editor."
                else "MiniHUD can only edit shapes in the dimension you are in.\n" +
                    "Go to the ${Dimensions.name(shape.dimension)} to edit this one.",
            ) { parent -> MiniHudShapes.openEditor(shape, parent) }.setActive(shape.editable))
            val cannot = if (shape.changeable) null else "MiniHUD's file for the ${Dimensions.name(shape.dimension)} could not be found."
            if (!shape.visible) {
                // Only from the panel: the map has nothing to right-click on.
                options.add(option("Show on the map", options.size, target,
                    tip = "Back on the map. In MiniHUD it stays ${if (shape.enabledInMiniHud) "on" else "off"}.") { _ ->
                    ShapeStore.setVisible(shape, true)
                    say("Showing ${shape.name} on the map.")
                })
                if (!shape.enabledInMiniHud) {
                    options.add(option("Show in both", options.size, target,
                        tip = cannot ?: "On the map, and switched on in MiniHUD so it is in the world too.") { _ ->
                        if (ShapeStore.setInMiniHud(shape, true)) {
                            ShapeStore.setVisible(shape, true)
                            say("Showing ${shape.name} on the map and in MiniHUD.")
                        } else {
                            say("MiniHUD would not switch ${shape.name} on; the log says why.")
                        }
                    }.setActive(shape.changeable))
                }
                options.add(option(if (shape.enabledInMiniHud) "Switch off in MiniHUD" else "Switch on in MiniHUD", options.size, target,
                    tip = cannot ?: "Leaves it off the map.") { _ ->
                    val on = !shape.enabledInMiniHud
                    if (ShapeStore.setInMiniHud(shape, on)) say("Switched ${shape.name} ${if (on) "on" else "off"} in MiniHUD.")
                    else say("MiniHUD would not switch ${shape.name} ${if (on) "on" else "off"}; the log says why.")
                }.setActive(shape.changeable))
            } else if (shape.enabledInMiniHud) {
                options.add(option("Hide in both", options.size, target,
                    tip = cannot ?: "Off the map, and switched off in MiniHUD so it goes from the world too.") { _ ->
                    if (ShapeStore.setInMiniHud(shape, false)) {
                        ShapeStore.setVisible(shape, false)
                        say("Hid ${shape.name} on the map and in MiniHUD. Show it again from the Shapes list.")
                    } else {
                        say("MiniHUD would not switch ${shape.name} off; the log says why.")
                    }
                }.setActive(shape.changeable))
                options.add(option("Hide on the map only", options.size, target,
                    tip = "Off the map. It stays on in MiniHUD, in the world.") { _ ->
                    ShapeStore.setVisible(shape, false)
                    say("Hid ${shape.name} on the map. It is still on in MiniHUD.")
                })
                options.add(option("Hide in MiniHUD only", options.size, target,
                    tip = cannot ?: "Switched off in MiniHUD, so it goes from the world. It stays on the map.") { _ ->
                    if (ShapeStore.setInMiniHud(shape, false)) say("Switched ${shape.name} off in MiniHUD. It is still on the map.")
                    else say("MiniHUD would not switch ${shape.name} off; the log says why.")
                }.setActive(shape.changeable))
            } else {
                options.add(option("Hide on the map", options.size, target, tip = "It is already off in MiniHUD.") { _ ->
                    ShapeStore.setVisible(shape, false)
                    say("Hid ${shape.name}. Show it again from the Shapes list.")
                })
                options.add(option("Switch on in MiniHUD", options.size, target,
                    tip = cannot ?: "Switched back on in MiniHUD, so it is in the world again.") { _ ->
                    if (ShapeStore.setInMiniHud(shape, true)) say("Switched ${shape.name} on in MiniHUD.")
                    else say("MiniHUD would not switch ${shape.name} on; the log says why.")
                }.setActive(shape.changeable))
            }
            if (ShapeShare.asShape(shape) != null) {
                options.add(option("Share in chat…", options.size, target) { parent -> confirmShare(parent, shape) })
            }
            options.add(option("Delete…", options.size, target,
                tip = cannot ?: "Deletes it from MiniHUD, which takes it off the map too.") { parent -> confirmDelete(parent, shape) }
                .setActive(shape.changeable))
            return
        }
        if (shape !is Shape) return
        options.add(option("Edit…", options.size, target) { parent -> edit(parent, shape) })
        if (shape.visible) {
            options.add(option("Hide", options.size, target) { _ ->
                ShapeStore.setVisible(shape, false)
                say("Hid ${shape.name}. Show it again from the Shapes panel or list.")
            })
        } else {
            options.add(option("Show", options.size, target) { _ ->
                ShapeStore.setVisible(shape, true)
                say("Showing ${shape.name}.")
            })
        }
        options.add(option("Share in chat…", options.size, target) { parent -> confirmShare(parent, shape) })
        if (MiniHudShapes.installed && Config.showMiniHud) {
            // Greyed out from another dimension, and the line itself says why on hover.
            val why = whyNotMoveToMiniHud(shape)
            options.add(option(
                "Move into MiniHUD…", options.size, target,
                tip = "Turn this into a MiniHUD shape, shown in the world as well as on the map." +
                    if (why != null) "\n" + why else "",
            ) { parent -> confirmMoveToMiniHud(parent, shape) }.setActive(why == null))
        }
        options.add(option("Delete…", options.size, target) { parent -> confirmDelete(parent, shape) })
    }

    /**
     * A new shape at block ([x], [z]), at height [y] when it is known (a waypoint's): on the world map, in the window beside the Shapes panel with
     * the shape drawn live; anywhere else, on the full add screen.
     */
    fun add(parent: Screen?, dimension: String, x: Int, z: Int, label: String, y: Int? = null) {
        if (isWorldMap(parent)) AddShapeWindow.openNew(parent!!, dimension, x, z, label, y)
        else open(ShapeEditScreen.forNew(parent, dimension, x, z, label, y))
    }

    /** Editing one of this mod's shapes, the same way round as [add]. */
    fun edit(parent: Screen?, shape: Shape) {
        val current = ShapeStore.byId(shape.id) ?: return
        if (isWorldMap(parent) && Dimensions.ofMap() == current.dimension) AddShapeWindow.openExisting(parent!!, current)
        else open(ShapeEditScreen.forExisting(parent, current))
    }

    private fun isWorldMap(screen: Screen?) = screen?.javaClass?.name == "xaero.map.gui.GuiMap"

    /** Asks who to share with first: everyone, or one player privately. */
    fun confirmShare(parent: Screen?, shape: MapShape) {
        if (ShapeShare.asShape(shape) == null) {
            say("${shape.name} is a kind of shape that cannot be shared.")
            return
        }
        open(ShareScreen(parent, shape))
    }

    /**
     * Asks first, and how it should stand up in the world, then makes [shape] in MiniHUD and takes
     * it off this mod's map, so there is one of it rather than two. A shape in another dimension
     * goes into MiniHUD's file for it.
     */
    fun confirmMoveToMiniHud(parent: Screen?, shape: Shape) {
        val player = Minecraft.getInstance().player
        val why = whyNotMoveToMiniHud(shape)
        if (player == null || why != null) {
            // Said on a screen of its own: the action bar is hidden while a screen is open.
            open(ConfirmScreen(
                { _ -> open(parent) },
                Component.literal("${shape.name} cannot move into MiniHUD yet"),
                Component.literal(why ?: "Join a world first."),
                CommonComponents.GUI_BACK,
                CommonComponents.GUI_BACK,
            ))
            return
        }
        if (ShapeStore.byId(shape.id) == null) {
            say("${shape.name} is not on your map any more.")
            return
        }
        val y = miniHudY(shape)
        open(MoveToMiniHudScreen(parent, shape, y) { form ->
            // Checked again here: the screen it was started from may have been left open.
            if (ShapeStore.byId(shape.id) != null) {
                // Only taken off this map once MiniHUD has it, at its full size.
                val failed = MiniHudShapes.create(shape, y, form = form)
                if (failed == null) {
                    ShapeStore.remove(shape.id)
                    say("${shape.name} is now a MiniHUD ${MiniHudGeometry.formName(shape.type, form).lowercase()}" +
                        if (Dimensions.ofPlayer() == shape.dimension) "" else ", there when you next go to the ${Dimensions.name(shape.dimension)}")
                } else {
                    say(failed)
                }
            }
            open(parent)
        })
    }

    /**
     * Asks first, then deletes one of MiniHUD's shapes from MiniHUD, which takes it off the map
     * too, in any dimension: another dimension's shapes are deleted from MiniHUD's file for it.
     */
    fun confirmDelete(parent: Screen?, shape: MiniHudShape) {
        open(ConfirmScreen(
            { yes ->
                if (yes) {
                    if (MiniHudShapes.delete(shape)) {
                        ShapeStore.forgetMiniHud(shape.id)
                        say("Deleted ${shape.name} from MiniHUD and the map")
                    } else {
                        say("MiniHUD would not delete ${shape.name}; the log says why.")
                    }
                }
                open(parent)
            },
            Component.literal("Delete ${shape.name} from MiniHUD?"),
            Component.literal(
                "${shape.describeSize()}, ${Dimensions.name(shape.dimension)}. It goes from MiniHUD and the map. This cannot be undone." +
                    if (shape.editable) "" else " It is in another dimension, so it is taken out of MiniHUD's file for it."
            ),
        ))
    }

    /** Asks first, then deletes [shape] and goes back to [parent]. */
    fun confirmDelete(parent: Screen?, shape: Shape) {
        open(ConfirmScreen(
            { yes ->
                if (yes) {
                    ShapeStore.remove(shape.id)
                    say("Deleted ${shape.name}")
                }
                open(parent)
            },
            Component.literal("Delete ${shape.name}?"),
            Component.literal("${shape.describeSize()}, ${shape.describePosition().replaceFirstChar { it.lowercase() }}. This cannot be undone."),
        ))
    }

    /** Why a shape cannot be moved into MiniHUD, for a tooltip; null when it can. */
    fun whyNotMoveToMiniHud(shape: Shape): String? = when {
        !MiniHudShapes.installed -> "MiniHUD is not installed."
        !Config.showMiniHud -> "MiniHUD shapes are switched off in the settings."
        else -> MiniHudShapes.whyUnreachable(shape.dimension)
    }

    /**
     * The height a shape is made at in MiniHUD: your feet in its dimension; elsewhere the height
     * it was shared at, or sea level.
     */
    fun miniHudY(shape: Shape): Int {
        val player = Minecraft.getInstance().player
        return if (player != null && Dimensions.ofPlayer() == shape.dimension) player.blockY else shape.y ?: SEA_LEVEL
    }

    fun open(screen: Screen?) {
        Minecraft.getInstance().gui.setScreen(screen)
    }

    /** A message on the action bar, which only this client sees. */
    fun say(message: String) {
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(message))
    }

    /** A line in a right-click menu, with [tip] shown while the mouse is over it. */
    fun option(name: String, index: Int, target: IRightClickableElement, tip: String? = null, action: (Screen) -> Unit) =
        MenuTips.TipOption(name, index, target, tip, action)

    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            Log.error("Could not add shape options to Xaero's right-click menu", e)
        }
    }

    private const val MAX_UNDER_CLICK = 4
    const val SEA_LEVEL = 64
}
