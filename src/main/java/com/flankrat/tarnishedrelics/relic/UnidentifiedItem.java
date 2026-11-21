package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.name.NameFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import javax.annotation.Nullable;

/**
 * An unidentified relic. The real, already rolled item is hidden inside its NBT ("Hidden"), so this is just a plain
 * item: it can't be equipped, used as a weapon or tool, enchanted, repaired, socketed or reforged until a blacksmith
 * identifies it. Its icon is a darkened silhouette of its category (see the client setup).
 * <pre>
 *   Hidden     the real item      Category  category id        Silhouette  icon name
 *   Rarity     rarity id          Level     villager level needed   Cost  emeralds   Xp  villager XP   Color  rarity color
 * </pre>
 */
public class UnidentifiedItem extends Item {

    public UnidentifiedItem(Properties properties) {
        super(properties);
    }

    // "Unidentified Sword" from the category; plain white unless the server's config shows rarity colors
    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        String key = tag != null && tag.contains("NameKey") ? tag.getString("NameKey") : getDescriptionId();
        // a category made in a datapack/the editor may have no lang entry: "Unidentified Spear" from its id
        String category = tag != null ? tag.getString("Category") : "";
        MutableComponent name = Component.translatableWithFallback(key, NameFallback.unidentified(category));
        if (tag != null && tag.contains("Color") && DisplaySettings.showRarityColor()) {
            name = name.withStyle(s -> s.withColor(tag.getInt("Color")));
        }
        return name;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tarnishedrelics.tooltip.unidentified").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** The hidden real item (a copy), or EMPTY if this one has none. */
    public static ItemStack hidden(ItemStack wrapper) {
        CompoundTag tag = wrapper.getTag();
        return tag != null && tag.contains("Hidden") ? ItemStack.of(tag.getCompound("Hidden")) : ItemStack.EMPTY;
    }
}
