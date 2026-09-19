package com.skystormer.skysmapshapes

import com.mojang.blaze3d.platform.InputConstants
import com.skystormer.skysmapshapes.gui.ShapeListScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object ShapesClient : ClientModInitializer {

    private var labelsAdded = false
    private var hookChecked = false

    /** Opens the shapes list. Unbound until you pick a key in Controls. */
    private val listKey = KeyMapping(
        "key.skysmapshapes.list",
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.value,
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skysmapshapes", "shapes")),
    )

    override fun onInitializeClient() {
        // Development testing only (gradle runClient -PnotIde): see build.gradle.kts. Never set otherwise.
        if (FabricLoader.getInstance().isDevelopmentEnvironment && System.getProperty("skysmapshapes.notIde") != null) {
            SharedConstants.IS_RUNNING_IN_IDE = false
            Log.info("Development client: IS_RUNNING_IN_IDE turned off for MaLiLib")
        }
        Config.load()
        KeyMappingHelper.registerKeyMapping(listKey)

        ClientPlayConnectionEvents.JOIN.register { _, _, client -> client.execute { ShapeStore.open(client) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            client.execute {
                ShapeStore.pruneHidden(MiniHudShapes.all.mapTo(HashSet()) { it.id })
                ShapeStore.close()
                MiniHudShapes.clear()
                ShapeHover.clear()
            }
        }

        // A Shapes button on Xaero's world map, under its settings button in the top-left corner.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (Config.enabled && screen.javaClass.name == "xaero.map.gui.GuiMap") {
                Screens.getWidgets(screen).add(
                    Button.builder(Component.literal("Shapes")) { Screens.getMinecraft(screen).gui.setScreen(ShapeListScreen(screen)) }
                        .bounds(0, 32, 44, 20)
                        .tooltip(Tooltip.create(Component.literal("All your shapes: show, hide, find, edit or delete them.")))
                        .build()
                )
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!hookChecked) {
                hookChecked = true
                if (hookInstalled()) Log.info("Xaero hooks installed") else Log.warn("This version of Xaero's World Map is not supported; shapes will not be drawn on it")
            }
            if (!labelsAdded) {
                labelsAdded = try {
                    ShapeLabels.register().also { if (it) Log.info("Labels added to Xaero's world map") }
                } catch (e: Throwable) {
                    Log.error("Could not add shape labels to Xaero's world map", e)
                    true
                }
            }
            MiniHudShapes.tick(client)
            // Hover only means anything while the world map is open.
            if (client.gui.screen()?.javaClass?.name != "xaero.map.gui.GuiMap") ShapeHover.clear()
            while (listKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(ShapeListScreen(null))
            }
        }
    }

    /**
     * Whether the drawing mixin made it into Xaero's map screen. It is optional, so that an
     * unsupported Xaero version costs the shapes and not the game; this is how that gets noticed.
     */
    private fun hookInstalled(): Boolean = try {
        Class.forName("xaero.map.gui.GuiMap").declaredMethods.any { it.name.contains("drawShapes") }
    } catch (e: Throwable) {
        false
    }
}
