package com.skystormer.skysmapshapes.mixin.minimap;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skystormer.skysmapshapes.ShapeDrawing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.render.MinimapRendererHelper;
import xaero.hud.minimap.module.MinimapSession;
import xaero.map.MapProcessor;

/**
 * Draws shapes on Xaero's minimap, when it draws its terrain from Xaero's World Map (which it does
 * whenever both are installed): into the minimap's overlay buffer, which it draws over its terrain.
 *
 * In its own mixin config, marked not required, so that the mod still loads without Xaero's
 * Minimap. Locals are taken by name, which the minimap's jar keeps.
 */
@Mixin(targets = "xaero.common.mods.SupportXaeroWorldmap", remap = false)
public abstract class SupportXaeroWorldmapMixin {

    @Inject(
            method = "drawMinimap",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;draw(Lxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void skysmapshapes$drawShapes(
            MinimapSession minimapSession, PoseStack matrixStack, MinimapRendererHelper helper,
            int xFloored, int zFloored, int minViewX, int minViewZ, int maxViewX, int maxViewZ,
            boolean zooming, double zoom, double mapDimensionScale,
            VertexConsumer overlayBufferBuilder, MultiTextureRenderTypeRendererProvider multiTextureRenderTypeRenderers,
            CallbackInfo ci,
            @Local(name = "mapProcessor") MapProcessor mapProcessor
    ) {
        ShapeDrawing.drawMinimap(mapProcessor, matrixStack, xFloored, zFloored, minViewX, minViewZ, maxViewX, maxViewZ, overlayBufferBuilder);
    }
}
