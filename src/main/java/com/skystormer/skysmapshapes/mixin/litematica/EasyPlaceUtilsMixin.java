package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Easy Place stops you placing a block where a placement holds air, and within two blocks of one.
 * Spawn proofing's placement is all air apart from the spots, so it is left out of that check: Easy
 * Place still puts the slabs on the spots, and everything else places as if it were not there.
 * Only applied when Litematica is installed (see {@link LitematicaMixinPlugin}).
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
}
