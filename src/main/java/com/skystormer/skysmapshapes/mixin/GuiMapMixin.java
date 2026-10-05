package com.skystormer.skysmapshapes.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.skystormer.skysmapshapes.MapCamera;
import com.skystormer.skysmapshapes.MapMenus;
import com.skystormer.skysmapshapes.MenuTips;
import com.skystormer.skysmapshapes.ShapeDrawing;
import com.skystormer.skysmapshapes.ShapeHover;
import com.skystormer.skysmapshapes.gui.AddShapeWindow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.MapProcessor;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * What this mod adds to Xaero's world map screen.
 *
 * Drawing: straight after Xaero has drawn its terrain (the second flush of its terrain renderers,
 * the one without baked light), shapes go into Xaero's colour overlay buffer, which Xaero draws on
 * top of the terrain with its own highlights. Sky's Map Exposer hooks the same spot for its
 * terrain and outlines; both only add to the frame, so either can be installed without the other.
 *
 * Hover: the outline under the mouse is drawn thicker with a tooltip, and right-clicking it opens
 * that shape's menu instead of the map's.
 *
 * Right-click menu: "Add shape here", plus "Edit" for any shape under the click, at the end of the
 * menu Xaero shows when you right-click the map itself.
 *
 * Locals are taken by name, which Xaero's jar keeps. None of the injections is required, so an
 * unsupported Xaero version costs the shapes, not the game.
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class GuiMapMixin implements MapCamera {

    @Shadow private double cameraX;
    @Shadow private double cameraZ;
    @Shadow private MapProcessor mapProcessor;
    @Shadow private int rightClickX;
    @Shadow private int rightClickZ;
    @Shadow private ResourceKey<Level> rightClickDim;
    @Shadow private int mouseBlockPosX;
    @Shadow private int mouseBlockPosZ;
    @Shadow private HoveredMapElementHolder<?, ?> viewed;
    @Shadow private GuiRightClickMenu rightClickMenu;
    @Shadow private int[] cameraDestination;
    @Shadow private boolean shouldResetCameraPos;
    @Shadow private static boolean attachedCamera;

    /**
     * Moves the camera the way Xaero's own "hop to coordinates" does: detached from the player
     * (while attached, Xaero puts it back on the player every frame), then gliding there. A map
     * that has not been drawn yet would put its camera on the player on its first frame, so that
     * one starts on the spot instead.
     */
    @Override
    public void skysmapshapesCentreOn(int x, int z) {
        attachedCamera = false;
        if (shouldResetCameraPos) {
            shouldResetCameraPos = false;
            cameraX = x + 0.5;
            cameraZ = z + 0.5;
            cameraDestination = null;
        } else {
            cameraDestination = new int[]{x, z};
        }
    }

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;draw(Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void skysmapshapes$drawShapes(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci,
            @Local(name = "matrix") Matrix4f matrix,
            @Local(name = "flooredCameraX") int flooredCameraX,
            @Local(name = "flooredCameraZ") int flooredCameraZ
    ) {
        // Hover is worked out only while no menu is open, so the outline a menu is for stays lit.
        ShapeDrawing.drawWorldMap(mapProcessor, matrix, flooredCameraX, flooredCameraZ, cameraX, cameraZ,
                mouseBlockPosX, mouseBlockPosZ, rightClickMenu == null);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void skysmapshapes$drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (viewed == null && rightClickMenu == null) ShapeHover.INSTANCE.drawTooltip(graphics, mouseX, mouseY);
        // Hover text for the lines this mod adds to the menu, which Xaero's own options cannot carry.
        MenuTips.INSTANCE.draw(graphics, mouseX, mouseY, rightClickMenu);
    }

    /**
     * A right-click on the map itself (not on a waypoint or label, which Xaero handles first)
     * opens the hovered outlines' menu instead of the map's, when there are any.
     */
    @ModifyArg(
            method = "mapClicked",
            at = @At(value = "INVOKE", target = "Lxaero/map/gui/GuiMap;handleRightClick(Lxaero/map/gui/IRightClickableElement;II)V", ordinal = 1),
            index = 0,
            require = 0
    )
    private IRightClickableElement skysmapshapes$rightClickShape(IRightClickableElement target) {
        IRightClickableElement shapes = ShapeHover.rightClickTarget();
        return shapes != null ? shapes : target;
    }

    /** Letters typed while a box in the add shape window has the keyboard go to it, not to Xaero. */
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void skysmapshapes$typeIntoWindow(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (AddShapeWindow.charTyped(event)) cir.setReturnValue(true);
    }

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), require = 0)
    private void skysmapshapes$addMenuOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        MapMenus.addMapOptions(cir.getReturnValue(), (IRightClickableElement) this, (Screen) (Object) this,
                rightClickX, rightClickZ, rightClickDim);
    }
}
