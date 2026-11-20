package com.flankrat.tarnishedrelics.client;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/**
 * Client setup for the unidentified relic's icon: a darkened silhouette of its category.
 * <p>
 * The item model ({@code models/item/unidentified.json}) has one override per silhouette, picked by the
 * "tarnishedrelics:silhouette" property below (the silhouette's index in {@link #SILHOUETTES}). Each override model
 * draws a vanilla item texture, and the color handler tints it nearly black, so you see the shape but not the item.
 */
public final class TRClient {

    /** Built-in silhouettes; a category's "silhouette" must be one of these (unknown ones show "generic"). */
    public static final List<String> SILHOUETTES = List.of(
            "generic", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident",
            "helmet", "chestplate", "leggings", "boots");

    // tint multiplied over the texture: dark grey keeps a hint of the shading so it reads as a shape
    private static final int SILHOUETTE_TINT = 0x2E2E34;

    private TRClient() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(TRClient::setup);
        modBus.addListener(TRClient::colors);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(TRItems.UNIDENTIFIED.get(),
                new ResourceLocation(TarnishedRelics.MODID, "silhouette"), (stack, level, entity, seed) -> {
                    CompoundTag tag = stack.getTag();
                    int i = tag == null ? 0 : SILHOUETTES.indexOf(tag.getString("Silhouette"));
                    return Math.max(i, 0);
                }));
    }

    private static void colors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, layer) -> layer == 0 ? SILHOUETTE_TINT : -1, TRItems.UNIDENTIFIED.get());
    }
}
