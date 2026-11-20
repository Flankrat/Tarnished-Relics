package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * How a relic is stored on an item. An identified relic has a "TarnishedRelics" compound:
 * <pre>
 *   Rarity     rarity id            Category  category id        Slot  slot its affixes work in
 *   Color      name color (int)     Prefix / Suffix  lang keys of the name parts (absent if none)
 *   UniqueName generated name (uniques only)
 *   Affixes    [{Id: affix id, Roll: 0-1}]
 * </pre>
 * Names and color are stored so the name shows correctly even without the datapack; the affixes' numbers come from
 * the current datapack using the stored roll.
 */
public final class RelicTag {

    public static final String KEY = "TarnishedRelics";
    /** Optional JSON text component another mod can set; shown before the affix prefix (after the quality word). */
    public static final String NAME_PREFIX = "TarnishedRelicsNamePrefix";

    private RelicTag() {
    }

    /** One rolled affix on an item. */
    public record Rolled(Affix affix, double roll) {
    }

    @Nullable
    public static CompoundTag get(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEY, Tag.TAG_COMPOUND) ? tag.getCompound(KEY) : null;
    }

    public static boolean isRelic(ItemStack stack) {
        return get(stack) != null;
    }

    @Nullable
    public static ResourceLocation rarity(CompoundTag relic) {
        return relic.contains("Rarity") ? ResourceLocation.tryParse(relic.getString("Rarity")) : null;
    }

    @Nullable
    public static EquipmentSlot slot(CompoundTag relic) {
        try {
            return relic.contains("Slot") ? EquipmentSlot.byName(relic.getString("Slot")) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The item's affixes that still exist in the current datapack (removed ones are skipped). */
    public static List<Rolled> affixes(CompoundTag relic) {
        List<Rolled> out = new ArrayList<>();
        ListTag list = relic.getList("Affixes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag a = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(a.getString("Id"));
            Affix affix = id == null ? null : TRData.get().affixes().get(id);
            if (affix != null) out.add(new Rolled(affix, a.getFloat("Roll")));
        }
        return out;
    }
}
