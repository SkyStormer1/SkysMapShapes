package com.skystormer.skysmapshapes.mixin;

import com.skystormer.skysmapshapes.MapMenus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;

import java.util.ArrayList;

/**
 * Adds "Add shape here" to the menu of a waypoint right-clicked on Xaero's world map, centred on
 * that waypoint. Waypoints saved from BlueMap markers by Sky's Map Exposer are ordinary Xaero
 * waypoints, so they get it too.
 */
@Mixin(targets = "xaero.map.mods.gui.WaypointReader", remap = false)
public abstract class WaypointReaderMixin {

    @Inject(
            method = "getRightClickOptions(Lxaero/map/mods/gui/Waypoint;Lxaero/map/gui/IRightClickableElement;)Ljava/util/ArrayList;",
            at = @At("RETURN"),
            require = 0
    )
    private void skysmapshapes$addMenuOptions(Waypoint waypoint, IRightClickableElement target,
                                              CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        MapMenus.addWaypointOptions(cir.getReturnValue(), target, waypoint);
    }
}
