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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
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
}
