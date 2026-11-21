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
        files.put(new ResourceLocation("trtest", "affixes/wise"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"xp_bonus\", \"value\": 1.0}]}"));
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

    // Another mod refused the crit (DENY): crit_chance still rolls, and a crit it rolls overrides the refusal. Without
    // a crit_chance crit the refusal stands (crit_damage doesn't make one). A denied hit that was a vanilla crit
    // counted as a crit already, so crit_chance never rolled and the hit didn't crit at all.
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void critChanceOverridesADeniedCrit(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        java.util.function.Consumer<CriticalHitEvent> otherMod = e -> {
            if (e.getEntity() == p) e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, false, CriticalHitEvent.class, otherMod);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:always_crits", 0.5));
            // getCriticalHit is null for "no crit"
            helper.assertTrue(ForgeHooks.getCriticalHit(p, z, false, 1.0F) != null, "100% crit chance didn't crit a denied hit");
            helper.assertTrue(ForgeHooks.getCriticalHit(p, z, true, 1.5F) != null, "100% crit chance didn't crit a denied vanilla crit");
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:harder_crits", 0.5));
            helper.assertTrue(ForgeHooks.getCriticalHit(p, z, true, 1.5F) == null, "crit_damage alone overrode a denied crit");
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(otherMod);
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

    // Bug (round 8): crit_chance and crit_damage were only checked by Forge's crit event, which only fires for melee
    // attacks, so on a bow, a crossbow or a thrown relic they did nothing (Hunter, and Executioner on a bow).
    // Projectile hits now roll crit_chance too: a crit is 1.5x damage, and crit_damage makes those crits hit harder.

    // a 10 damage hit by this projectile on the zombie, after relic effects
    private static float projectileHit(Zombie z, net.minecraft.world.damagesource.DamageSource source) {
        LivingHurtEvent hurt = new LivingHurtEvent(z, source, 10);
        MinecraftForge.EVENT_BUS.post(hurt);
        return hurt.getAmount();
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void arrowsCritWithCritChance(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            Arrow arrow = new Arrow(helper.getLevel(), p);
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.BOW, "rare", "bow", "mainhand", "trtest:always_crits", 0.5));
            float crit = projectileHit(z, p.damageSources().arrow(arrow, p));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.BOW, "rare", "bow", "mainhand",
                    "trtest:always_crits", 0.5, "trtest:harder_crits", 0.5));
            float harder = projectileHit(z, p.damageSources().arrow(arrow, p));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.BOW, "rare", "bow", "mainhand", "trtest:harder_crits", 0.5));
            float noCrit = projectileHit(z, p.damageSources().arrow(arrow, p));
            helper.assertTrue(Math.abs(crit - 15) < 1e-4, "an arrow crit from crit_chance is 1.5x: " + crit);
            helper.assertTrue(Math.abs(harder - 22.5F) < 1e-4, "+50% crit damage on that crit: " + harder);
            helper.assertTrue(noCrit == 10, "crit_damage alone made an arrow crit: " + noCrit);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void thrownRelicCritsWithItsCritChance(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            ThrownTrident t = thrown(helper, p, TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand", "trtest:always_crits", 0.5));
            float crit = projectileHit(z, p.damageSources().trident(t, p));
            // a held sword's crit_chance doesn't count for a thrown relic
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:always_crits", 0.5));
            ThrownTrident plain = thrown(helper, p, TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand"));
            float held = projectileHit(z, p.damageSources().trident(plain, p));
            helper.assertTrue(Math.abs(crit - 15) < 1e-4, "the thrown trident's crit_chance crit: " + crit);
            helper.assertTrue(held == 10, "the held sword's crit_chance counted for the thrown trident: " + held);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // melee crits stay with Forge's crit event: a melee hit isn't crit a second time here
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void meleeHitsArentCritTwice(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:always_crits", 0.5));
            float melee = projectileHit(z, p.damageSources().playerAttack(p));
            helper.assertTrue(melee == 10, "a melee hit got a second crit in the hurt event: " + melee);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Bug (round 8): only vanilla's player_attack / mob_attack counted as melee. Combat mods give melee hits their own
    // damage types (by weapon: slashing, blunt...), and then a sword's on-hit effects, on-hit fire, lifesteal and hit
    // functions did nothing. Melee hits are now the damage types in #tarnishedrelics:melee; minecraft:sting (a bee's
    // attack, also a melee hit) stands in here for a mod's type.
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void meleeHitWithAnotherMeleeDamageType(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:thrown_kit", 0.5));
            var sting = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.STING);
            net.minecraft.world.damagesource.DamageSource hit = new net.minecraft.world.damagesource.DamageSource(sting, p);
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(z, hit, 10));
            helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the sword's on-hit slowness didn't land on a melee hit of another type");
            p.setHealth(10);
            MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(z, hit, 4));
            helper.assertTrue(Math.abs(p.getHealth() - 12) < 1e-4, "the sword's lifesteal on that hit: health " + p.getHealth());
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

    // Bug (round 8): xp_bonus counted what the killer held and wore, not what made the kill (looting already did):
    // a thrown relic trident's xp_bonus did nothing, and a held sword's counted for a trident kill
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void xpBonusFollowsTheThrownRelic(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie thrownKill = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            ThrownTrident wise = thrown(helper, p, TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand", "trtest:wise", 0.5));
            thrownKill.hurt(p.damageSources().trident(wise, p), 1);   // the hit that "killed" it
            net.minecraftforge.event.entity.living.LivingExperienceDropEvent own = new net.minecraftforge.event.entity.living.LivingExperienceDropEvent(thrownKill, p, 10);
            MinecraftForge.EVENT_BUS.post(own);

            Zombie heldKill = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:wise", 0.5));
            ThrownTrident plain = thrown(helper, p, TestUtil.relic(Items.TRIDENT, "rare", "trident", "mainhand"));
            heldKill.hurt(p.damageSources().trident(plain, p), 1);
            net.minecraftforge.event.entity.living.LivingExperienceDropEvent held = new net.minecraftforge.event.entity.living.LivingExperienceDropEvent(heldKill, p, 10);
            MinecraftForge.EVENT_BUS.post(held);
            helper.assertTrue(own.getDroppedExperience() == 20, "the thrown trident's +100% xp: " + own.getDroppedExperience());
            helper.assertTrue(held.getDroppedExperience() == 10, "the held sword's xp bonus counted for a trident kill: " + held.getDroppedExperience());
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // arrows: the bow's damage bonus counts (it's in the hand), its on-hit effects don't (not melee)
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
