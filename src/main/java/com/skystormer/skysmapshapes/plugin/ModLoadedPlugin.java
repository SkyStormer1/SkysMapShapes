package com.skystormer.skysmapshapes.plugin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies a mixin config's hooks only when the mod they hook into is installed (any of the ids given), so without it
 * nothing is even looked for, and Mixin has no missing classes to warn about. Kept outside the
 * mixin packages, which Mixin keeps to itself.
 */
public abstract class ModLoadedPlugin implements IMixinConfigPlugin {

    private final String[] modIds;

    protected ModLoadedPlugin(String... modIds) {
        this.modIds = modIds;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (String id : modIds) {
            if (FabricLoader.getInstance().isModLoaded(id)) return true;
        }
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
