package com.skystormer.skysmapshapes

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.StringArgumentType
import com.skystormer.skysmapshapes.gui.AddShapeWindow
import com.skystormer.skysmapshapes.gui.ShapeListScreen
import com.skystormer.skysmapshapes.gui.ShapesPanel
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.SharedConstants
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier

object ShapesClient : ClientModInitializer {

    private var labelsAdded = false
    private var hookChecked = false

    /** Opens the shapes list. Unbound until you pick a key in Controls. */
    private val listKey = KeyMapping(
        "key.skysmapshapes.list",
        InputConstants.Type.KEYBOARD,
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

        // A shape shared in chat becomes a message with an add button; everything else is untouched.
        ClientReceiveMessageEvents.ALLOW_CHAT.register { message, _, _, _, _ -> ShapeShare.onChat(message.string) }
        // Action-bar text comes through here too ([overlay]); a shape there is not turned into a chat line.
        ClientReceiveMessageEvents.ALLOW_GAME.register { message, overlay -> overlay || ShapeShare.onChat(message.string) }

        // `fix <check>` is spawn proofing's [Fix it]; any other word is a shared shape's code. Brigadier
        // tries a matching literal before the argument, and no code is ever the word "fix".
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommands.literal(ShapeShare.COMMAND)
                    .then(
                        ClientCommands.literal(SpawnProofSetup.FIX).then(
                            ClientCommands.argument("check", StringArgumentType.word()).executes { context ->
                                SpawnProofSetup.fixFromCommand(StringArgumentType.getString(context, "check"))
                                1
                            }
                        )
                    )
                    .then(
                        ClientCommands.argument("code", StringArgumentType.word()).executes { context ->
                            ShapeShare.accept(StringArgumentType.getString(context, "code"))
                            1
                        }
                    )
            )
        }

        ClientPlayConnectionEvents.JOIN.register { _, _, client -> client.execute { ShapeStore.open(client) } }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client ->
            client.execute {
                ShapeStore.pruneHidden(MiniHudShapes.idsForPruning())
                ShapeStore.close()
                MiniHudShapes.clear()
                ShapeShare.clear()
                ShapeHover.clear()
                SpawnProofing.reset()
                SpawnProofSetup.reset()
            }
        }

        // The Shapes panel on Xaero's world map, which docks with Sky's Map Exposer's bar and Sky's
        // Structure Map's legend.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (Config.enabled && screen.javaClass.name == "xaero.map.gui.GuiMap") {
                try {
                    ShapesPanel.addTo(screen)
                    AddShapeWindow.install(screen)
                } catch (e: Throwable) {
                    Log.error("Could not add the Shapes panel to Xaero's world map", e)
                }
            }
        }

        // MiniHUD's shape screens closing; the next tick reads MiniHUD again.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (MiniHudShapes.installed && screen.javaClass.name.startsWith("fi.dy.masa.minihud.gui.GuiShape")) {
                ScreenEvents.remove(screen).register { MiniHudShapes.markDirty() }
            }
        }

        // Spawn proofing's through-blocks outlines, added as each frame is gathered.
        LevelExtractionEvents.END_EXTRACTION.register {
            SpawnProofing.drawOutlines()
            BeaconBeams.draw()
        }

        // Before the game handles the use key, so Easy Place gets the swapped-in block.
        ClientTickEvents.START_CLIENT_TICK.register { client -> SpawnProofing.beforeUse(client) }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (!hookChecked) {
                hookChecked = true
                reportMinimapHooks()
                if (hasHook("xaero.map.gui.GuiMap", "drawShapes")) Log.info("Xaero hooks installed") else Log.warn("This version of Xaero's World Map is not supported; shapes will not be drawn on it")
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
            LightLevels.tick()
            SpawnProofing.tick()
            SpawnProofSetup.tick()
            BeaconBeams.tick()
            ShapeShare.tick()
            // Hover only means anything while the world map is open.
            if (client.gui.screen()?.javaClass?.name != "xaero.map.gui.GuiMap") ShapeHover.clear()
            while (listKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(ShapeListScreen(null))
            }
        }
    }

    /**
     * Which of the two minimap hooks made it in: the one for terrain drawn from the world map, and
     * the one for the minimap's own (used underground and in the Nether). Both are optional, so
     * this is how a missing one gets noticed rather than shapes quietly not appearing.
     */
    private fun reportMinimapHooks() {
        // The same ids as the minimap hooks' MinimapMixinPlugin: the minimap, or its Fair-Play build.
        if (listOf("xaerominimap", "xaerominimapfair").none(FabricLoader.getInstance()::isModLoaded)) return
        val fromWorldMap = hasHook("xaero.common.mods.SupportXaeroWorldmap", "drawShapes")
        val ownData = hasHook("xaero.common.minimap.render.MinimapFBORenderer", "drawShapes")
        // Applying the class is not the same as finding the spot inside it; the drawing itself
        // logs when it first runs, which is what proves a hook works.
        Log.info("Minimap hook classes applied: world map's data = {}, minimap's own data = {}", fromWorldMap, ownData)
        if (!ownData) Log.warn("This version of Xaero's Minimap is not supported underground or in the Nether; shapes will be missing there")
    }

    /**
     * Whether a mixin method named like [name] made it into [className]. The hooks are optional, so
     * that an unsupported Xaero version costs the shapes and not the game; this is how that gets noticed.
     */
    private fun hasHook(className: String, name: String): Boolean = try {
        Class.forName(className).declaredMethods.any { it.name.contains(name) }
    } catch (e: Throwable) {
        false
    }
}
