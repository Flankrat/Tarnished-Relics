package com.flankrat.tarnishedrelics.name;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Name parts in order (no lang file here, so translated parts show as their keys). */
class NameBuilderTest {

    static {
        BuiltInData.files();   // bootstrap
    }

    private static ItemStack relic(CompoundTag relic) {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        relic.putInt("Color", 0x5555FF);
        stack.getOrCreateTag().put(RelicTag.KEY, relic);
        return stack;
    }

    private static String name(ItemStack stack) {
        Component c = NameBuilder.build(stack, Component.literal("Iron Sword"));
        return c == null ? null : c.getString();
    }

    @Test
    void notARelicHasNoName() {
        assertNull(name(new ItemStack(Items.IRON_SWORD)));
    }

    @Test
    void prefixBaseSuffix() {
        CompoundTag r = new CompoundTag();
        r.putString("Prefix", "affix.p");
        r.putString("Suffix", "affix.s");
        assertEquals("affix.p Iron Sword affix.s", name(relic(r)));
    }

    @Test
    void uniqueNameReplacesTheSuffix() {
        CompoundTag r = new CompoundTag();
        r.putString("Prefix", "affix.p");
        r.putString("Suffix", "affix.s");
        r.putString("UniqueName", "Ithireal");
        assertEquals("affix.p Iron Sword tarnishedrelics.name.unique", name(relic(r)));
    }

    @Test
    void legendaryNameIsAlone() {
        CompoundTag r = new CompoundTag();
        r.putString("Prefix", "affix.p");
        r.putString("LegendaryName", "Widow's Kiss");
        assertEquals("Widow's Kiss", name(relic(r)));
    }

    @Test
    void otherModsPrefixGoesBeforeTheAffixPrefix() {
        CompoundTag r = new CompoundTag();
        r.putString("Prefix", "affix.p");
        ItemStack stack = relic(r);
        stack.getTag().putString(RelicTag.NAME_PREFIX, "{\"text\":\"Socketed\"}");
        assertEquals("Socketed affix.p Iron Sword", name(stack));
    }

    @Test
    void badPrefixFromAnotherModIsLeftOut() {
        CompoundTag r = new CompoundTag();
        ItemStack stack = relic(r);
        stack.getTag().putString(RelicTag.NAME_PREFIX, "{not json");
        assertEquals("Iron Sword", name(stack));
    }

    @Test
    void nameKeepsTheRarityColor() {
        Component c = NameBuilder.build(relic(new CompoundTag()), Component.literal("Iron Sword"));
        assertEquals(0x5555FF, c.getStyle().getColor().getValue());
    }
}
