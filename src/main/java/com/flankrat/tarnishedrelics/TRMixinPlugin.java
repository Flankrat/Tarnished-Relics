package com.flankrat.tarnishedrelics;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skips the compatibility mixins of mods that aren't installed. Their mixins are @Pseudo, so they never failed,
 * but Mixin still looked for the missing class and logged a WARN ("Error loading class ...") in every log without
 * that mod, which looks like a problem to anyone reading it. Mixin asks this before it looks the class up.
 */
public class TRMixinPlugin implements IMixinConfigPlugin {

    // mixin (from the mixin package) -> the mod id it needs
    private static final Map<String, String> NEEDS = Map.of("compat.TooltipOverhaulRatingMixin", "tooltipoverhaul");
    private static final String PACKAGE = "com.flankrat.tarnishedrelics.mixin.";

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String mod = NEEDS.get(mixinClassName.startsWith(PACKAGE) ? mixinClassName.substring(PACKAGE.length()) : mixinClassName);
        if (mod == null) return true;
        LoadingModList mods = LoadingModList.get();
        return mods == null || mods.getModFileById(mod) != null;   // can't tell: try it, as before
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
