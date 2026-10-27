package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.malilib.util.position.LayerRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Part of the render layer bypass: Litematica only draws the schematic chunks that meet your render
 * layers (with X or Z layers, whole chunks miss them). A chunk holding one of spawn proofing's spots
 * is drawn anyway; inside it, only the spots get past your layers (see
 * {@link ChunkRendererSchematicVboMixin}). Only applied when Litematica is installed (see
 * {@link LitematicaMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager", remap = false)
public abstract class SchematicPlacementManagerMixin {

    @Redirect(
            method = "getAndUpdateVisibleChunks(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;intersectsBox(IIIIII)Z"),
            require = 0
    )
    private boolean skysmapshapes$showChunksWithSpots(LayerRange range, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (range.intersectsBox(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        if (!SpawnProofing.litematicaBoxHasSpot(minX, minY, minZ, maxX, maxY, maxZ)) return false;
        SpawnProofing.layerBypassUsed("schematic chunks shown");
        return true;
    }
}
