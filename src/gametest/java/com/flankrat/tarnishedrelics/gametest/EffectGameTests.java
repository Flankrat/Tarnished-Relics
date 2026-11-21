package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Conditions;
import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Collection;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/** Affix effects in the world: slots, conditions, damage and passive effects, using built-in affixes. */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class EffectGameTests {

    private static boolean hasMaxHealth(ItemStack stack, EquipmentSlot slot) {
        Collection<AttributeModifier> mods = stack.getAttributeModifiers(slot).get(Attributes.MAX_HEALTH);
        return mods.stream().anyMatch(m -> m.getName().equals("Tarnished Relics"));
    }

    // ---- slots ----

    @GameTest(template = "gametest/empty")
    public static void attributeOnlyInItsCategorySlot(GameTestHelper helper) {
        ItemStack helmet = TestUtil.relic(Items.IRON_HELMET, "magic", "helmet", "head", "tarnishedrelics:vital", 0.5);
        helper.assertTrue(hasMaxHealth(helmet, EquipmentSlot.HEAD), "vital helmet gives no health on the head");
        helper.assertFalse(hasMaxHealth(helmet, EquipmentSlot.MAINHAND), "vital helmet gives health in the hand");
        helper.succeed();
    }

    // widow's kiss is a sword whose max health effect says "slot": "offhand"
    @GameTest(template = "gametest/empty")
    public static void effectSlotOverridesTheCategorySlot(GameTestHelper helper) {
        ItemStack sword = TestUtil.relic(Items.IRON_SWORD, "legendary", "sword", "mainhand", "tarnishedrelics:legendary/widows_kiss", 0.5);
        helper.assertTrue(hasMaxHealth(sword, EquipmentSlot.OFFHAND), "widow's kiss gives no health in the offhand");
        helper.assertFalse(hasMaxHealth(sword, EquipmentSlot.MAINHAND), "widow's kiss gives health in the main hand");
        helper.succeed();
    }

    // the roll scales the value: vital (magic 1-2) at roll 0 is +1, at roll 1 is +2
    @GameTest(template = "gametest/empty")
    public static void rollScalesTheAttribute(GameTestHelper helper) {
        for (double[] c : new double[][]{{0, 1}, {1, 2}, {0.5, 1.5}}) {
            ItemStack helmet = TestUtil.relic(Items.IRON_HELMET, "magic", "helmet", "head", "tarnishedrelics:vital", c[0]);
            double amount = helmet.getAttributeModifiers(EquipmentSlot.HEAD).get(Attributes.MAX_HEALTH).stream()
                    .filter(m -> m.getName().equals("Tarnished Relics")).mapToDouble(AttributeModifier::getAmount).sum();
            helper.assertTrue(Math.abs(amount - c[1]) < 1e-4, "roll " + c[0] + " gave " + amount + ", expected " + c[1]);
        }
        helper.succeed();
    }

    // ---- conditions ----

    private static Conditions conditionsOf(String affix, Effect.Type type) {
        return TRData.get().affixes().get(tr(affix)).effects().stream().filter(e -> e.type == type).findFirst().orElseThrow().conditions;
    }

    @GameTest(template = "gametest/empty")
    public static void healthBelow(GameTestHelper helper) {
        Conditions c = conditionsOf("unique_berserker", Effect.Type.DAMAGE_VS);   // health_below 0.4
        Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        z.setNoAi(true);
        z.setHealth(z.getMaxHealth());
        helper.assertFalse(c.test(z, null, z.getRandom()), "health_below passed at full health");
        z.setHealth(z.getMaxHealth() * 0.3F);
        helper.assertTrue(c.test(z, null, z.getRandom()), "health_below failed at 30% health");
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void inWater(GameTestHelper helper) {
        Conditions c = conditionsOf("legendary/tidebreaker", Effect.Type.DAMAGE_VS);   // in_water
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
        Zombie wet = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie dry = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 4));
        wet.setNoAi(true);
        dry.setNoAi(true);
        // water state is worked out when the entity ticks
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(c.test(wet, null, wet.getRandom()), "in_water failed in water");
            helper.assertFalse(c.test(dry, null, dry.getRandom()), "in_water passed on land");
            helper.succeed();
        });
    }

    @GameTest(template = "gametest/empty")
    public static void sneaking(GameTestHelper helper) {
        Conditions c = Conditions.parse(JsonParser.parseString("{\"sneaking\": true}"));
        ServerPlayer p = TestUtil.player(helper);
        try {
            helper.assertFalse(c.test(p, null, p.getRandom()), "sneaking passed while standing");
            p.setShiftKeyDown(true);
            helper.assertTrue(c.test(p, null, p.getRandom()), "sneaking failed while sneaking");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // end to end: berserker's +30-50% damage only while the wielder is below 40% health
    @GameTest(template = "gametest/empty")
    public static void damageVsWithCondition(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    TestUtil.relic(Items.IRON_SWORD, "unique", "sword", "mainhand", "tarnishedrelics:unique_berserker", 0.0));
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            LivingHurtEvent full = new LivingHurtEvent(z, p.damageSources().playerAttack(p), 10);
            MinecraftForge.EVENT_BUS.post(full);
            helper.assertTrue(Math.abs(full.getAmount() - 10) < 1e-4, "bonus at full health: " + full.getAmount());
            p.setHealth(p.getMaxHealth() * 0.2F);
            LivingHurtEvent low = new LivingHurtEvent(z, p.damageSources().playerAttack(p), 10);
            MinecraftForge.EVENT_BUS.post(low);
            helper.assertTrue(Math.abs(low.getAmount() - 13) < 1e-4, "expected 13 at low health (roll 0 = +30%), got " + low.getAmount());
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // ---- passive effects (refreshed once a second by the player tick) ----

    private static final String PASSIVE_KEY = "TarnishedRelicsPassive";

    private static void secondTick(ServerPlayer p) {
        p.tickCount = 20 * (p.tickCount / 20 + 1);   // the passive check runs on whole seconds
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    /** An instance like the ones passives give (ambient, no particles). */
    private static MobEffectInstance ours(net.minecraft.world.effect.MobEffect effect, int duration, int amp) {
        return new MobEffectInstance(effect, duration, amp, true, false, true);
    }

    private static void markGiven(ServerPlayer p, String effect) {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(effect));
        p.getPersistentData().put(PASSIVE_KEY, list);
    }

    private static ItemStack windstrideBoots() {   // jump boost II (amplifier 1)
        return TestUtil.relic(Items.DIAMOND_BOOTS, "legendary", "boots", "feet", "tarnishedrelics:legendary/windstride", 0.5);
    }

    private static ItemStack nightSightHelmet() {   // night vision, amplifier 0
        return TestUtil.relic(Items.IRON_HELMET, "magic", "helmet", "head", "tarnishedrelics:of_night_sight", 0.5);
    }

    private static int amp(ServerPlayer p, net.minecraft.world.effect.MobEffect e) {
        MobEffectInstance i = p.getEffect(e);
        return i == null ? -1 : i.getAmplifier();
    }

    @GameTest(template = "gametest/empty")
    public static void passiveIsGivenWhileWorn(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            secondTick(p);
            helper.assertTrue(amp(p, MobEffects.JUMP) == 1, "jump boost amp " + amp(p, MobEffects.JUMP));
            // in the hand (not its slot) it does nothing
            p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            p.removeAllEffects();
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, windstrideBoots());
            secondTick(p);
            helper.assertTrue(amp(p, MobEffects.JUMP) == -1, "boots in the hand gave jump boost");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // not reset every second while it has plenty left (night vision flicker, regeneration timing)
    @GameTest(template = "gametest/empty")
    public static void passiveIsOnlyToppedUpWhenLow(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            p.addEffect(ours(MobEffects.JUMP, 300, 1));
            secondTick(p);
            helper.assertTrue(p.getEffect(MobEffects.JUMP).getDuration() == 300, "reset with 300 ticks left");
            p.removeAllEffects();
            p.addEffect(ours(MobEffects.JUMP, 100, 1));
            secondTick(p);
            helper.assertTrue(p.getEffect(MobEffects.JUMP).getDuration() == 400, "not topped up with 100 ticks left");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Bug (round 2): a weaker potion of the same effect blocked the relic's stronger one until the potion ran low
    @GameTest(template = "gametest/empty")
    public static void strongerPassiveBeatsAWeakerPotion(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 3000, 0));   // a drunk potion, jump boost I
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            secondTick(p);
            helper.assertTrue(amp(p, MobEffects.JUMP) == 1, "jump boost amp " + amp(p, MobEffects.JUMP) + ", expected the boots' 1");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Bug (round 2): swapping to a stronger item kept the weaker passive until it ran low (about 9 seconds)
    @GameTest(template = "gametest/empty")
    public static void upgradeIsImmediate(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.addEffect(ours(MobEffects.JUMP, 400, 0));   // what a jump boost I item gave a moment ago
            markGiven(p, "minecraft:jump_boost");
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            secondTick(p);
            helper.assertTrue(amp(p, MobEffects.JUMP) == 1, "jump boost amp " + amp(p, MobEffects.JUMP) + " after upgrading");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Bug (round 2): swapping to a weaker item kept the stronger passive for its full 20 seconds
    @GameTest(template = "gametest/empty")
    public static void downgradeIsImmediate(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.addEffect(ours(MobEffects.NIGHT_VISION, 400, 2));   // what a stronger night vision item gave
            markGiven(p, "minecraft:night_vision");
            p.setItemSlot(EquipmentSlot.HEAD, nightSightHelmet());
            secondTick(p);
            helper.assertTrue(amp(p, MobEffects.NIGHT_VISION) == 0, "night vision amp " + amp(p, MobEffects.NIGHT_VISION) + " after downgrading");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // a stronger potion is never replaced by the relic's weaker effect
    @GameTest(template = "gametest/empty")
    public static void strongerPotionIsKept(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3000, 2));
            p.setItemSlot(EquipmentSlot.HEAD, nightSightHelmet());
            secondTick(p);
            MobEffectInstance now = p.getEffect(MobEffects.NIGHT_VISION);
            helper.assertTrue(now.getAmplifier() == 2 && now.getDuration() >= 2990, "the potion was replaced: " + now);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void passiveGoesWhenTheItemDoes(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            secondTick(p);
            p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            secondTick(p);
            helper.assertTrue(p.getEffect(MobEffects.JUMP) == null, "jump boost stayed after taking the boots off");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // taking the item off doesn't remove a potion of the same effect
    @GameTest(template = "gametest/empty")
    public static void potionStaysWhenTheItemGoes(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            p.setItemSlot(EquipmentSlot.FEET, windstrideBoots());
            secondTick(p);
            p.removeAllEffects();
            p.addEffect(new MobEffectInstance(MobEffects.JUMP, 3000, 0));
            p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            secondTick(p);
            helper.assertTrue(p.getEffect(MobEffects.JUMP) != null, "the potion was removed with the boots");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
