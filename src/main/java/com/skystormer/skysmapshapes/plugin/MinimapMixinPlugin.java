package com.skystormer.skysmapshapes.plugin;

/** Applies the Xaero's Minimap hooks only when Xaero's Minimap, or its Fair-Play build, is installed. */
public final class MinimapMixinPlugin extends ModLoadedPlugin {
    public MinimapMixinPlugin() {
        super("xaerominimap", "xaerominimapfair");
    }
}
