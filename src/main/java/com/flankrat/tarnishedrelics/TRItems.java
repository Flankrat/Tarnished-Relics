package com.flankrat.tarnishedrelics;

import com.flankrat.tarnishedrelics.relic.RelicLootModifier;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The unidentified relic item and the loot modifier that adds it to loot. */
public final class TRItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TarnishedRelics.MODID);
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TarnishedRelics.MODID);

    // stacks to 1: every relic is different. Fire resistant (like netherite): a relic dropped in fire or lava isn't
    // destroyed, and floats on lava
    public static final RegistryObject<Item> UNIDENTIFIED =
            ITEMS.register("unidentified", () -> new UnidentifiedItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Codec<RelicLootModifier>> RELIC_LOOT =
            LOOT_MODIFIERS.register("relic_loot", RelicLootModifier.CODEC);

    private TRItems() {
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
    }
}
