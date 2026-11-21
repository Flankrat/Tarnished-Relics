package com.flankrat.tarnishedrelics.data;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Modded items that break the usual rules: a tool with no tier, armor with no material, an item whose attribute
 * lookup throws. Made without their constructors (Unsafe), since registries are frozen and that's exactly how
 * the broken fields end up null. Category matching and item levels must survive them.
 */
class WeirdItemsTest {

    static {
        BuiltInData.files();   // bootstrap
    }

    /** An item that throws when asked for its attributes (some mods need a player or NBT there). */
    static class ThrowingItem extends Item {
        ThrowingItem() {
            super(new Properties());
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
            throw new IllegalStateException("needs a player");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (T) ((Unsafe) f.get(null)).allocateInstance(type);
    }

    @Test
    void swordWithoutATier() throws Exception {
        SwordItem sword = allocate(SwordItem.class);
        TRData data = BuiltInData.data();
        assertDoesNotThrow(() -> data.categoryOf(sword));
        assertDoesNotThrow(() -> data.levels().of(sword));
    }

    @Test
    void armorWithoutAMaterial() throws Exception {
        ArmorItem armor = allocate(ArmorItem.class);
        TRData data = BuiltInData.data();
        assertDoesNotThrow(() -> data.categoryOf(armor));
        assertDoesNotThrow(() -> data.levels().of(armor));
    }

    @Test
    void itemWhoseAttributesThrow() throws Exception {
        ThrowingItem item = allocate(ThrowingItem.class);
        TRData data = BuiltInData.data();
        assertDoesNotThrow(() -> data.categoryOf(item));
        assertDoesNotThrow(() -> data.levels().of(item));
    }
}
