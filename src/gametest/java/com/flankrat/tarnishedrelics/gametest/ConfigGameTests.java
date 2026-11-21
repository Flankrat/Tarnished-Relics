package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.editor.ConfigFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The editor's Settings tab: saving the config file's values (the same code the editor's save runs). */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class ConfigGameTests {

    private static final String BATCH = "config";
    private static JsonObject original;

    @BeforeBatch(batch = BATCH)
    public static void remember(ServerLevel level) {
        original = ConfigFile.read();
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        ConfigFile.write(original);
    }

    // every value different from the defaults, and different between the two versions
    private static JsonObject values(int n) {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", n % 2 == 0);
        o.addProperty("chance", 0.1 + n * 0.01);
        JsonArray types = new JsonArray();
        types.add("chest");
        if (n % 2 == 0) types.add("fishing");
        else types.add("barter");
        o.add("lootTypes", types);
        o.addProperty("entityNeedsPlayerKill", n % 2 == 0);
        JsonArray tables = new JsonArray();
        tables.add("minecraft:chests/test_" + n);
        o.add("excludeTables", tables);
        o.addProperty("confirmSeconds", 20 + n);
        o.addProperty("showRarityColorUnidentified", n % 2 == 0);
        return o;
    }

    // Bug (round 3): each value was set on its own, and Forge saves the file after every set and then reads it back
    // on its file-watcher thread. A read-back between two sets put the older values back in memory, and the next
    // save wrote them to the file, so a save from the Settings tab could lose some of its values for good.
    @GameTest(template = "gametest/empty", batch = BATCH, timeoutTicks = 1200)
    public static void savedValuesStay(GameTestHelper helper) {
        GameTestSequence seq = helper.startSequence();
        for (int round = 0; round < 20; round++) {
            JsonObject want = values(round);
            seq.thenExecute(() -> ConfigFile.write(want))
                    // give the file watcher time to read the saves back
                    .thenIdle(30)
                    .thenExecute(() -> helper.assertTrue(ConfigFile.read().equals(want), "saved " + want + "\nbut have " + ConfigFile.read()));
        }
        seq.thenSucceed();
    }
}
