package com.skystormer.skysmapshapes.mixin.litematica;

import com.skystormer.skysmapshapes.SpawnProofing;
import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.FluidModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
import fi.dy.masa.litematica.util.OverlayType;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out Litematica's overlay box on spawn proofing's own spots, which get this mod's overlay
 * instead, in the colour, opacity and height you set, and on spots you have already covered, which
 * Litematica would mark as extra blocks (see {@link SpawnProofing#ownsLitematicaOverlay}); and,
 * when the setting hides them, Litematica's ghost blocks on the spots (see
 * {@link SpawnProofing#hidesLitematicaBlock}).
 * Litematica's ghost blocks still show, your other placements keep Litematica's overlay, and Easy
 * Place works as before. Only applied when Litematica is installed (see {@link LitematicaMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo", remap = false)
public abstract class ChunkRendererSchematicVboMixin {

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
        if (SpawnProofing.hidesLitematicaBlock(pos)) ci.cancel();
    }
}
