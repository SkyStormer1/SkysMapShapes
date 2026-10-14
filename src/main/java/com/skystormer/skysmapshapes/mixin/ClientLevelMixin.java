package com.skystormer.skysmapshapes.mixin;

import com.skystormer.skysmapshapes.SpawnProofing;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link SpawnProofing} when a block changes, so its outline is worked out again only when
 * a block on one of its spots changed, not on a timer.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void skysmapshapes$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        SpawnProofing.blockChanged(pos);
    }
}
