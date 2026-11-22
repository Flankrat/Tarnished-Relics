package com.flankrat.tarnishedrelics.effect;

import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * Legendary set bonuses: a hand-made legendary's "set_effects" work while the whole set (it and its companions) is
 * held and worn, each piece in its own slot. They work like the pieces' own effects (RelicEffects.active and gear add
 * them), except attributes, which can't come from one item: PassiveEffects puts those on the player once a second.
 * Values use the main piece's roll (the one with "set_effects").
 */
public final class SetBonuses {

    private SetBonuses() {
    }

    /** A complete set the entity has on: its legendary (with the set effects) and the main piece's stack. */
    public record Worn(Legendary legendary, ItemStack main) {
    }

    /** The legendary pieces the entity has in their own slots, by legendary id. */
    public static Map<ResourceLocation, ItemStack> pieces(LivingEntity entity) {
        Map<ResourceLocation, ItemStack> out = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            CompoundTag relic = RelicTag.get(stack);
            // only a piece in its own slot counts, like its own effects (a helmet in the hand isn't worn)
            if (relic == null || !relic.contains("Legendary") || RelicTag.slot(relic) != slot) continue;
            ResourceLocation id = ResourceLocation.tryParse(relic.getString("Legendary"));
            if (id != null) out.putIfAbsent(id, stack);
        }
        return out;
    }

    /** Every complete set among these pieces (a legendary with set effects, and all its companions). */
    public static List<Worn> complete(Map<ResourceLocation, ItemStack> pieces) {
        if (pieces.isEmpty()) return List.of();
        TRData data = TRData.get();
        List<Worn> out = new java.util.ArrayList<>();
        for (Map.Entry<ResourceLocation, ItemStack> p : pieces.entrySet()) {
            Legendary l = data.legendaries().get(p.getKey());
            if (l == null || l.setEffects().isEmpty() || !pieces.keySet().containsAll(l.companions())) continue;
            out.add(new Worn(l, p.getValue()));
        }
        return out;
    }

    /** The set effects (of one type, or all with type null) of the sets the entity has on, to "out". */
    static void collect(LivingEntity entity, @Nullable Effect.Type type, Consumer<RelicEffects.Active> out) {
        for (Worn w : complete(pieces(entity))) {
            ResourceLocation rarity = RelicTag.rarity(RelicTag.get(w.main()));
            double roll = roll(w.main());
            for (Effect e : w.legendary().setEffects()) {
                if (type == null || e.type == type) out.accept(new RelicEffects.Active(e, e.value.get(rarity, roll), w.main()));
            }
        }
    }

    /** A hand-made legendary's one roll for all its ranges: the roll of its hidden affix (its first). */
    public static double roll(ItemStack main) {
        CompoundTag relic = RelicTag.get(main);
        if (relic == null) return 0;
        ListTag affixes = relic.getList("Affixes", Tag.TAG_COMPOUND);
        return affixes.isEmpty() ? 0 : affixes.getCompound(0).getFloat("Roll");
    }
}
