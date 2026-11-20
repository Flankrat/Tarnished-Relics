package com.flankrat.tarnishedrelics.name;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Optional support for Quality Equipment Forked (mod id quality_forked). It stores a quality number in
 * "tool_quality" and shows the word ("Fine", "Legendary") as its own tooltip line; Tarnished Relics also puts that
 * word at the start of the relic's name. Done with reflection so Quality isn't needed to build or run.
 */
public final class QualityCompat {

    private static final String MODID = "quality_forked";
    private static Boolean loaded;
    private static boolean failed;   // stop trying after the first error

    private QualityCompat() {
    }

    private static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded(MODID);
        return loaded && !failed;
    }

    /** "quality_equipment.quality.<name>" as a component, or null if the item has no quality (or Quality is absent). */
    @Nullable
    public static Component word(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains("tool_quality") || !loaded()) return null;
        int id = stack.getTag().getInt("tool_quality");
        String name = null;
        try {
            // The client has the qualities the server sent; the server (and single player) has its own loaded list
            if (FMLEnvironment.dist == Dist.CLIENT) name = nameFrom(Class.forName("com.kettle.qualityequipment.events.ClientEvents").getField("CLIENT_QUALITIES").get(null), id);
            if (name == null) name = nameFrom(Class.forName("com.kettle.qualityequipment.data.QualityLoader").getMethod("getQualities").invoke(null), id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't read Quality Equipment's qualities, names won't show the quality word", e);
            return null;
        }
        return name == null ? null : Component.translatable("quality_equipment.quality." + name);
    }

    @Nullable
    private static String nameFrom(Object map, int id) throws ReflectiveOperationException {
        if (!(map instanceof Map<?, ?> m)) return null;
        Object quality = m.get(id);
        return quality == null ? null : (String) quality.getClass().getMethod("getName").invoke(quality);
    }

    /**
     * Gives an identified relic a quality the way Quality does for items entering an inventory (it only does that in
     * Inventory.add, and identifying puts the item straight into the hand).
     */
    public static void rollQuality(ItemStack stack, RandomSource random) {
        if (!loaded() || (stack.getTag() != null && stack.getTag().contains("tool_quality"))) return;
        try {
            Method m = Class.forName("com.kettle.qualityequipment.data.QualityLoader").getMethod("getRandomQuality", ItemStack.class, RandomSource.class);
            m.invoke(null, stack, random);
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't give a relic its Quality Equipment quality", e);
        }
    }
}
