package com.flankrat.tarnishedrelics.api;

import com.flankrat.tarnishedrelics.data.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.eventbus.api.Event;

import javax.annotation.Nullable;

/**
 * Fired on the Forge event bus right after a relic is rolled, before it is wrapped as an unidentified item.
 * Other mods can change the real item here, e.g. give it sockets or other loot rolls they would normally add to
 * loot (they can't see it once it's wrapped). To add a word in front of the relic's name, set
 * {@link com.flankrat.tarnishedrelics.relic.RelicTag#NAME_PREFIX} on the stack to a JSON text component.
 */
public class RelicGeneratedEvent extends Event {

    /** Where the relic came from, so a mod can use its own odds for each (e.g. sockets on chest loot vs mob drops). */
    public enum Source {
        CHEST, ENTITY, FISHING, ARCHAEOLOGY, GIFT, BARTER,
        /** Loot from a table of some other type */
        OTHER_LOOT,
        /** /tarnishedrelics give */
        COMMAND
    }

    private final ItemStack stack;
    private final Rarity rarity;
    private final Source source;
    @Nullable private final LootContext context;

    public RelicGeneratedEvent(ItemStack stack, Rarity rarity, Source source, @Nullable LootContext context) {
        this.stack = stack;
        this.rarity = rarity;
        this.source = source;
        this.context = context;
    }

    /** Kept for mods built against 0.1.0: source COMMAND, no loot context. */
    public RelicGeneratedEvent(ItemStack stack, Rarity rarity) {
        this(stack, rarity, Source.COMMAND, null);
    }

    /** The real (hidden) item. Changes to it are kept. */
    public ItemStack getStack() {
        return stack;
    }

    public Rarity getRarity() {
        return rarity;
    }

    public Source getSource() {
        return source;
    }

    /** The loot table roll that made the relic (null for commands): its level, position, killed mob, player... */
    @Nullable
    public LootContext getLootContext() {
        return context;
    }
}
