package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import com.flankrat.tarnishedrelics.editor.ModpackPack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/**
 * In-world findings of the Nov 24 2026 bug scan (the plain-JUnit ones are in ScanFindingsTest). Each save test is
 * its own batch, since a reload replaces the data every other test is using.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class ScanFindingsGameTests {

    private static String affix(int weight) {
        return "{\"kind\": \"hidden\", \"pool\": \"zz_test\", \"weight\": " + weight + ", \"effects\": []}";
    }

    private static Path worldFile(GameTestHelper helper, String name) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.DATAPACK_DIR)
                .resolve(EditorServer.PACK).resolve("data/tarnishedrelics/tarnished_relics/affixes/" + name + ".json");
    }

    private static int loadedWeight(String name) {
        var a = TRData.get().affixes().get(tr(name));
        return a == null ? -1 : a.weight();
    }

    // an op at the given level (TestUtil.op gives 4)
    private static ServerPlayer player(GameTestHelper helper, int level) {
        ServerPlayer p = TestUtil.player(helper);
        p.server.getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), level, false));
        p.server.getPlayerList().sendPlayerPermissionLevel(p);
        return p;
    }

    private static void deleteQuietly(Path... files) {
        for (Path f : files) {
            try {
                if (Files.isDirectory(f)) Files.delete(f);
                else Files.deleteIfExists(f);
            } catch (IOException ignored) {
                // nothing there
            }
        }
    }

    // #1: the second file can't be written (its path is a folder). The first one already was, so the server must
    // reload and run what's on disk; it skipped the reload and kept the old data
    @GameTest(template = "gametest/empty", batch = "scan_partial_write", timeoutTicks = 600)
    public static void aSaveThatFailsHalfwayStillReloads(GameTestHelper helper) {
        ServerPlayer p = player(helper, 4);
        Path first = worldFile(helper, "zz_scan_first");
        Path second = worldFile(helper, "zz_scan_second");
        try {
            Files.createDirectories(second);   // a folder where the second file goes: writing it fails
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        EditorServer.receiveFile(p, tr("affixes/zz_scan_first"), affix(7), false);
        EditorServer.receiveFile(p, tr("affixes/zz_scan_second"), affix(8), false);
        EditorServer.commit(p, false);
        helper.startSequence()
                .thenExecuteAfter(1, () -> helper.assertTrue(Files.exists(first), "the first file wasn't written (test setup)"))
                .thenWaitUntil(() -> helper.assertTrue(loadedWeight("zz_scan_first") == 7,
                        "a file the failed save wrote is on disk but the server never reloaded it"))
                .thenExecute(() -> {
                    deleteQuietly(first, second);
                    TestUtil.remove(p);
                })
                .thenSucceed();
    }

    // #4: op level 2 (command blocks, moderators) may edit this world, but not files every world loads: the whole-
    // modpack pack and the config. Those need level 4, which a server's ops and single player with cheats have
    @GameTest(template = "gametest/empty", batch = "scan_permission", timeoutTicks = 600)
    public static void levelTwoCanNotSaveForTheWholeModpack(GameTestHelper helper) {
        ServerPlayer p = player(helper, 2);
        Path modpackFile = ModpackPack.folder().resolve("data/tarnishedrelics/tarnished_relics/affixes/zz_scan_level2.json");
        try {
            helper.assertTrue(EditorServer.allowed(p), "level 2 can't open the editor at all");
            EditorServer.receiveFile(p, tr("affixes/zz_scan_level2"), affix(5), false);
            EditorServer.commit(p, true);
            helper.assertFalse(Files.exists(modpackFile), "a level 2 op saved for the whole modpack");
            helper.succeed();
        } finally {
            deleteQuietly(modpackFile);
            TestUtil.remove(p);
        }
    }

    // #6: empty entries count as 0 characters, so a client could queue any number of them; a save with more files
    // than an editor ever sends is refused as a whole
    @GameTest(template = "gametest/empty", batch = "scan_many_files", timeoutTicks = 600)
    public static void tooManyFilesInOneSaveAreRefused(GameTestHelper helper) {
        ServerPlayer p = player(helper, 4);
        Path good = worldFile(helper, "zz_scan_many");
        try {
            for (int i = 0; i < 20_000; i++) EditorServer.receiveFile(p, tr("affixes/zz_flood_" + i), "", false);
            EditorServer.receiveFile(p, tr("affixes/zz_scan_many"), affix(4), false);
            EditorServer.commit(p, false);
            helper.assertFalse(Files.exists(good), "a save of 20,000 files was accepted");
            helper.succeed();
        } finally {
            deleteQuietly(good);
            TestUtil.remove(p);
        }
    }

    // #6: a big file sent in 8,000-character pieces was copied whole for every piece (and every queued file was
    // re-counted per piece): 1,000 pieces (8 MB) took seconds on the server thread
    @GameTest(template = "gametest/empty", batch = "scan_big_file", timeoutTicks = 600)
    public static void sendingABigFileInPiecesIsFast(GameTestHelper helper) {
        ServerPlayer p = player(helper, 4);
        try {
            String piece = "x".repeat(8000);
            long start = System.nanoTime();
            EditorServer.receiveFile(p, tr("affixes/zz_scan_big"), piece, false);
            for (int i = 1; i < 1000; i++) EditorServer.receiveFile(p, tr("affixes/zz_scan_big"), piece, true);
            long ms = (System.nanoTime() - start) / 1_000_000;
            TarnishedRelics.LOGGER.info("[TRSCAN] 1000 pieces took {} ms", ms);
            helper.assertTrue(ms < 500, "1000 pieces of 8,000 characters took " + ms + " ms on the server thread");
            helper.succeed();
        } finally {
            EditorServer.cancel(p);
            TestUtil.remove(p);
        }
    }

    // an unidentified relic, as a chest would drop it
    private static net.minecraft.world.item.ItemStack unidentified() {
        TRData data = TRData.get();
        return com.flankrat.tarnishedrelics.relic.Roller.wrap(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD),
                data.rarities().values().iterator().next(), data.categories().values().iterator().next(),
                net.minecraft.util.RandomSource.create(1));
    }

    // #7: the real item inside an unidentified relic was sent to every client, so a tooltip or NBT-viewer mod showed
    // what it is before paying the smith; what goes over the network must not have it
    @GameTest(template = "gametest/empty")
    public static void theHiddenItemIsNotSentToClients(GameTestHelper helper) {
        net.minecraft.world.item.ItemStack relic = unidentified();
        helper.assertTrue(relic.getTag().contains("Hidden"), "test setup: no hidden item");
        net.minecraft.nbt.CompoundTag sent = relic.getShareTag();
        helper.assertTrue(sent != null && !sent.contains("Hidden"), "the hidden item is sent to clients: " + sent);
        helper.assertTrue(sent.contains("Rarity") && sent.contains("Category"), "the client lost what it shows: " + sent);
        helper.assertTrue(relic.getTag().contains("Hidden"), "sending stripped the server's own copy");
        helper.succeed();
    }

    // #7 guard: a creative player's client sends its copy of an item back when moving it around the inventory. That
    // copy has no hidden item, so the server must put it back, or moving a relic in creative emptied it
    @GameTest(template = "gametest/empty")
    public static void creativeInventoryKeepsTheHiddenItem(GameTestHelper helper) {
        ServerPlayer p = player(helper, 4);
        try {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            net.minecraft.world.item.ItemStack relic = unidentified();
            net.minecraft.world.item.ItemStack clientCopy = relic.copy();
            clientCopy.setTag(relic.getShareTag());   // what the client got (and sends back)
            p.connection.handleSetCreativeModeSlot(new net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket(36, clientCopy));
            net.minecraft.world.item.ItemStack placed = p.inventoryMenu.getSlot(36).getItem();
            helper.assertTrue(placed.hasTag() && placed.getTag().contains("Hidden"), "the relic placed in creative lost its hidden item");
            helper.assertTrue(com.flankrat.tarnishedrelics.relic.UnidentifiedItem.hidden(placed).is(net.minecraft.world.item.Items.IRON_SWORD),
                    "the wrong hidden item came back");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
