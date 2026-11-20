package com.flankrat.tarnishedrelics.api;

import com.flankrat.tarnishedrelics.data.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus right after a relic is rolled, before it is wrapped as an unidentified item.
 * Other mods can change the real item here, e.g. give it sockets or other loot rolls they would normally add to
 * loot (they can't see it once it's wrapped). To add a word in front of the relic's name, set
 * {@link com.flankrat.tarnishedrelics.relic.RelicTag#NAME_PREFIX} on the stack to a JSON text component.
 */
public class RelicGeneratedEvent extends Event {

    private final ItemStack stack;
    private final Rarity rarity;

    public RelicGeneratedEvent(ItemStack stack, Rarity rarity) {
        this.stack = stack;
        this.rarity = rarity;
    }

    /** The real (hidden) item. Changes to it are kept. */
    public ItemStack getStack() {
        return stack;
    }

    public Rarity getRarity() {
        return rarity;
    }
}
