package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Loot table entry "tarnishedrelics:relic": drops one unidentified relic (or a legendary with its set), made the same
 * way as the relics the loot modifier adds, so a loot table can give a relic for sure instead of by chance.
 * <pre>
 * { "type": "tarnishedrelics:relic" }                                       any rarity, by the usual weights
 * { "type": "tarnishedrelics:relic",
 *   "rarities": ["tarnishedrelics:unique", "tarnishedrelics:legendary"] }  only these rarities (by their weights)
 * </pre>
 * It takes the usual entry fields too (weight, quality, conditions, functions). Rarities that don't exist are
 * skipped with one warning; when none of the listed rarities can make anything, the entry drops nothing.
 */
public class RelicEntry extends LootPoolSingletonContainer {

    // listed rarity ids already warned about as missing (once each, not every roll)
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    private final List<ResourceLocation> rarities;   // empty = any rarity

    RelicEntry(List<ResourceLocation> rarities, int weight, int quality, LootItemCondition[] conditions,
               LootItemFunction[] functions) {
        super(weight, quality, conditions, functions);
        this.rarities = List.copyOf(rarities);
    }

    @Override
    public LootPoolEntryType getType() {
        return TRItems.RELIC_ENTRY.get();
    }

    @Override
    protected void createItemStack(Consumer<ItemStack> out, LootContext context) {
        List<ItemStack> made;
        if (rarities.isEmpty()) {
            made = Roller.generateAll(context.getRandom(), null, null, null, RelicLootModifier.source(context), context);
        } else {
            made = Roller.generateAmong(context.getRandom(), resolve(), RelicLootModifier.source(context), context);
        }
        made.forEach(out);
    }

    // the listed rarities as loaded now (the datapack can change on /reload)
    private List<Rarity> resolve() {
        List<Rarity> out = new ArrayList<>();
        for (ResourceLocation id : rarities) {
            Rarity r = TRData.get().rarities().get(id);
            if (r != null) out.add(r);
            else if (WARNED.add(id)) TarnishedRelics.LOGGER.warn("Loot entry tarnishedrelics:relic: no rarity {}, skipped", id);
        }
        return out;
    }

    public static class Serializer extends LootPoolSingletonContainer.Serializer<RelicEntry> {
        @Override
        public void serializeCustom(JsonObject json, RelicEntry entry, JsonSerializationContext context) {
            super.serializeCustom(json, entry, context);
            if (!entry.rarities.isEmpty()) {
                JsonArray list = new JsonArray();
                entry.rarities.forEach(id -> list.add(id.toString()));
                json.add("rarities", list);
            }
        }

        @Override
        protected RelicEntry deserialize(JsonObject json, JsonDeserializationContext context, int weight, int quality,
                                         LootItemCondition[] conditions, LootItemFunction[] functions) {
            List<ResourceLocation> rarities = new ArrayList<>();
            if (json.has("rarities")) {
                for (var e : GsonHelper.getAsJsonArray(json, "rarities")) {
                    rarities.add(new ResourceLocation(GsonHelper.convertToString(e, "rarity")));
                }
            }
            return new RelicEntry(rarities, weight, quality, conditions, functions);
        }
    }
}
