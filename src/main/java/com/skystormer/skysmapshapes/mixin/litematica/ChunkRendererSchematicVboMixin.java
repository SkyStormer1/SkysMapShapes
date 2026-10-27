package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.FluidModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
import fi.dy.masa.litematica.util.OverlayType;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import fi.dy.masa.malilib.util.position.LayerRange;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out Litematica's overlay box on spawn proofing's own spots, which get this mod's overlay
 * instead, in the colour, opacity and height you set, and on spots you have already covered, which
 * Litematica would mark as extra blocks (see {@link SpawnProofing#ownsLitematicaOverlay}); and,
 * when the setting hides them, Litematica's ghost blocks on the spots (see
 * {@link SpawnProofing#hidesLitematicaBlock}).
 * Your other placements keep Litematica's overlay.
 *
 * <p>Also the drawing half of the render layer bypass: spawn proofing's spots are drawn whatever
 * your render layers say, so the layers you set for building are left alone. Litematica skips a
 * chunk that misses the layers, and walks only the part of each placement box inside them; a
 * chunk or box holding a spot is built and walked whole, and then every block outside the layers
 * that is not a spot is left out again, one by one (see {@link SpawnProofing#litematicaBoxHasSpot}).
 *
 * <p>Only applied when Litematica is installed (see {@link com.skystormer.skysmapshapes.plugin.LitematicaMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo", remap = false)
public abstract class ChunkRendererSchematicVboMixin {

    private static final String REBUILD = "rebuildChunk(Lfi/dy/masa/litematica/render/schematic/ChunkRenderTaskSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherBuffers;)V";

    /**
     * The layers to filter the current box's blocks by, when it is walked whole for a spot; null
     * when Litematica's own clamping stands. Set before each box is walked, on the thread building
     * this chunk.
     */
    @Unique
    private LayerRange skysmapshapes$filterLayers;

    @Redirect(
            method = REBUILD,
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;intersectsBox(IIIIII)Z"),
            require = 0
    )
    private boolean skysmapshapes$buildChunkWithSpots(LayerRange range, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (range.intersectsBox(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        if (!SpawnProofing.litematicaBoxHasSpot(minX, minY, minZ, maxX, maxY, maxZ)) return false;
        SpawnProofing.layerBypassUsed("schematic chunks built");
        return true;
    }

    @Redirect(
            method = REBUILD,
            at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/position/LayerRange;getClampedRenderBoundingBox(Lfi/dy/masa/malilib/util/position/IntBoundingBox;)Lfi/dy/masa/malilib/util/position/IntBoundingBox;"),
            require = 0
    )
    private IntBoundingBox skysmapshapes$walkBoxWithSpots(LayerRange range, IntBoundingBox box) {
        IntBoundingBox clamped = range.getClampedRenderBoundingBox(box);
        if (!box.equals(clamped) && SpawnProofing.litematicaBoxHasSpot(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            skysmapshapes$filterLayers = range;
            return box;
        }
        skysmapshapes$filterLayers = null;
        return clamped;
    }

    @Inject(
            method = "renderOverlay(Lfi/dy/masa/litematica/util/OverlayType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZLfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkMeshDataSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherBuffers;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void skysmapshapes$ownOverlayOnSpawnProofing(OverlayType type, BlockPos pos, BlockState stateSchematic, boolean missing,
            ChunkRenderDataSchematic data, ChunkMeshDataSchematic mesh, ChunkRenderDispatcherBuffers buffers, CallbackInfo ci) {
        if (SpawnProofing.ownsLitematicaOverlay(pos, stateSchematic.isAir())) ci.cancel();
    }

    @Inject(
            method = "renderBlocksAndOverlay(Lfi/dy/masa/litematica/render/schematic/BlockModelRendererSchematic;Lfi/dy/masa/litematica/render/schematic/FluidModelRendererSchematic;Lnet/minecraft/core/BlockPos;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDataSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkMeshDataSchematic;Lfi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherBuffers;Lfi/dy/masa/litematica/render/schematic/IBlockOutputSchematic;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/chunk/VisGraph;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void skysmapshapes$hideGhostBlockOnSpawnProofing(BlockModelRendererSchematic blocks, FluidModelRendererSchematic fluids, BlockPos pos,
            ChunkRenderDataSchematic data, ChunkMeshDataSchematic mesh, ChunkRenderDispatcherBuffers buffers, IBlockOutputSchematic output,
            Vec3 offset, VisGraph visibility, CallbackInfo ci) {
        LayerRange layers = skysmapshapes$filterLayers;
        if (layers != null && !layers.isPositionWithinRange(pos)) {
            // Walked only because the box holds a spot: everything else outside your layers stays hidden.
            if (!SpawnProofing.coversLitematicaOverlay(pos)) {
                ci.cancel();
                return;
            }
            SpawnProofing.layerBypassUsed("schematic blocks drawn");
        }
        if (SpawnProofing.hidesLitematicaBlock(pos)) ci.cancel();
    }
}
