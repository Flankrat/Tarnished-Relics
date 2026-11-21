package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.effect.RelicEffects;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The effect handlers that other tests don't reach, so a change to RelicEffects can't lose them: durability (wear),
 * "function" effects on hit / kill / hurt (as the wielder or the target), thorns reflecting damage, on-hit fire.
 * Uses test affixes added for this batch and the test function tarnishedrelics:test/mark (tags whoever runs it).
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class EffectHandlerGameTests {

    private static final String BATCH = "handlers";
    private static final String MARK = "trtest_fn";
    private static Map<ResourceLocation, JsonElement> original;

    @BeforeBatch(batch = BATCH)
    public static void load(ServerLevel level) {
        original = TestUtil.currentFiles();
        Map<ResourceLocation, JsonElement> files = TestUtil.currentFiles();
        add(files, "wear_never", "{\"type\": \"durability\", \"value\": 1.0}");
        add(files, "wear_double", "{\"type\": \"durability\", \"value\": -1.0}");
        add(files, "fn_hit", "{\"type\": \"function\", \"function\": \"tarnishedrelics:test/mark\", \"trigger\": \"hit\", \"as\": \"target\"}");
        add(files, "fn_kill", "{\"type\": \"function\", \"function\": \"tarnishedrelics:test/mark\", \"trigger\": \"kill\"}");
        add(files, "fn_hurt", "{\"type\": \"function\", \"function\": \"tarnishedrelics:test/mark\", \"trigger\": \"hurt\"}");
        add(files, "thorns", "{\"type\": \"thorns\", \"value\": 0.5}");
        add(files, "fire", "{\"type\": \"on_hit_fire\", \"value\": 4}");
        add(files, "steal", "{\"type\": \"lifesteal\", \"value\": 0.5}");
        TRData.load(files);
    }

    private static void add(Map<ResourceLocation, JsonElement> files, String name, String effect) {
        files.put(new ResourceLocation("trtest", "affixes/" + name), JsonParser.parseString("{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [" + effect + "]}"));
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        TRData.load(original);
    }

    private static ItemStack sword(String affix) {
        return TestUtil.relic(Items.IRON_SWORD, "rare", "sword", "mainhand", "trtest:" + affix, 0.5);
    }

    private static ItemStack chest(String affix) {
        return TestUtil.relic(Items.IRON_CHESTPLATE, "rare", "chestplate", "chest", "trtest:" + affix, 0.5);
    }

    private static Zombie zombie(GameTestHelper helper, int x) {
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 1, 3));
        z.setNoAi(true);
        return z;
    }

    private static void hurt(LivingEntity victim, DamageSource source, float amount) {
        MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(victim, source, amount));
    }

    // durability 1.0: never wears; -1.0: every point of wear counts twice
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void durabilityChangesWear(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1);
        helper.assertTrue(RelicEffects.adjustWear(sword("wear_never"), 10, random) == 0, "durability 1.0 still wore");
        helper.assertTrue(RelicEffects.adjustWear(sword("wear_double"), 10, random) == 20, "durability -1.0 didn't wear double");
        helper.assertTrue(RelicEffects.adjustWear(new ItemStack(Items.IRON_SWORD), 10, random) == 10, "a plain sword's wear changed");
        helper.succeed();
    }

    // a hit function runs as the target on a melee hit (not on the wielder's thorns); a kill function on a kill
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void functionsOnHitAndKill(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = zombie(helper, 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, sword("fn_hit"));
            hurt(z, p.damageSources().thorns(p), 4);
            helper.assertFalse(z.getTags().contains(MARK), "the hit function ran on thorns damage");
            hurt(z, p.damageSources().playerAttack(p), 4);
            helper.assertTrue(z.getTags().contains(MARK), "the hit function didn't run as the target");
            helper.assertFalse(p.getTags().contains(MARK), "the hit function ran as the wielder");
            p.setItemInHand(InteractionHand.MAIN_HAND, sword("fn_kill"));
            Zombie other = zombie(helper, 4);
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(other, p.damageSources().playerAttack(p)));
            helper.assertTrue(p.getTags().contains(MARK), "the kill function didn't run as the wielder");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // armor's hurt function runs when its wearer is hit; thorns hit back for half the damage, but not thorns with thorns
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void hurtFunctionAndThorns(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = zombie(helper, 2);
            p.setItemSlot(EquipmentSlot.CHEST, chest("fn_hurt"));
            hurt(p, z.damageSources().mobAttack(z), 4);
            helper.assertTrue(p.getTags().contains(MARK), "the hurt function didn't run");
            p.setItemSlot(EquipmentSlot.CHEST, chest("thorns"));
            float before = z.getHealth();
            hurt(p, z.damageSources().thorns(z), 4);
            helper.assertTrue(z.getHealth() == before, "thorns answered thorns");
            hurt(p, z.damageSources().mobAttack(z), 4);
            helper.assertTrue(z.getHealth() < before, "thorns didn't hit back");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // on-hit fire: a melee hit sets the target on fire, an arrow doesn't (on-hit effects are for weapon hits)
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void onHitFireOnlyOnMeleeHits(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = zombie(helper, 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, sword("fire"));
            hurt(z, p.damageSources().arrow(new Arrow(helper.getLevel(), p), p), 2);
            helper.assertFalse(z.isOnFire(), "an arrow set off on-hit fire");
            hurt(z, p.damageSources().playerAttack(p), 2);
            helper.assertTrue(z.isOnFire(), "a melee hit didn't set the target on fire");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Lifesteal heals from the damage the hit really does: other mods change it in LivingDamageEvent too (JLME's
    // Advanced Protection, after armor) or cancel the hit, so it's read last, after them. Read earlier, a hit another
    // mod halved healed as if it hadn't been, and a cancelled one healed too.
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void lifestealUsesTheFinalDamage(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        boolean[] cancel = {false};
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDamageEvent> otherMod = e -> {
            if (!(e.getEntity() instanceof Zombie)) return;
            if (cancel[0]) e.setCanceled(true);
            else e.setAmount(e.getAmount() / 2);   // like a protection enchantment applied after armor
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW, false,
                net.minecraftforge.event.entity.living.LivingDamageEvent.class, otherMod);
        try {
            Zombie z = zombie(helper, 2);
            p.setItemInHand(InteractionHand.MAIN_HAND, sword("steal"));
            p.setHealth(10);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDamageEvent(z, p.damageSources().playerAttack(p), 8));
            helper.assertTrue(Math.abs(p.getHealth() - 12) < 1e-4, "50% lifesteal of a hit halved to 4 should heal 2, health is " + p.getHealth());
            cancel[0] = true;
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDamageEvent(z, p.damageSources().playerAttack(p), 8));
            helper.assertTrue(Math.abs(p.getHealth() - 12) < 1e-4, "a cancelled hit healed: health " + p.getHealth());
            helper.succeed();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(otherMod);
            TestUtil.remove(p);
        }
    }
}
