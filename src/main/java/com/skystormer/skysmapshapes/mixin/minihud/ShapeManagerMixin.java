package com.skystormer.skysmapshapes.mixin.minihud;

import com.skystormer.skysmapshapes.MiniHudShapes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link MiniHudShapes} when a shape is added to or removed from MiniHUD's list, so the map
 * reads MiniHUD again on the next tick instead of on a timer. Only applied when MiniHUD is
 * installed (see {@link MiniHudMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.minihud.renderer.shapes.ShapeManager", remap = false)
public abstract class ShapeManagerMixin {

    @Inject(method = "addShape(Lfi/dy/masa/minihud/renderer/shapes/ShapeBase;)V", at = @At("RETURN"), require = 0)
    private void skysmapshapes$added(CallbackInfo ci) {
        MiniHudShapes.markDirty();
    }

    @Inject(method = "removeShape(Lfi/dy/masa/minihud/renderer/shapes/ShapeBase;)V", at = @At("RETURN"), require = 0)
    private void skysmapshapes$removed(CallbackInfo ci) {
        MiniHudShapes.markDirty();
    }
}
