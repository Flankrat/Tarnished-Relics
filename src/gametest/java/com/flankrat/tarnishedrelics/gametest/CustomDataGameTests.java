package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests that need affixes the built-in data doesn't have. The batch loads the built-in data plus these files and
 * puts the original back afterwards (other batches never run at the same time).
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class CustomDataGameTests {

    private static final String BATCH = "custom_data";
    private static Map<ResourceLocation, JsonElement> original;

    @BeforeBatch(batch = BATCH)
    public static void load(ServerLevel level) {
        original = TestUtil.currentFiles();
        Map<ResourceLocation, JsonElement> files = TestUtil.currentFiles();
        files.put(new ResourceLocation("trtest", "affixes/break_sometimes"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"break_speed\", \"value\": 1.0, \"conditions\": {\"chance\": 0.5}}]}"));
        files.put(new ResourceLocation("trtest", "affixes/always_crits"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"crit_chance\", \"value\": 1.0}]}"));
        files.put(new ResourceLocation("trtest", "affixes/harder_crits"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"crit_damage\", \"value\": 0.5}]}"));
        files.put(new ResourceLocation("trtest", "affixes/thrown_kit"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"damage_vs\", \"value\": 0.5},"
                        + " {\"type\": \"on_hit_effect\", \"effect\": \"minecraft:slowness\", \"duration\": 100},"
                        + " {\"type\": \"lifesteal\", \"value\": 0.5}, {\"type\": \"looting\", \"value\": 2}]}"));
        TRData.load(files);
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        TRData.load(original);
    }

    // Bug (round 2): break speed with a "chance" was rolled on every call. The client and the server each work out
    // the mining speed, got different answers, and the block cracked and reset (or broke at a different time).
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void breakSpeedIsTheSameEveryTime(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND,
                    TestUtil.relic(Items.IRON_PICKAXE, "magic", "pickaxe", "mainhand", "trtest:break_sometimes", 0.5));
            BlockPos pos = TestUtil.abs(helper, 1, 0, 1);
            Set<Float> speeds = new HashSet<>();
            for (int i = 0; i < 40; i++) {
                PlayerEvent.BreakSpeed e = new PlayerEvent.BreakSpeed(p, Blocks.STONE.defaultBlockState(), 1.0F, pos);
                MinecraftForge.EVENT_BUS.post(e);
                speeds.add(e.getNewSpeed());
            }
            helper.assertTrue(speeds.size() == 1, "break speeds differ between calls: " + speeds);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // the multiplier Forge's crit check gives a hit (what Player.attack multiplies the damage by), or 1 if no crit
    private static float critMultiplier(ServerPlayer p, Zombie target, boolean vanillaCrit) {
        CriticalHitEvent e = ForgeHooks.getCriticalHit(p, target, vanillaCrit, vanillaCrit ? 1.5F : 1.0F);
        return e == null ? 1.0F : e.getDamageModifier();
    }

    // Bug (round 6): a crit made by crit_chance kept Forge's starting multiplier for a hit that isn't a vanilla crit
    // (1.0), so it showed the crit particles but did no extra damage
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void critChanceCritsHitHarder(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:always_crits", 0.5));
            float m = critMultiplier(p, z, false);
            helper.assertTrue(m >= 1.5F, "a crit_chance crit multiplies damage by " + m + ", a vanilla crit by 1.5");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // crit_damage on a vanilla crit, and nothing at all on a hit that isn't one
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void critDamageOnlyOnCrits(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:harder_crits", 0.5));
            float vanilla = critMultiplier(p, z, true);
            helper.assertTrue(Math.abs(vanilla - 2.25F) < 1e-4, "vanilla crit with +50% crit damage: " + vanilla);
            helper.assertTrue(critMultiplier(p, z, false) == 1.0F, "crit_damage made a plain hit a crit");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // ---- thrown relic weapons hit with their own effects ----

    private static ItemStack kitTrident() {
        return TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand", "trtest:thrown_kit", 0.5);
    }

    private static ThrownTrident thrown(GameTestHelper helper, ServerPlayer p, ItemStack trident) {
        return new ThrownTrident(helper.getLevel(), p, trident);
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void thrownTridentHitsWithItsEffects(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            ThrownTrident t = thrown(helper, p, kitTrident());   // the hands are empty: it was thrown
            LivingHurtEvent hurt = new LivingHurtEvent(z, p.damageSources().trident(t, p), 10);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(Math.abs(hurt.getAmount() - 15) < 1e-4, "thrown trident's +50% damage: " + hurt.getAmount());
            helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "thrown trident's on-hit slowness didn't land");
            p.setHealth(10);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(z, p.damageSources().trident(t, p), 4));
            helper.assertTrue(Math.abs(p.getHealth() - 12) < 1e-4, "thrown trident's 50% lifesteal: health " + p.getHealth());
            LootingLevelEvent loot = new LootingLevelEvent(z, p.damageSources().trident(t, p), 0);
            MinecraftForge.EVENT_BUS.post(loot);
            helper.assertTrue(loot.getLootingLevel() == 2, "thrown trident's +2 looting: " + loot.getLootingLevel());
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // what's in the hands when a relic is thrown didn't make that hit: its effects don't count
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void handsDontCountForAThrownRelic(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:thrown_kit", 0.5));
            ThrownTrident t = thrown(helper, p, TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand"));   // a relic without affixes
            LivingHurtEvent hurt = new LivingHurtEvent(z, p.damageSources().trident(t, p), 10);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(Math.abs(hurt.getAmount() - 10) < 1e-4, "the held sword's bonus counted for the thrown trident: " + hurt.getAmount());
            helper.assertFalse(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the held sword's on-hit effect counted for the thrown trident");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // arrows stay as they were: the bow's damage bonus counts (it's in the hand), its on-hit effects don't (not melee)
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void arrowsUnchanged(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.BOW, "rare", "bow", "mainhand", "trtest:thrown_kit", 0.5));
            Arrow arrow = new Arrow(helper.getLevel(), p);
            LivingHurtEvent hurt = new LivingHurtEvent(z, p.damageSources().arrow(arrow, p), 10);
            MinecraftForge.EVENT_BUS.post(hurt);
            helper.assertTrue(Math.abs(hurt.getAmount() - 15) < 1e-4, "the bow's +50% damage on its arrow: " + hurt.getAmount());
            helper.assertFalse(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "an arrow set off the bow's on-hit effect");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
