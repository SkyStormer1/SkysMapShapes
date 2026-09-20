package com.skystormer.skysmapshapes.mixin.minimap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skystormer.skysmapshapes.ShapeDrawing;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.MinimapProcessor;
import xaero.hud.minimap.module.MinimapSession;
import xaero.lib.client.graphics.XaeroBufferProvider;

/**
 * Draws shapes on Xaero's minimap however it drew its terrain.
 *
 * The minimap has two ways of drawing: from Xaero's World Map data, which
 * {@code SupportXaeroWorldmapMixin} covers, and from the minimap's own records, which is what it
 * uses underground in cave mode. This injection sits where the two paths meet again, just before
 * Xaero draws its chunk grid, so shapes appear either way. Whichever of the two runs first each
 * frame does the drawing; see {@code ShapeDrawing.drawMinimap}.
 *
 * Not required: on an unsupported Xaero version this costs the shapes on the minimap, not the game.
 */
@Mixin(targets = "xaero.common.minimap.render.MinimapFBORenderer", remap = false)
public abstract class MinimapFBORendererMixin {

    @Inject(
            method = "renderChunksToFBO",
            at = @At(
                    value = "FIELD",
                    target = "Lxaero/hud/minimap/common/config/option/MinimapProfiledConfigOptions;CHUNK_GRID:Lxaero/lib/common/config/option/ConfigOption;",
                    opcode = org.objectweb.asm.Opcodes.GETSTATIC
            ),
            require = 0
    )
    private void skysmapshapes$drawShapes(
            MinimapSession session, PoseStack matrixStack, MinimapProcessor minimap, Vec3 renderPos,
            ResourceKey<Level> mapDimension, double zoom, int size, float sunBrightness, int level,
            boolean usingWorldMap, boolean rotate, int caveLayer, double playerX, double playerZ,
            boolean cave, XaeroBufferProvider buffers, CallbackInfo ci
    ) {
        ShapeDrawing.drawMinimapAnyMode(matrixStack, renderPos, mapDimension, buffers);
    }
}
