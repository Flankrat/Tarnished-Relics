package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.Roller;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/** The unidentified relic item itself, dropped in the world. */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class RelicItemGameTests {

    private static ItemEntity drop(GameTestHelper helper, ItemStack stack, int x) {
        Vec3 at = helper.absoluteVec(new Vec3(x + 0.5, 1.2, 2.5));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static ItemStack relic(String category) {
        return Roller.generate(RandomSource.create(7), TRData.get().rarities().get(tr("rare")), TRData.get().categories().get(tr(category)));
    }

    /**
     * Drops a relic and a stick (the control: it must burn, or the test proves nothing) into this block, on
     * netherrack so fire never goes out, and checks after 5 seconds.
     */
    private static void burnTest(GameTestHelper helper, Block block, String category) {
        for (int x : new int[]{1, 3}) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.NETHERRACK);
            helper.setBlock(new BlockPos(x, 1, 2), block);
        }
        ItemEntity relic = drop(helper, relic(category), 1);
        ItemEntity stick = drop(helper, new ItemStack(Items.STICK), 3);
        helper.runAfterDelay(100, () -> {
            helper.assertFalse(stick.isAlive(), "the stick didn't burn: this block didn't burn anything");
            helper.assertTrue(relic.isAlive(), "an unidentified " + category + " relic burned up in " + block.getName().getString());
            helper.succeed();
        });
    }

    // an unidentified relic dropped in lava or fire isn't destroyed (like netherite gear), whatever kind it is
    @GameTest(template = "gametest/empty", timeoutTicks = 200)
    public static void unidentifiedSurvivesLava(GameTestHelper helper) {
        burnTest(helper, Blocks.LAVA, "sword");
    }

    @GameTest(template = "gametest/empty", timeoutTicks = 200)
    public static void unidentifiedSurvivesFire(GameTestHelper helper) {
        burnTest(helper, Blocks.FIRE, "helmet");
    }
}
