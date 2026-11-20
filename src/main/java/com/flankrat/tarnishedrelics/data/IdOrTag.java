package com.flankrat.tarnishedrelics.data;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * One entry in a datapack list that can be an id ({@code "minecraft:zombie"}) or a tag ({@code "#minecraft:undead"}).
 * Works for any registry (entity types, items, damage types, biomes...).
 */
public record IdOrTag<T>(ResourceLocation id, boolean isTag, ResourceKey<? extends Registry<T>> registry) {

    public static <T> IdOrTag<T> parse(String text, ResourceKey<? extends Registry<T>> registry) {
        boolean tag = text.startsWith("#");
        return new IdOrTag<>(new ResourceLocation(tag ? text.substring(1) : text), tag, registry);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public TagKey<T> tag() {
        return TagKey.create((ResourceKey) registry, id);
    }

    /** True if the holder is this id, or is in this tag. */
    public boolean matches(Holder<T> holder) {
        if (isTag) return holder.is(tag());
        return holder.unwrapKey().map(k -> k.location().equals(id)).orElse(false);
    }

    @Override
    public String toString() {
        return (isTag ? "#" : "") + id;
    }
}
