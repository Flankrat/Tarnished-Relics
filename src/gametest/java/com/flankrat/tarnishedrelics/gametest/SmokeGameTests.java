package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The data loaded in the test world. */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class SmokeGameTests {

    @GameTest(template = "gametest/empty")
    public static void dataLoaded(GameTestHelper helper) {
        helper.assertTrue(TRData.get().errors().isEmpty(), "data errors: " + TRData.get().errors());
        helper.assertTrue(TRData.get().legendaries().size() == 10, "legendaries: " + TRData.get().legendaries().size());
        helper.succeed();
    }
}
