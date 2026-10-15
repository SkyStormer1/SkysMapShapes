package com.skystormer.skysmapshapes.test;

import com.skystormer.skysmapshapes.Colours;
import com.skystormer.skysmapshapes.Config;
import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.minihud.config.Configs;
import fi.dy.masa.minihud.config.RendererToggle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dark stone room in a test world, MiniHUD's light level overlay on and spawn proofing set to
 * everywhere, so every floor in the room is a spot. Screenshots the overlay at a few settings.
 */
public class SpawnProofOverlayGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("SpawnProofOverlayGameTest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set midnight");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule do_daylight_cycle false");
            // Stone shell, 17 by 17 inside, three high: no sky light, no block light.
            server.runCommand("fill -10 98 -10 10 104 10 stone");
            server.runCommand("fill -8 100 -8 8 102 8 air");
            // A lit corner, which spawn proofing leaves alone, and a ready slab to show the difference.
            server.runCommand("setblock 6 100 6 torch");
            server.runCommand("setblock -6 100 -6 stone_slab");
            server.runCommand("item replace entity @a hotbar.0 with stone_slab 64");
            server.runCommand("tp @a -7.5 101.5 -7.5 -45 35");
            context.runOnClient(c -> {
                c.player.getInventory().setSelectedSlot(0);
                c.options.gamma().set(1.0);
            });

            context.runOnClient(c -> {
                Config.INSTANCE.setSpawnProofing(true);
                Config.INSTANCE.setSpawnProofEverywhere(true);
                Config.INSTANCE.setSpawnProofRenderThrough(true);
                Config.INSTANCE.setSpawnProofColour(Colours.AQUA.getArgb());
                Config.INSTANCE.setSpawnProofOpacity(0.25f);
                Config.INSTANCE.setSpawnProofHeight(1);
                Configs.Generic.MAIN_RENDERING_TOGGLE.setBooleanValue(true);
                RendererToggle.OVERLAY_LIGHT_LEVEL.setBooleanValue(true);
                SpawnProofing.INSTANCE.refresh();
            });
            context.waitTicks(100);
            int spots = shownSpots(context);
            LOG.info("spawn proofing spots shown: {}, fill boxes: {}", spots, fillBoxes(context));
            if (spots < 100) throw new AssertionError("expected the room's floor as spots, got " + spots);

            // Easy Place must not stop vanilla placing around the spots: the room's walls and air are
            // inside spawn proofing's placement, where it holds air.
            boolean blocked = context.computeOnClient(c -> {
                try {
                    var m = Class.forName("fi.dy.masa.litematica.util.EasyPlaceUtils").getDeclaredMethod("isPositionWithinRangeOfSchematicRegions", BlockPos.class, int.class);
                    m.setAccessible(true);
                    return (Boolean) m.invoke(null, new BlockPos(0, 101, 0), 2) || (Boolean) m.invoke(null, new BlockPos(8, 102, -8), 2);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            LOG.info("Easy Place sees spawn proofing as a schematic to protect: {}", blocked);
            if (blocked) throw new AssertionError("Easy Place still guards spawn proofing's placement");

            // Covering a spot, and uncovering it, changes the placement in place: no new placement, no blink.
            Object before = placement(context);
            server.runCommand("setblock -4 100 -8 stone_slab");
            context.waitTicks(60);
            int covered = shownSpots(context);
            Object afterCover = placement(context);
            server.runCommand("setblock -4 100 -8 air");
            context.waitTicks(60);
            int uncovered = shownSpots(context);
            Object afterUncover = placement(context);
            LOG.info("spots {} -> covered {} -> uncovered {}; same placement: {} {}", spots, covered, uncovered, before == afterCover, before == afterUncover);
            if (covered != spots - 1 || uncovered != spots) throw new AssertionError("the spot did not come and go");
            if (before != afterCover || before != afterUncover) throw new AssertionError("a new placement was made instead of changing it in place");

            // The settings: the main screen and the new light levels screen, checked to fit.
            context.setScreen(() -> new com.skystormer.skysmapshapes.gui.ConfigScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("settings-main");
            context.setScreen(() -> new com.skystormer.skysmapshapes.gui.LightLevelsScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("settings-light-levels");
            String problem = context.computeOnClient(c -> {
                var screen = c.gui.screen();
                var widgets = screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.AbstractWidget)
                        .map(w -> (net.minecraft.client.gui.components.AbstractWidget) w).toList();
                for (var w : widgets) {
                    if (w.getX() < 0 || w.getRight() > screen.width) return w.getMessage().getString() + " runs off the side";
                    for (var o : widgets) {
                        if (o != w && w.getRectangle().intersects(o.getRectangle())) return w.getMessage().getString() + " overlaps " + o.getMessage().getString();
                    }
                }
                return null;
            });
            LOG.info("light levels screen layout: {}", problem == null ? "fits" : problem);
            if (problem != null) throw new AssertionError(problem);
            context.setScreen(() -> null);
            context.waitTicks(5);

            shot(context, "overlay-1-16-aqua-25pct", null, null, null);
            // Vanilla's chunk borders (F3+G) are depth-tested gizmo lines too: do they show here?
            context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_G);
            context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
            context.waitTicks(5);
            context.takeScreenshot("vanilla-chunk-borders");
            context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_G);
            context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_F3);
            shot(context, "overlay-1-16-green-40pct", Colours.GREEN.getArgb(), 0.40f, 1);
            shot(context, "overlay-1-16-white-15pct", Colours.WHITE.getArgb(), 0.15f, 1);
            shot(context, "overlay-half-aqua-25pct", Colours.AQUA.getArgb(), 0.25f, 8);
            shot(context, "overlay-off", Colours.AQUA.getArgb(), 0f, 1);
            LOG.info("ALL CHECKS PASSED");
        }
    }

    private static void shot(ClientGameTestContext context, String name, Integer colour, Float opacity, Integer height) {
        if (colour != null) {
            context.runOnClient(c -> {
                Config.INSTANCE.setSpawnProofColour(colour);
                Config.INSTANCE.setSpawnProofOpacity(opacity);
                Config.INSTANCE.setSpawnProofHeight(height);
                SpawnProofing.INSTANCE.refresh();
            });
            context.waitTicks(40);
        }
        context.takeScreenshot(name);
    }

    private static Object placement(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            try {
                var f = Class.forName("com.skystormer.skysmapshapes.LitematicaPlacement").getDeclaredField("placement");
                f.setAccessible(true);
                return f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    private static int fillBoxes(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            try {
                var f = SpawnProofing.class.getDeclaredField("fill");
                f.setAccessible(true);
                return ((java.util.List<?>) f.get(null)).size();
            } catch (ReflectiveOperationException e) {
                return -1;
            }
        });
    }

    /** How many spots the placement holds, read from spawn proofing itself. */
    private static int shownSpots(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            int n = 0;
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    if (SpawnProofing.coversLitematicaOverlay(new BlockPos(x, 100, z))) n++;
                }
            }
            return n;
        });
    }
}
