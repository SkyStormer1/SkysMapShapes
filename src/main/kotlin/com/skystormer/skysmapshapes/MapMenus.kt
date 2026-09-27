package com.skystormer.skysmapshapes

import com.skystormer.skysmapshapes.gui.ShapeEditScreen
import com.skystormer.skysmapshapes.gui.ShapeListScreen
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
            options.add(option("Add shape here", options.size, target) { parent ->
                open(ShapeEditScreen.forNew(parent, dim, x, z, ""))
            })
            // Every shape under the click can be edited from here, even one whose label is off screen.
            val under = ShapeStore.inDimension(dim).filter { it.contains(x, z) }
            for (shape in under.take(MAX_UNDER_CLICK)) {
                options.add(option("Edit shape: ${shape.name}", options.size, target) { parent ->
                    ShapeStore.byId(shape.id)?.let { open(ShapeEditScreen.forExisting(parent, it)) }
                })
            }
            options.add(option("All shapes…", options.size, target) { parent -> open(ShapeListScreen(parent)) })
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
            options.add(option("Add shape here", options.size, target) { parent ->
                open(ShapeEditScreen.forNew(parent, dim, x, z, name))
            })
        }
    }

    /** A shape's label or outline on the world map. MiniHUD's can only be hidden here. */
    fun addShapeOptions(options: ArrayList<RightClickOption>, target: IRightClickableElement, shape: MapShape) {
        if (shape is MiniHudShape) {
            options.add(option(
                "Edit in MiniHUD…", options.size, target,
                tip = if (shape.editable) "Opens this shape in MiniHUD's own Shape Editor."
                else "MiniHUD can only edit shapes in the dimension you are in.\n" +
                    "Go to the ${Dimensions.name(shape.dimension)} to edit this one.",
            ) { parent -> MiniHudShapes.openEditor(shape, parent) }.setActive(shape.editable))
            options.add(option(if (Config.hideInMiniHud && shape.editable) "Hide, in MiniHUD too" else "Hide on the map", options.size, target) { _ ->
                ShapeStore.setVisible(shape, false)
                say("Hid ${shape.name}. Show it again from the Shapes list.")
            })
            if (ShapeShare.asShape(shape) != null) {
                options.add(option("Share in chat…", options.size, target) { parent -> confirmShare(parent, shape) })
            }
            if (Config.hideInMiniHud && shape.editable) {
                options.add(option("Hide on the map only", options.size, target) { _ ->
                    ShapeStore.hideOnMapOnly(shape)
                    say("Hid ${shape.name} on the map. It is still on in MiniHUD.")
                })
            }
            return
        }
        if (shape !is Shape) return
        options.add(option("Edit…", options.size, target) { parent ->
            ShapeStore.byId(shape.id)?.let { open(ShapeEditScreen.forExisting(parent, it)) }
        })
        options.add(option("Hide", options.size, target) { _ ->
            ShapeStore.setVisible(shape, false)
            say("Hid ${shape.name}. Show it again from the Shapes list.")
        })
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

    /** Asks who to share with first: everyone, or one player privately. */
    fun confirmShare(parent: Screen?, shape: MapShape) {
        if (ShapeShare.asShape(shape) == null) {
            say("${shape.name} is a kind of shape that cannot be shared.")
            return
        }
        open(ShareScreen(parent, shape))
    }

    /**
     * Asks first, then makes [shape] in MiniHUD and takes it off this mod's map, so there is one
     * of it rather than two. MiniHUD only holds the dimension you are in.
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
        open(ConfirmScreen(
            { yes ->
                // Checked again here: the screen it was started from may have been left open.
                if (yes && ShapeStore.byId(shape.id) != null) {
                    if (MiniHudShapes.create(shape, player.blockY)) {
                        ShapeStore.remove(shape.id)
                        say("${shape.name} is now a MiniHUD shape")
                    } else {
                        say("MiniHUD would not take that shape; the log says why.")
                    }
                }
                open(parent)
            },
            Component.literal("Move ${shape.name} into MiniHUD?"),
            Component.literal(
                "It becomes an ordinary MiniHUD shape, shown in the world as well as on the map, and edited in MiniHUD from then on. " +
                    "It is put at your height (${player.blockY}), and is taken off this mod's own map so it is not drawn twice."
            ),
            Component.literal("Move it"),
            CommonComponents.GUI_CANCEL,
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
        Dimensions.ofPlayer() != shape.dimension ->
            "Go to the ${Dimensions.name(shape.dimension)} first: MiniHUD only holds shapes for the dimension you are in."
        else -> null
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
}
