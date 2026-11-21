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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
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
}
