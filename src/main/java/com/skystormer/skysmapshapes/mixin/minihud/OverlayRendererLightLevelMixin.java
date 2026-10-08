package com.skystormer.skysmapshapes.mixin.minihud;

import com.skystormer.skysmapshapes.LightLevels;
import com.skystormer.skysmapshapes.NetherSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps MiniHUD's light level overlay to inside your shapes, when the setting asks: just after
 * MiniHUD gathers the spots it will mark, {@link LightLevels#filter} takes out the ones outside.
 * MiniHUD's answer is whether there is anything to draw, so it is given again for what is left.
 * And in the Nether, which floors count as ones mobs can spawn on is {@link NetherSpawns#floor}'s.
 * Only applied when MiniHUD is installed (see {@link MiniHudMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.minihud.renderer.OverlayRendererLightLevel", remap = false)
public abstract class OverlayRendererLightLevelMixin {

    @Inject(
            method = "updateLightLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void skysmapshapes$keepInsideShapes(Level level, BlockPos centre, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && LightLevels.filter(this)) {
            cir.setReturnValue(LightLevels.anyLeft(this));
        }
    }

    @Redirect(
            method = "canSpawnAt(IIILnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/Level;Z)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;isValidSpawn(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntityType;)Z"
            ),
            require = 0
    )
    private boolean skysmapshapes$floor(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return NetherSpawns.floor(state, level, pos, type);
    }
}
