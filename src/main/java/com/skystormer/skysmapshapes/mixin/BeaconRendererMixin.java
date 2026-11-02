package com.skystormer.skysmapshapes.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skystormer.skysmapshapes.Config;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out the game's own beacon beams when {@link Config#hideGameBeams} is on. The beacon block
 * itself is drawn with the other blocks; this renderer only draws its beam.
 */
@Mixin(BeaconRenderer.class)
public abstract class BeaconRendererMixin {

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BeaconRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"), cancellable = true)
    private void skysmapshapes$hideBeam(BeaconRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (Config.INSTANCE.getEnabled() && Config.INSTANCE.getHideGameBeams()) ci.cancel();
    }
}
