package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Legendary set bonuses: a set's "set_effects" work only while every piece is in its own slot, and its attributes go
 * on the player while the set is complete and come off when it isn't. A two-piece test set: a sword (with the set
 * effects: +100% damage, +10 max health) and a helmet.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class SetBonusGameTests {

    private static final String BATCH = "sets";
    private static Map<ResourceLocation, JsonElement> original;

    @BeforeBatch(batch = BATCH)
    public static void load(ServerLevel level) {
        original = TestUtil.currentFiles();
        Map<ResourceLocation, JsonElement> files = TestUtil.currentFiles();
        files.put(new ResourceLocation("trtest", "legendaries/set_sword"), JsonParser.parseString("{\"name\": \"Set Sword\", \"items\": [\"minecraft:iron_sword\"],"
                + " \"category\": \"tarnishedrelics:sword\", \"weight\": 0, \"companions\": [\"trtest:set_helm\"],"
                + " \"set_effects\": [{\"type\": \"damage_vs\", \"value\": 1.0},"
                + " {\"type\": \"attribute\", \"attribute\": \"minecraft:generic.max_health\", \"operation\": \"add\", \"value\": 10}]}"));
        files.put(new ResourceLocation("trtest", "legendaries/set_helm"), JsonParser.parseString("{\"name\": \"Set Helm\", \"items\": [\"minecraft:iron_helmet\"],"
                + " \"category\": \"tarnishedrelics:helmet\", \"weight\": 0}"));
        TRData.load(files);
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        TRData.load(original);
    }

    // the set, made like a drop: the sword and the helmet that drops with it
    private static List<ItemStack> set() {
        var l = TRData.get().legendaries().get(new ResourceLocation("trtest", "set_sword"));
        return Roller.generateAll(RandomSource.create(1), null, null, l, RelicGeneratedEvent.Source.COMMAND, null).stream()
                .map(UnidentifiedItem::hidden).toList();
    }

    private static float hurt(Zombie z, ServerPlayer p) {
        LivingHurtEvent e = new LivingHurtEvent(z, p.damageSources().playerAttack(p), 10);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getAmount();
    }

    private static void secondTick(ServerPlayer p) {
        p.tickCount = 20 * (p.tickCount / 20 + 1);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, p));
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void setBonusNeedsTheWholeSetInItsSlots(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            List<ItemStack> set = set();
            helper.assertTrue(set.size() == 2, "the set made " + set.size() + " pieces");
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            p.setItemInHand(InteractionHand.MAIN_HAND, set.get(0));
            helper.assertTrue(Math.abs(hurt(z, p) - 10) < 1e-4, "the bonus worked with the sword alone");
            p.setItemInHand(InteractionHand.OFF_HAND, set.get(1));   // the helmet, held: not in its slot
            helper.assertTrue(Math.abs(hurt(z, p) - 10) < 1e-4, "the bonus worked with the helmet held, not worn");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.HEAD, set.get(1));
            helper.assertTrue(Math.abs(hurt(z, p) - 20) < 1e-4, "+100% with the whole set: " + hurt(z, p));
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(Math.abs(hurt(z, p) - 10) < 1e-4, "the bonus worked with the helmet alone");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void setAttributesComeAndGoWithTheSet(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            List<ItemStack> set = set();
            double base = p.getAttributeValue(Attributes.MAX_HEALTH);
            p.setItemInHand(InteractionHand.MAIN_HAND, set.get(0));
            p.setItemSlot(EquipmentSlot.HEAD, set.get(1));
            secondTick(p);
            helper.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH) == base + 10, "max health with the set: " + p.getAttributeValue(Attributes.MAX_HEALTH));
            secondTick(p);
            helper.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH) == base + 10, "the set's health stacked: " + p.getAttributeValue(Attributes.MAX_HEALTH));
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            secondTick(p);
            helper.assertTrue(p.getAttributeValue(Attributes.MAX_HEALTH) == base, "the set's health stayed after the set broke: " + p.getAttributeValue(Attributes.MAX_HEALTH));
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // the built-in Dawnbreaker set has a bonus, and the crest says so
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void dawnbreakerHasASetBonus(GameTestHelper helper) {
        var dawnbreaker = TRData.get().legendaries().get(new ResourceLocation(TarnishedRelics.MODID, "dawnbreaker"));
        helper.assertTrue(dawnbreaker != null && !dawnbreaker.setEffects().isEmpty(), "Dawnbreaker has no set bonus");
        helper.succeed();
    }
}
