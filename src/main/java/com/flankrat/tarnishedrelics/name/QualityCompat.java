package com.flankrat.tarnishedrelics.name;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Optional support for Quality Equipment Forked (mod id quality_forked). It stores a quality number in
 * "tool_quality" and shows the word ("Fine", "Legendary") as its own tooltip line; Tarnished Relics also puts that
 * word at the start of the relic's name. Done with reflection so Quality isn't needed to build or run.
 */
public final class QualityCompat {

    private static final String MODID = "quality_forked";
    private static Boolean loaded;
    private static boolean failed;   // stop trying after the first error

    // Quality's classes and members, looked up once (the name is built every time an item's name is shown, and
    // looking them up each time was most of its cost)
    // (client and integrated server threads can both get here: getQualities is volatile and set last, and a
    // quality's two methods are kept as one object, so a thread never sees half of a lookup)
    private static Field clientQualities;              // ClientEvents.CLIENT_QUALITIES (client only)
    private static Method getRandomQuality;            // QualityLoader.getRandomQuality(ItemStack, RandomSource)
    private static volatile Method getQualities;       // QualityLoader.getQualities()
    private static volatile QualityMethods qualityMethods;

    /** A quality object's getAttributes() and getName(), for its class. */
    private record QualityMethods(Class<?> type, Method attributes, Method name) {
    }

    private QualityCompat() {
    }

    private static boolean loaded() {
        if (loaded == null) loaded = ModList.get() != null && ModList.get().isLoaded(MODID);
        return loaded && !failed;
    }

    // finds Quality's members the first time they're needed
    private static void lookUp() throws ReflectiveOperationException {
        if (getQualities != null) return;
        Class<?> loader = Class.forName("com.kettle.qualityequipment.data.QualityLoader");
        if (FMLEnvironment.dist == Dist.CLIENT) clientQualities = Class.forName("com.kettle.qualityequipment.events.ClientEvents").getField("CLIENT_QUALITIES");
        getRandomQuality = loader.getMethod("getRandomQuality", ItemStack.class, RandomSource.class);
        getQualities = loader.getMethod("getQualities");
    }

    /**
     * The quality's word ("Thick"), or null if the item has no quality, its quality has no word to show, or Quality is
     * absent. Quality's own tooltip does the same: the quality's name is already its lang key
     * ("quality_equipment.quality.thick"), and a quality without attributes ("normal") shows nothing.
     */
    @Nullable
    public static Component word(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().contains("tool_quality") || !loaded()) return null;
        int id = stack.getTag().getInt("tool_quality");
        String name = null;
        try {
            lookUp();
            // The client has the qualities the server sent; the server (and single player) has its own loaded list
            if (clientQualities != null) name = nameFrom(clientQualities.get(null), id);
            if (name == null) name = nameFrom(getQualities.invoke(null), id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            failed = true;
            TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't read Quality Equipment's qualities, names won't show the quality word", e);
            return null;
        }
        if (name == null || name.isEmpty()) return null;
        // the name is the lang key; a bare name gets Quality's prefix
        String key = name.contains(".") ? name : "quality_equipment.quality." + name;
        Language lang = Language.getInstance();
        // no translation: leave the word out instead of showing the raw key
        if (!lang.has(key)) return null;
        // Quality's words carry their own color codes (§9Thick); the name keeps the relic's color instead
        String word = ChatFormatting.stripFormatting(lang.getOrDefault(key));
        return word == null || word.isBlank() ? null : Component.literal(word.trim());
    }

    // the quality's name, or null if it has no attributes (Quality shows nothing for those, e.g. "normal")
    @Nullable
    private static String nameFrom(Object map, int id) throws ReflectiveOperationException {
        if (!(map instanceof Map<?, ?> m)) return null;
        Object quality = m.get(id);
        if (quality == null) return null;
        QualityMethods q = qualityMethods;
        if (q == null || q.type != quality.getClass()) {
            q = new QualityMethods(quality.getClass(), quality.getClass().getMethod("getAttributes"), quality.getClass().getMethod("getName"));
            qualityMethods = q;
        }
        Object attributes = q.attributes.invoke(quality);
        if (attributes instanceof Collection<?> c && c.isEmpty()) return null;
        return (String) q.name.invoke(quality);
    }

    /**
     * Gives an identified relic a quality the way Quality does for items entering an inventory (it only does that in
     * Inventory.add, and identifying puts the item straight into the hand).
     */
    public static void rollQuality(ItemStack stack, RandomSource random) {
        if (!loaded() || (stack.getTag() != null && stack.getTag().contains("tool_quality"))) return;
        try {
            lookUp();
            getRandomQuality.invoke(null, stack, random);
        } catch (ReflectiveOperationException | LinkageError e) {
            failed = true;
            TarnishedRelics.LOGGER.warn("[Tarnished Relics] couldn't give a relic its Quality Equipment quality", e);
        }
    }
}
