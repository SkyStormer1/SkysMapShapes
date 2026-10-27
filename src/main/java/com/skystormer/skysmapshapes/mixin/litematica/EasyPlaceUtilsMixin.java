package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.util.position.LayerRange;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Easy Place stops you placing a block where a placement holds air, and within two blocks of one.
 * Spawn proofing's placement is all air apart from the spots, so it is left out of that check: Easy
 * Place still puts the slabs on the spots, and everything else places as if it were not there.
 *
 * <p>Easy Place also refuses to place outside your render layers. Spawn proofing's spots are let
 * through (the render layer bypass, with {@link RayTraceUtilsMixin}), so it works whatever layers
 * you build with.
 *
 * <p>Only applied when Litematica is installed (see {@link LitematicaMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils", remap = false)
public abstract class EasyPlaceUtilsMixin {

    @Redirect(
            method = "isPositionWithinRangeOfSchematicRegions",
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/placement/SchematicPlacementManager;getPlacementPartsInChunk(II)Ljava/util/List;"),
            require = 0
    )
    private static List<SchematicPlacementManager.PlacementPart> skysmapshapes$withoutSpawnProofing(SchematicPlacementManager manager, int cx, int cz) {
        List<SchematicPlacementManager.PlacementPart> parts = manager.getPlacementPartsInChunk(cx, cz);
        if (parts.stream().noneMatch(p -> SpawnProofing.isOwnPlacement(p.placement))) return parts;
        return parts.stream().filter(p -> !SpawnProofing.isOwnPlacement(p.placement)).toList();
    }

    @Redirect(
            method = {"placementRestrictionInEffect()Z", "placementRestrictionInEffect(Lnet/minecraft/client/Minecraft;)Z"},
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;isPositionWithinRange(Lnet/minecraft/core/BlockPos;)Z"),
            require = 0
    )
    private static boolean skysmapshapes$placeSpotsOutsideLayers(LayerRange range, BlockPos pos) {
        if (range.isPositionWithinRange(pos)) return true;
        if (!SpawnProofing.coversLitematicaOverlay(pos)) return false;
        SpawnProofing.layerBypassUsed("Easy Place allowed");
        return true;
    }
}
