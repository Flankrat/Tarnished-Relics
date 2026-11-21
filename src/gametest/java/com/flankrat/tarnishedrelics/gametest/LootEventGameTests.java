package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TRConfig;
import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.editor.ConfigFile;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * RelicGeneratedEvent from real loot tables of every kind: it fires with the right source, on the real item before
 * it is hidden in the unidentified wrapper, and what a listener changes is kept in the relic. The batch turns relic
 * loot on for every kind at 100% and puts the config back afterwards.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class LootEventGameTests {

    private static final String BATCH = "loot_events";
    private static final String MARK = "TRTestMark";

    private record Seen(RelicGeneratedEvent.Source source, boolean wrapped, boolean relic) {
    }

    private static final List<Seen> SEEN = new ArrayList<>();
    private static boolean listening;
    private static JsonObject original;

    static {
        // a listener like another mod's: records the event and marks the item
        MinecraftForge.EVENT_BUS.addListener((RelicGeneratedEvent e) -> {
            if (!listening) return;
            SEEN.add(new Seen(e.getSource(), e.getStack().is(TRItems.UNIDENTIFIED.get()), RelicTag.isRelic(e.getStack())));
            e.getStack().getOrCreateTag().putBoolean(MARK, true);
        });
    }

    private static final List<String> ALL = List.of("chest", "entity", "fishing", "archaeology", "gift", "barter");

    // saved the way the editor's Settings tab saves (all values in one write, live at once)
    @BeforeBatch(batch = BATCH)
    public static void on(ServerLevel level) {
        original = ConfigFile.read();
        JsonObject test = original.deepCopy();
        test.addProperty("chance", 1.0);
        JsonArray types = new JsonArray();
        ALL.forEach(types::add);
        test.add("lootTypes", types);
        ConfigFile.write(test);
        listening = true;
    }

    private static boolean configured() {
        return TRConfig.CHANCE.get() == 1.0 && TRConfig.LOOT_TYPES.get().equals(ALL);
    }

    @AfterBatch(batch = BATCH)
    public static void off(ServerLevel level) {
        listening = false;
        ConfigFile.write(original);
    }

    /** Rolls a table and checks the event and the relic it added. */
    private static void check(GameTestHelper helper, ResourceLocation table, LootParams params, RelicGeneratedEvent.Source source) {
        helper.succeedWhen(() -> {
            helper.assertTrue(configured(), "the test config isn't set: " + TRConfig.LOOT_TYPES.get());
            synchronized (SEEN) {
                SEEN.clear();
                List<ItemStack> loot = helper.getLevel().getServer().getLootData().getLootTable(table).getRandomItems(params);
                helper.assertTrue(SEEN.stream().anyMatch(s -> s.source == source), table + ": no event with source " + source + ", saw " + SEEN);
                helper.assertTrue(SEEN.stream().noneMatch(Seen::wrapped), table + ": the event got the wrapper, not the real item");
                helper.assertTrue(SEEN.stream().allMatch(Seen::relic), table + ": the event's item has no relic data yet");
                ItemStack wrapper = loot.stream().filter(s -> s.is(TRItems.UNIDENTIFIED.get())).findFirst().orElse(null);
                helper.assertTrue(wrapper != null, table + ": no relic in the loot " + loot);
                helper.assertTrue(wrapper.getTag().getCompound("Hidden").getCompound("tag").getBoolean(MARK), table + ": the listener's change was lost");
            }
        });
    }

    private static LootParams.Builder at(GameTestHelper helper) {
        return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 1, 2)));
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void chest(GameTestHelper helper) {
        check(helper, BuiltInLootTables.SIMPLE_DUNGEON, at(helper).create(LootContextParamSets.CHEST), RelicGeneratedEvent.Source.CHEST);
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void entity(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        LootParams params = at(helper).withParameter(LootContextParams.THIS_ENTITY, z)
                .withParameter(LootContextParams.DAMAGE_SOURCE, p.damageSources().playerAttack(p))
                .withParameter(LootContextParams.KILLER_ENTITY, p).withParameter(LootContextParams.LAST_DAMAGE_PLAYER, p)
                .create(LootContextParamSets.ENTITY);
        check(helper, EntityType.ZOMBIE.getDefaultLootTable(), params, RelicGeneratedEvent.Source.ENTITY);
        helper.runAfterDelay(90, () -> TestUtil.remove(p));   // after the check (the test times out at 100 ticks)
    }

    // a mob killed without a player gets nothing (entityNeedsPlayerKill), so mob farms don't make relics
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void entityWithoutAPlayerGetsNothing(GameTestHelper helper) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        LootParams params = at(helper).withParameter(LootContextParams.THIS_ENTITY, z)
                .withParameter(LootContextParams.DAMAGE_SOURCE, z.damageSources().generic()).create(LootContextParamSets.ENTITY);
        helper.succeedWhen(() -> {
            helper.assertTrue(configured(), "the test config isn't set: " + TRConfig.LOOT_TYPES.get());
            List<ItemStack> loot = helper.getLevel().getServer().getLootData().getLootTable(EntityType.ZOMBIE.getDefaultLootTable()).getRandomItems(params);
            helper.assertTrue(loot.stream().noneMatch(s -> s.is(TRItems.UNIDENTIFIED.get())), "a relic from a kill without a player");
        });
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void fishing(GameTestHelper helper) {
        check(helper, BuiltInLootTables.FISHING, at(helper).withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .create(LootContextParamSets.FISHING), RelicGeneratedEvent.Source.FISHING);
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void archaeology(GameTestHelper helper) {
        check(helper, BuiltInLootTables.DESERT_PYRAMID_ARCHAEOLOGY, at(helper).create(LootContextParamSets.ARCHAEOLOGY), RelicGeneratedEvent.Source.ARCHAEOLOGY);
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void gift(GameTestHelper helper) {
        Cat cat = helper.spawn(EntityType.CAT, new BlockPos(2, 1, 2));
        check(helper, BuiltInLootTables.CAT_MORNING_GIFT, at(helper).withParameter(LootContextParams.THIS_ENTITY, cat)
                .create(LootContextParamSets.GIFT), RelicGeneratedEvent.Source.GIFT);
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void barter(GameTestHelper helper) {
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(2, 1, 2));
        check(helper, BuiltInLootTables.PIGLIN_BARTERING, new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, piglin).create(LootContextParamSets.PIGLIN_BARTER), RelicGeneratedEvent.Source.BARTER);
    }

    // the default config: chests only
    @GameTest(template = "gametest/empty")
    public static void defaultIsChestsOnly(GameTestHelper helper) {
        helper.assertTrue(TRConfig.LOOT_TYPES.getDefault().equals(List.of("chest")), "default loot types " + TRConfig.LOOT_TYPES.getDefault());
        helper.succeed();
    }
}
