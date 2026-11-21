package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Conditions;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The built-in legendary affixes for tools, shields, bows and armor that only work some of the time (below y 0, in
 * the Nether, at night, below 30% health...) or only against some damage. Each test checks the effect is there when
 * its condition holds and gone when it doesn't, using the real built-in affix files.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class LegendaryAffixGameTests {

    // a legendary relic with one built-in legendary affix, rolled in the middle of its range
    private static ItemStack legendary(net.minecraft.world.item.Item item, String category, String slot, String affix) {
        return TestUtil.relic(item, "legendary", category, slot, "tarnishedrelics:legendary_" + affix, 0.5);
    }

    // the mining speed the player gets on this block (1.0 before relic effects)
    private static float breakSpeed(ServerPlayer p, BlockState state, GameTestHelper helper) {
        PlayerEvent.BreakSpeed e = new PlayerEvent.BreakSpeed(p, state, 1.0F, TestUtil.abs(helper, 1, 0, 1));
        MinecraftForge.EVENT_BUS.post(e);
        return e.getNewSpeed();
    }

    // the damage left of a 10 damage hit on this entity after relic effects
    private static float hurt(LivingEntity victim, DamageSource source) {
        LivingHurtEvent e = new LivingHurtEvent(victim, source, 10);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getAmount();
    }

    // sets the time of day; is_night / is_day read the sky darkness, which the level only updates on its next tick
    private static void setTime(GameTestHelper helper, long time) {
        helper.getLevel().setDayTime(time);
        helper.getLevel().updateSkyBrightness();
    }

    // runs the passive effect check (it runs on whole seconds)
    private static void passiveTick(ServerPlayer p) {
        p.tickCount = 20 * (p.tickCount / 20 + 1);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    @GameTest(template = "gametest/empty")
    public static void deepcoreOnlyBelowZero(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.IRON_PICKAXE, "pickaxe", "mainhand", "deepcore"));
            p.setPos(p.getX(), 10, p.getZ());
            float above = breakSpeed(p, Blocks.STONE.defaultBlockState(), helper);
            passiveTick(p);
            boolean visionAbove = p.hasEffect(MobEffects.NIGHT_VISION);
            p.setPos(p.getX(), -10, p.getZ());
            float below = breakSpeed(p, Blocks.STONE.defaultBlockState(), helper);
            passiveTick(p);
            helper.assertTrue(above == 1.0F, "no mining bonus above y 0: " + above);
            helper.assertTrue(below == 2.0F, "+100% mining below y 0 (middle roll): " + below);
            helper.assertTrue(!visionAbove && p.hasEffect(MobEffects.NIGHT_VISION), "night vision only below y 0");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void stonecutterOnlyOnStone(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.IRON_PICKAXE, "pickaxe", "mainhand", "stonecutter"));
            float stone = breakSpeed(p, Blocks.STONE.defaultBlockState(), helper);
            float ore = breakSpeed(p, Blocks.IRON_ORE.defaultBlockState(), helper);
            helper.assertTrue(stone == 2.25F, "+125% on stone (middle roll): " + stone);
            helper.assertTrue(ore == 1.0F, "nothing on ore: " + ore);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void delversResolveOnlyExplosionsAndFallingBlocks(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.IRON_PICKAXE, "pickaxe", "mainhand", "delvers_resolve"));
            float blast = hurt(p, p.damageSources().explosion(null, null));
            float suffocation = hurt(p, p.damageSources().inWall());
            float generic = hurt(p, p.damageSources().generic());
            helper.assertTrue(Math.abs(blast - 6.0F) < 0.01F && Math.abs(suffocation - 6.0F) < 0.01F,
                    "-40% from explosions and suffocation (middle roll): " + blast + ", " + suffocation);
            helper.assertTrue(generic == 10.0F, "nothing from other damage: " + generic);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void hellforgedOnlyInTheNether(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.IRON_SHOVEL, "shovel", "mainhand", "hellforged"));
            float overworld = breakSpeed(p, Blocks.DIRT.defaultBlockState(), helper);
            passiveTick(p);
            helper.assertTrue(overworld == 1.0F, "no mining bonus in the overworld: " + overworld);
            helper.assertTrue(!p.hasEffect(MobEffects.FIRE_RESISTANCE), "no fire resistance in the overworld");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void rampartOnlyProjectiles(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.OFF_HAND, legendary(Items.SHIELD, "shield", "offhand", "rampart"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            Arrow arrow = new Arrow(helper.getLevel(), z);
            float shot = hurt(p, p.damageSources().arrow(arrow, z));
            helper.assertTrue(Math.abs(shot - 6.5F) < 0.01F, "-35% from arrows (middle roll): " + shot);
            helper.assertTrue(hurt(p, p.damageSources().generic()) == 10.0F, "nothing from other damage");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void lastStandOnlyBelowThirtyPercent(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.OFF_HAND, legendary(Items.SHIELD, "shield", "offhand", "last_stand"));
            p.setHealth(p.getMaxHealth());
            float full = hurt(p, p.damageSources().generic());
            p.setHealth(p.getMaxHealth() * 0.2F);
            float low = hurt(p, p.damageSources().generic());
            helper.assertTrue(full == 10.0F, "nothing at full health: " + full);
            helper.assertTrue(Math.abs(low - 6.5F) < 0.01F, "-35% below 30% health (middle roll): " + low);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void retributionSlowsTheAttacker(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.OFF_HAND, legendary(Items.SHIELD, "shield", "offhand", "retribution"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            // 35% chance per hit: 30 hits miss every time with a chance of about 1 in 400,000
            for (int i = 0; i < 30 && !z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN); i++) hurt(p, p.damageSources().mobAttack(z));
            helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the attacker got slowness");
            helper.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the wearer didn't");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // arrows don't set off on-hit effects or crits, so the bow affixes are damage bonuses: check they count for arrows
    @GameTest(template = "gametest/empty")
    public static void nightstalkerOnlyAtNightAndWithArrows(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        long time = helper.getLevel().getDayTime();
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.BOW, "bow", "mainhand", "nightstalker"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            Arrow arrow = new Arrow(helper.getLevel(), p);
            setTime(helper, 6000);
            float day = hurt(z, p.damageSources().arrow(arrow, p));
            setTime(helper, 18000);
            float night = hurt(z, p.damageSources().arrow(arrow, p));
            helper.assertTrue(day == 10.0F, "no bonus during the day: " + day);
            helper.assertTrue(Math.abs(night - 14.0F) < 0.01F, "+40% at night (middle roll): " + night);
            helper.succeed();
        } finally {
            setTime(helper, time);
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void patientEyeOnlyWhileSneaking(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.CROSSBOW, "crossbow", "mainhand", "patient_eye"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            Arrow arrow = new Arrow(helper.getLevel(), p);
            float standing = hurt(z, p.damageSources().arrow(arrow, p));
            p.setShiftKeyDown(true);
            float sneaking = hurt(z, p.damageSources().arrow(arrow, p));
            helper.assertTrue(standing == 10.0F, "no bonus standing: " + standing);
            helper.assertTrue(Math.abs(sneaking - 15.5F) < 0.01F, "+55% sneaking (middle roll): " + sneaking);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Skyfall: more arrow damage against a mob in the air, whatever it is (a zombie knocked up counts, a phantom
    // sitting on the ground doesn't)
    @GameTest(template = "gametest/empty")
    public static void skyfallOnlyMobsInTheAir(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.BOW, "bow", "mainhand", "skyfall"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            Arrow arrow = new Arrow(helper.getLevel(), p);
            z.setOnGround(true);
            float ground = hurt(z, p.damageSources().arrow(arrow, p));
            z.setOnGround(false);
            float air = hurt(z, p.damageSources().arrow(arrow, p));
            helper.assertTrue(ground == 10.0F, "nothing against a zombie on the ground: " + ground);
            helper.assertTrue(Math.abs(air - 16.5F) < 0.01F, "+65% against a zombie in the air (middle roll): " + air);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void featherfallOnlyFalls(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.FEET, legendary(Items.IRON_BOOTS, "boots", "feet", "featherfall"));
            float fall = hurt(p, p.damageSources().fall());
            helper.assertTrue(Math.abs(fall - 3.5F) < 0.01F, "-65% from falls (middle roll): " + fall);
            helper.assertTrue(hurt(p, p.damageSources().generic()) == 10.0F, "nothing from other damage");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void juggernautStats(GameTestHelper helper) {
        // the stats the leggings give in their slot (what the player gets when wearing them)
        var mods = legendary(Items.IRON_LEGGINGS, "leggings", "legs", "juggernaut").getAttributeModifiers(EquipmentSlot.LEGS);
        double kb = mods.get(Attributes.KNOCKBACK_RESISTANCE).stream().filter(m -> m.getName().equals("Tarnished Relics")).mapToDouble(m -> m.getAmount()).sum();
        double tough = mods.get(Attributes.ARMOR_TOUGHNESS).stream().filter(m -> m.getName().equals("Tarnished Relics")).mapToDouble(m -> m.getAmount()).sum();
        helper.assertTrue(Math.abs(kb - 0.4) < 0.01, "+0.4 knockback resistance (middle roll): " + kb);
        helper.assertTrue(Math.abs(tough - 3.0) < 0.01, "+3 armor toughness (middle roll): " + tough);
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void secondWindOnlyBelowThirtyPercent(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.CHEST, legendary(Items.IRON_CHESTPLATE, "chestplate", "chest", "second_wind"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            p.setHealth(p.getMaxHealth());
            for (int i = 0; i < 30; i++) hurt(p, p.damageSources().mobAttack(z));
            boolean atFull = p.hasEffect(MobEffects.ABSORPTION);
            p.setHealth(p.getMaxHealth() * 0.2F);
            // 50% chance per hit: 30 hits miss every time with a chance of about 1 in a billion
            for (int i = 0; i < 30 && !p.hasEffect(MobEffects.ABSORPTION); i++) hurt(p, p.damageSources().mobAttack(z));
            helper.assertTrue(!atFull, "no absorption when hit at full health");
            helper.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "absorption when hit below 30% health");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void abyssalNightVisionOnlyInWater(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.HEAD, legendary(Items.IRON_HELMET, "helmet", "head", "abyssal"));
            passiveTick(p);
            helper.assertTrue(p.hasEffect(MobEffects.WATER_BREATHING), "water breathing all the time");
            helper.assertTrue(!p.hasEffect(MobEffects.NIGHT_VISION), "no night vision out of water");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void stormstrideOnlyDuringTheDay(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        long time = helper.getLevel().getDayTime();
        try {
            p.setItemSlot(EquipmentSlot.FEET, legendary(Items.IRON_BOOTS, "boots", "feet", "stormstride"));
            setTime(helper, 18000);
            passiveTick(p);
            boolean atNight = p.hasEffect(MobEffects.MOVEMENT_SPEED) || p.hasEffect(MobEffects.JUMP);
            setTime(helper, 6000);
            passiveTick(p);
            helper.assertTrue(!atNight, "no speed or jump boost at night");
            helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED) && p.hasEffect(MobEffects.JUMP), "speed and jump boost during the day");
            helper.succeed();
        } finally {
            setTime(helper, time);
            TestUtil.remove(p);
        }
    }

    // Wrath (damage_vs on weapons) boosts hits with the weapon (melee, arrows), not other damage the wielder causes:
    // their thorns, or an explosion they set off
    @GameTest(template = "gametest/empty")
    public static void damageVsOnlyBoostsWeaponAndProjectileHits(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.IRON_SWORD, "sword", "mainhand", "wrath"));
            helper.assertTrue(hurt(z, p.damageSources().playerAttack(p)) > 10.1F, "a melee hit wasn't boosted");
            helper.assertTrue(Math.abs(hurt(z, p.damageSources().thorns(p)) - 10) < 1e-4, "the wielder's thorns damage was boosted");
            helper.assertTrue(Math.abs(hurt(z, p.damageSources().explosion(null, p)) - 10) < 1e-4, "an explosion the wielder caused was boosted");
            var potion = new net.minecraft.world.entity.projectile.ThrownPotion(helper.getLevel(), p);
            helper.assertTrue(Math.abs(hurt(z, p.damageSources().indirectMagic(potion, p)) - 10) < 1e-4, "a Harming potion the wielder threw was boosted");
            p.setItemInHand(InteractionHand.MAIN_HAND, legendary(Items.BOW, "bow", "mainhand", "wrath"));
            Arrow arrow = new Arrow(helper.getLevel(), p);
            helper.assertTrue(hurt(z, p.damageSources().arrow(arrow, p)) > 10.1F, "an arrow from the bow wasn't boosted");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // is_day / is_night only in a dimension with days: the Nether and the End are neither (is_day counted them as day,
    // so Stormstride's day buffs were always on there)
    @GameTest(template = "gametest/empty")
    public static void isDayIsNeverTrueInTheNether(GameTestHelper helper) {
        Conditions day = Conditions.parse(JsonParser.parseString("{\"is_day\": true}"));
        Conditions night = Conditions.parse(JsonParser.parseString("{\"is_night\": true}"));
        long time = helper.getLevel().getDayTime();
        try {
            setTime(helper, 6000);   // noon in the overworld
            Zombie here = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
            helper.assertTrue(day.testIgnoringChance(here, null), "noon in the overworld isn't day");
            for (var dim : java.util.List.of(Level.NETHER, Level.END)) {
                Zombie there = EntityType.ZOMBIE.create(helper.getLevel().getServer().getLevel(dim));
                helper.assertFalse(day.testIgnoringChance(there, null), "is_day passed in " + dim.location());
                helper.assertFalse(night.testIgnoringChance(there, null), "is_night passed in " + dim.location());
            }
            helper.succeed();
        } finally {
            setTime(helper, time);
        }
    }

    // A general damage reduction (Bastion: less damage from anything) doesn't apply to damage meant to get through
    // everything: falling out of the world and /kill (#minecraft:bypasses_invulnerability). Bastion made you last
    // longer in the void. Ordinary hits are still reduced.
    @GameTest(template = "gametest/empty")
    public static void bastionDoesntReduceVoidOrKill(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.CHEST, legendary(Items.IRON_CHESTPLATE, "chestplate", "chest", "bastion"));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            helper.assertTrue(hurt(p, z.damageSources().mobAttack(z)) < 9.9F, "an ordinary hit wasn't reduced");
            helper.assertTrue(Math.abs(hurt(p, p.damageSources().fellOutOfWorld()) - 10) < 1e-4, "falling out of the world was reduced");
            helper.assertTrue(Math.abs(hurt(p, p.damageSources().genericKill()) - 10) < 1e-4, "/kill was reduced");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
