package com.flankrat.tarnishedrelics.data;

import com.google.common.collect.Multimap;
import com.google.gson.JsonParseException;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.common.ToolActions;

import java.util.Locale;

/**
 * Item kinds a category can pick up automatically with {@code "auto": ["sword"]}, from any mod. Mods build their
 * weapons on vanilla's classes (Spartan Weaponry's and Better Survival's blades are swords, modded bows are bows...),
 * so checking the class finds them without listing every item. Only single, damageable items count, so stacks of
 * arrows or a totem never match.
 */
public enum AutoMatch {
    SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, SHIELD, HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    /** Anything else that hits harder than a fist (throwing weapons, odd modded weapons) */
    MELEE,
    /** Any other projectile weapon */
    RANGED;

    static AutoMatch parse(String s) {
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("unknown auto type \"" + s + "\"");
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean matches(Item item) {
        try {
            return check(item);
        } catch (RuntimeException e) {
            // a modded item that can't answer for an empty stack (no NBT, no player) is just not picked up,
            // instead of breaking category matching for every item
            return false;
        }
    }

    private boolean check(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.getMaxStackSize() != 1 || !stack.isDamageableItem()) return false;
        return switch (this) {
            case SWORD -> item instanceof SwordItem;
            case AXE -> item instanceof AxeItem;
            case PICKAXE -> item instanceof PickaxeItem;
            case SHOVEL -> item instanceof ShovelItem;
            case HOE -> item instanceof HoeItem;
            case BOW -> item instanceof BowItem;
            case CROSSBOW -> item instanceof CrossbowItem;
            case TRIDENT -> item instanceof TridentItem;
            // modded shields don't always extend ShieldItem, but they can all block
            case SHIELD -> item instanceof ShieldItem || stack.canPerformAction(ToolActions.SHIELD_BLOCK);
            case HELMET -> armor(item, ArmorItem.Type.HELMET);
            case CHESTPLATE -> armor(item, ArmorItem.Type.CHESTPLATE);
            case LEGGINGS -> armor(item, ArmorItem.Type.LEGGINGS);
            case BOOTS -> armor(item, ArmorItem.Type.BOOTS);
            case MELEE -> attackDamage(stack) > 0;
            case RANGED -> item instanceof ProjectileWeaponItem;
        };
    }

    // ArmorItem only: elytra and carved pumpkins are wearable too, but aren't armor
    private static boolean armor(Item item, ArmorItem.Type type) {
        return item instanceof ArmorItem a && a.getType() == type;
    }

    /** Attack damage the item adds in the main hand (0 for non-weapons). */
    static double attackDamage(ItemStack stack) {
        Multimap<Attribute, AttributeModifier> mods = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        double total = 0;
        for (AttributeModifier m : mods.get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) total += m.getAmount();
        }
        return total;
    }
}
