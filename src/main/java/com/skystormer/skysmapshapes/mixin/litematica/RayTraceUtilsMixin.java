package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.malilib.util.position.LayerRange;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The aiming half of the render layer bypass: Litematica's traces through the schematic world, which
 * Easy Place uses to find the block you look at (and pick block to find what to hold), pass over
 * every block outside your render layers. Spawn proofing's spots are let through, so Easy Place
 * finds them whatever layers you build with; every other block still follows your layers. With
 * {@link EasyPlaceUtilsMixin}, which lets Easy Place place there.
 * Only applied when Litematica is installed (see {@link LitematicaMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.RayTraceUtils", remap = false)
public abstract class RayTraceUtilsMixin {

    @Redirect(
            method = {
                    "traceFirstStep(Lfi/dy/masa/litematica/util/RayTraceUtils$RayTraceCalcsData;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;ZZZ)Lnet/minecraft/world/phys/BlockHitResult;",
                    "traceLoopSteps(Lfi/dy/masa/litematica/util/RayTraceUtils$RayTraceCalcsData;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;ZZZ)Z"
            },
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;isPositionWithinRange(III)Z"),
            require = 0
    )
    private static boolean skysmapshapes$traceSpotsOutsideLayers(LayerRange range, int x, int y, int z) {
        if (range.isPositionWithinRange(x, y, z)) return true;
        if (!SpawnProofing.coversLitematicaOverlay(x, y, z)) return false;
        SpawnProofing.layerBypassUsed("Easy Place aiming");
        return true;
    }

    @Redirect(
            method = {
                    "getFurthestSchematicWorldBlockBeforeVanilla(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;DZ)Lnet/minecraft/core/BlockPos;",
                    "getPickBlockLastTrace(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;DZ)Lnet/minecraft/core/BlockPos;"
            },
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;isPositionWithinRange(Lnet/minecraft/core/BlockPos;)Z"),
            require = 0
    )
    private static boolean skysmapshapes$findSpotsOutsideLayers(LayerRange range, BlockPos pos) {
        if (range.isPositionWithinRange(pos)) return true;
        if (!SpawnProofing.coversLitematicaOverlay(pos)) return false;
        SpawnProofing.layerBypassUsed("schematic block lookup");
        return true;
    }
}
