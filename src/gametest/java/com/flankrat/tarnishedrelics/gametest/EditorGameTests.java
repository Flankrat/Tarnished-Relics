package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.editor.ModpackPack;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.smith.Blacksmith;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/**
 * The editor's saves as an op makes them: files sent, committed to "This world"
 * ({@code <world>/datapacks/tarnishedrelics_editor}) or "Whole modpack" ({@code config/tarnishedrelics/datapack}),
 * the game reloads and the change is live. Each save test is its own batch, since a reload replaces the data
 * every other test is using.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class EditorGameTests {

    private static final ResourceLocation ID = tr("affixes/zz_editor_test");   // the editor's file id
    private static final ResourceLocation AFFIX = tr("zz_editor_test");        // the affix it makes

    private static String affix(int weight) {
        return "{\"kind\": \"hidden\", \"pool\": \"zz_test\", \"weight\": " + weight + ", \"effects\": []}";
    }

    private static Path worldFile(GameTestHelper helper) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(EditorServer.PACK).resolve("data/tarnishedrelics/tarnished_relics/affixes/zz_editor_test.json");
    }

    private static Path modpackFile() {
        return ModpackPack.folder().resolve("data/tarnishedrelics/tarnished_relics/affixes/zz_editor_test.json");
    }

    private static ServerPlayer editor(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        TestUtil.op(p);
        helper.assertTrue(EditorServer.allowed(p), "the test player isn't allowed to use the editor");
        return p;
    }

    private static void save(ServerPlayer p, String json, boolean modpack) {
        EditorServer.receiveFile(p, ID, json, false);
        EditorServer.commit(p, modpack);
    }

    private static int loadedWeight() {
        var a = TRData.get().affixes().get(AFFIX);
        return a == null ? -1 : a.weight();
    }

    private static void noErrors(GameTestHelper helper) {
        helper.assertTrue(TRData.get().errors().isEmpty(), "errors after the reload: " + TRData.get().errors());
    }

    // "This world": written to the world's pack, live after the reload; removing it deletes the file again
    @GameTest(template = "gametest/empty", batch = "editor_world", timeoutTicks = 600)
    public static void saveToThisWorld(GameTestHelper helper) {
        ServerPlayer p = editor(helper);
        save(p, affix(3), false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight() == 3, "not loaded yet"))
                .thenExecute(() -> {
                    noErrors(helper);
                    helper.assertTrue(Files.exists(worldFile(helper)), "no file in the world pack");
                    helper.assertFalse(Files.exists(modpackFile()), "a world save wrote to the modpack pack");
                    save(p, "", false);   // removed in the editor
                })
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight() == -1, "still loaded after removing"))
                .thenExecute(() -> {
                    helper.assertFalse(Files.exists(worldFile(helper)), "the removed file is still in the world pack");
                    noErrors(helper);
                    TestUtil.remove(p);
                })
                .thenSucceed();
    }

    // "Whole modpack": written to the config pack, and the world's copy of the same file is deleted so it can't hide it
    @GameTest(template = "gametest/empty", batch = "editor_modpack", timeoutTicks = 900)
    public static void saveToModpackRemovesTheWorldCopy(GameTestHelper helper) {
        ServerPlayer p = editor(helper);
        save(p, affix(3), false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight() == 3, "world save not loaded yet"))
                .thenExecute(() -> save(p, affix(7), true))
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight() == 7, "modpack save not loaded (weight " + loadedWeight() + ")"))
                .thenExecute(() -> {
                    noErrors(helper);
                    helper.assertTrue(Files.exists(modpackFile()), "no file in the modpack pack");
                    helper.assertFalse(Files.exists(worldFile(helper)), "the world's copy is still there");
                    save(p, "", true);
                })
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight() == -1, "still loaded after removing"))
                .thenExecute(() -> {
                    helper.assertFalse(Files.exists(modpackFile()), "the removed file is still in the modpack pack");
                    TestUtil.remove(p);
                })
                .thenSucceed();
    }

    // broken files are refused before anything is written, and the server carries on
    @GameTest(template = "gametest/empty")
    public static void brokenFilesAreRefused(GameTestHelper helper) {
        ServerPlayer p = editor(helper);
        try {
            for (String bad : new String[]{"{\"kind\": ", "[1, 2]", "{\"effects\": [{\"type\": \"no_such_type\"}]}"}) {
                for (boolean modpack : new boolean[]{false, true}) {
                    save(p, bad, modpack);
                    helper.assertFalse(Files.exists(worldFile(helper)) || Files.exists(modpackFile()), "wrote a broken file: " + bad);
                    helper.assertTrue(loadedWeight() == -1, "a broken file got loaded: " + bad);
                }
            }
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // a save with a file id outside the mod's folders (a tag id with "..") is refused as a whole, before anything is
    // written: it was only found while writing, after the save's other files were already on disk
    @GameTest(template = "gametest/empty")
    public static void badFileIdIsRefusedBeforeWriting(GameTestHelper helper) {
        ServerPlayer p = editor(helper);
        ResourceLocation bad = new ResourceLocation("minecraft", "tags/../loot_tables/chests/zz_trtest");
        Path badFile = helper.getLevel().getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(EditorServer.PACK)
                .resolve("data/minecraft/loot_tables/chests/zz_trtest.json");
        try {
            EditorServer.receiveFile(p, ID, affix(3), false);
            EditorServer.receiveFile(p, bad, "{\"values\": []}", false);
            EditorServer.commit(p, false);
            helper.assertFalse(Files.exists(badFile), "wrote a loot table through a tag id");
            helper.assertFalse(Files.exists(worldFile(helper)), "the save's good file was written though the save was refused");
            helper.succeed();
        } finally {
            try {
                Files.deleteIfExists(worldFile(helper));
                Files.deleteIfExists(badFile);
            } catch (java.io.IOException ignored) {
                // nothing there
            }
            TestUtil.remove(p);
        }
    }

    // not an op: nothing is saved
    @GameTest(template = "gametest/empty")
    public static void needsOp(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        try {
            save(p, affix(3), false);
            helper.assertFalse(Files.exists(worldFile(helper)), "a non-op's save was written");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    private static boolean qualityLoaded() {
        return ModList.get().isLoaded("quality_forked");
    }

    // The editor's "give identified" doesn't call the smith's Quality roll, but the relic goes into the inventory,
    // where Quality rolls it anyway: checked here so it stays that way. Needs Quality Equipment Forked (-PoptionalMods).
    @GameTest(template = "gametest/empty")
    public static void giveIdentifiedRollsQualityLikeTheSmith(GameTestHelper helper) {
        if (!qualityLoaded()) {
            TarnishedRelics.LOGGER.info("[gametest] Quality Equipment not installed, giveIdentifiedRollsQualityLikeTheSmith checks nothing");
            helper.succeed();
            return;
        }
        ServerPlayer p = editor(helper);
        try {
            // the smith's way, for comparison
            ItemStack wrapper = Roller.generate(RandomSource.create(1), TRData.get().rarities().get(tr("magic")), TRData.get().categories().get(tr("sword")));
            helper.assertTrue(Blacksmith.identify(wrapper, p).getTag().contains("tool_quality"), "the smith's identify gave no quality");
            p.getInventory().clearContent();
            EditorServer.give(p, tr("magic"), tr("sword"), null, true);
            ItemStack given = p.getInventory().items.stream().filter(RelicTag::isRelic).findFirst().orElse(ItemStack.EMPTY);
            helper.assertFalse(given.isEmpty() || given.is(TRItems.UNIDENTIFIED.get()), "nothing identified was given");
            helper.assertTrue(given.getTag().contains("tool_quality"), "the editor's identified relic has no quality: " + given.getTag());
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
