package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.TRConfig;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Wildcard;
import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Every time loot is generated of the kinds in [loot] lootTypes (chests only by default), there is a
 * [loot] chance (default 3%) that one unidentified relic is added to it.
 */
public class RelicLootModifier extends LootModifier {

    public static final Supplier<Codec<RelicLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, RelicLootModifier::new)));

    // loot table "types" (their parameter sets) by config name
    private static final Map<String, LootContextParamSet> TYPES = Map.of(
            "chest", LootContextParamSets.CHEST,
            "entity", LootContextParamSets.ENTITY,
            "fishing", LootContextParamSets.FISHING,
            "archaeology", LootContextParamSets.ARCHAEOLOGY,
            "gift", LootContextParamSets.GIFT,
            "barter", LootContextParamSets.PIGLIN_BARTER);

    public RelicLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (!TRConfig.ENABLED.get() || !eligible(context)) return loot;
        if (context.getRandom().nextDouble() >= TRConfig.CHANCE.get()) return loot;
        // usually one relic; a legendary set drops all its pieces
        loot.addAll(Roller.generateAll(context.getRandom(), null, null, null, source(context), context));
        return loot;
    }

    // what RelicGeneratedEvent listeners are told, by the loot table's kind
    private static final Map<LootContextParamSet, RelicGeneratedEvent.Source> SOURCES = Map.of(
            LootContextParamSets.CHEST, RelicGeneratedEvent.Source.CHEST,
            LootContextParamSets.ENTITY, RelicGeneratedEvent.Source.ENTITY,
            LootContextParamSets.FISHING, RelicGeneratedEvent.Source.FISHING,
            LootContextParamSets.ARCHAEOLOGY, RelicGeneratedEvent.Source.ARCHAEOLOGY,
            LootContextParamSets.GIFT, RelicGeneratedEvent.Source.GIFT,
            LootContextParamSets.PIGLIN_BARTER, RelicGeneratedEvent.Source.BARTER);

    private static RelicGeneratedEvent.Source source(LootContext context) {
        return SOURCES.getOrDefault(params(context), RelicGeneratedEvent.Source.OTHER_LOOT);
    }

    // the kind of loot table being rolled (chest, entity...)
    private static LootContextParamSet params(LootContext context) {
        return context.getLevel().getServer().getLootData().getLootTable(context.getQueriedLootTableId()).getParamSet();
    }

    private static boolean eligible(LootContext context) {
        ResourceLocation id = context.getQueriedLootTableId();
        if (TRConfig.EXCLUDE_TABLES.get().stream().anyMatch(p -> Wildcard.matches(p, id.toString()))) return false;
        LootContextParamSet params = params(context);
        boolean typeOk = TRConfig.LOOT_TYPES.get().stream().map(TYPES::get).anyMatch(t -> t == params);
        if (!typeOk) return false;
        // Mob drops only count when a player made the kill, so mob farms don't mass-produce relics
        if (params == LootContextParamSets.ENTITY && TRConfig.ENTITY_NEEDS_PLAYER.get()
                && !context.hasParam(LootContextParams.LAST_DAMAGE_PLAYER)) return false;
        return true;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
