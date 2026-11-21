package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TRConfig;
import com.flankrat.tarnishedrelics.TRItems;
import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import com.flankrat.tarnishedrelics.relic.Roller;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.flankrat.tarnishedrelics.gametest.TestUtil.tr;

/**
 * Identifying at a blacksmith, as a player does it: use the relic on the villager (price), use it again within the
 * confirm window (pay). The event is posted on the real event bus, so the mod's own handler runs.
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class BlacksmithGameTests {

    private static Villager smith(GameTestHelper helper, VillagerProfession profession, int level, int x) {
        Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 1, 1));
        v.setNoAi(true);   // no brain: it keeps its profession without a workstation and stays put
        v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(level));
        v.setVillagerXp(10);
        return v;
    }

    private static ItemStack relic(String rarity) {
        return relic(rarity, "sword");
    }

    private static ItemStack relic(String rarity, String category) {
        return Roller.generate(RandomSource.create(rarity.hashCode()), TRData.get().rarities().get(tr(rarity)), TRData.get().categories().get(tr(category)));
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack relic, int emeralds) {
        ServerPlayer p = TestUtil.player(helper);
        p.setItemInHand(InteractionHand.MAIN_HAND, relic);
        if (emeralds > 0) p.getInventory().add(new ItemStack(Items.EMERALD, emeralds));
        return p;
    }

    /** Uses the held item on the villager; returns true if the event was cancelled (no trade screen). */
    private static boolean use(ServerPlayer p, Villager v) {
        return MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p, InteractionHand.MAIN_HAND, v));
    }

    private static int emeralds(ServerPlayer p) {
        return p.getInventory().countItem(Items.EMERALD);
    }

    private static boolean stillUnidentified(ServerPlayer p) {
        return p.getMainHandItem().is(TRItems.UNIDENTIFIED.get());
    }

    @GameTest(template = "gametest/empty")
    public static void priceThenConfirmIdentifies(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);
        ItemStack relic = relic("magic");
        int cost = relic.getTag().getInt("Cost"), xp = relic.getTag().getInt("Xp");
        ServerPlayer p = holding(helper, relic, 20);
        try {
            helper.assertTrue(use(p, v), "relic use opened the trade screen");
            helper.assertTrue(stillUnidentified(p) && emeralds(p) == 20, "the first use must only name the price");
            use(p, v);
            helper.assertFalse(stillUnidentified(p), "the second use didn't identify it");
            helper.assertTrue(RelicTag.isRelic(p.getMainHandItem()), "the real item isn't a relic: " + p.getMainHandItem().getTag());
            helper.assertTrue(emeralds(p) == 20 - cost, "emeralds " + emeralds(p) + ", cost " + cost);
            helper.assertTrue(v.getVillagerXp() == 10 + xp, "villager xp " + v.getVillagerXp() + ", expected " + (10 + xp));
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void tooLowLevelRefuses(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);   // legendary needs level 5
        ServerPlayer p = holding(helper, relic("legendary"), 64);
        try {
            use(p, v);
            use(p, v);
            helper.assertTrue(stillUnidentified(p) && emeralds(p) == 64, "a level 2 smith identified a legendary");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void notEnoughEmeraldsKeepsTheRelic(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);
        ServerPlayer p = holding(helper, relic("magic"), 1);   // magic costs 2-4
        try {
            use(p, v);
            use(p, v);
            helper.assertTrue(stillUnidentified(p) && emeralds(p) == 1, "identified without enough emeralds");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // Bug (round 8): a relic whose hidden item is from a mod that was removed since holds nothing (the item loads as
    // air). The smith took the emeralds and "identified" it into nothing, and /tarnishedrelics identify deleted it.
    @GameTest(template = "gametest/empty")
    public static void relicOfAMissingItemIsntIdentified(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);
        ItemStack relic = relic("magic");
        relic.getTag().getCompound("Hidden").putString("id", "notamod:gone_sword");
        ServerPlayer p = holding(helper, relic, 20);
        try {
            use(p, v);
            use(p, v);
            helper.assertTrue(stillUnidentified(p), "the smith turned the relic into nothing");
            helper.assertTrue(emeralds(p) == 20, "the smith took emeralds for nothing: " + emeralds(p) + " left");
            TestUtil.op(p);
            p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "tarnishedrelics identify");
            helper.assertTrue(stillUnidentified(p), "/tarnishedrelics identify turned the relic into nothing");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    @GameTest(template = "gametest/empty")
    public static void nonSmithRefusesAndNoTradeScreen(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.FARMER, 5, 1);
        ServerPlayer p = holding(helper, relic("magic"), 64);
        try {
            helper.assertTrue(use(p, v), "relic use on a farmer opened the trade screen");
            use(p, v);
            helper.assertTrue(stillUnidentified(p) && emeralds(p) == 64, "a farmer identified a relic");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // the price named by one smith isn't accepted by another
    @GameTest(template = "gametest/empty")
    public static void confirmIsPerVillager(GameTestHelper helper) {
        Villager a = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);
        Villager b = smith(helper, VillagerProfession.WEAPONSMITH, 2, 3);
        ServerPlayer p = holding(helper, relic("magic"), 64);
        try {
            use(p, a);
            use(p, b);
            helper.assertTrue(stillUnidentified(p) && emeralds(p) == 64, "another smith took the first one's price");
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // after the confirm window the next use names the price again instead of paying
    @GameTest(template = "gametest/empty", timeoutTicks = 400)
    public static void confirmWindowExpires(GameTestHelper helper) {
        Villager v = smith(helper, VillagerProfession.WEAPONSMITH, 2, 1);
        ServerPlayer p = holding(helper, relic("magic"), 64);
        use(p, v);
        helper.runAfterDelay(TRConfig.CONFIRM_SECONDS.get() * 20L + 5, () -> {
            try {
                use(p, v);
                helper.assertTrue(stillUnidentified(p) && emeralds(p) == 64, "paid after the confirm window ran out");
                use(p, v);   // a fresh price was named, so this one pays
                helper.assertFalse(stillUnidentified(p), "the renewed price wasn't accepted");
                helper.succeed();
            } finally {
                TestUtil.remove(p);
            }
        });
    }

    // ---- which smith identifies what ----

    /** True if this kind of villager identifies a magic relic of this category (price, then pay). */
    private static boolean identifies(GameTestHelper helper, VillagerProfession profession, ItemStack relic, int x) {
        Villager v = smith(helper, profession, 5, x);
        ServerPlayer p = holding(helper, relic, 64);
        try {
            use(p, v);
            use(p, v);
            return !stillUnidentified(p);
        } finally {
            TestUtil.remove(p);
            v.discard();
        }
    }

    private static void only(GameTestHelper helper, String category, VillagerProfession... yes) {
        List<VillagerProfession> all = List.of(VillagerProfession.TOOLSMITH, VillagerProfession.ARMORER, VillagerProfession.WEAPONSMITH,
                VillagerProfession.FLETCHER, VillagerProfession.FARMER);
        int x = 0;
        for (VillagerProfession prof : all) {
            boolean expected = List.of(yes).contains(prof);
            // rare: magic only makes level 1-2 items, and a trident is level 3
            ItemStack relic = relic("rare", category);
            helper.assertTrue(relic.is(TRItems.UNIDENTIFIED.get()) && relic.getTag().getString("Category").equals("tarnishedrelics:" + category),
                    "couldn't make a " + category + " relic: " + relic.getTag());
            boolean did = identifies(helper, prof, relic, x++ % 5);
            helper.assertTrue(did == expected, category + " relic at a " + prof + ": " + (did ? "identified" : "refused") + ", expected " + (expected ? "identified" : "refused"));
        }
    }

    // tools need a toolsmith, armor an armorer, weapons a weaponsmith, bows a fletcher; an axe is a tool and a weapon
    @GameTest(template = "gametest/empty")
    public static void toolsNeedAToolsmith(GameTestHelper helper) {
        for (String c : new String[]{"pickaxe", "shovel", "hoe"}) only(helper, c, VillagerProfession.TOOLSMITH);
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void armorNeedsAnArmorer(GameTestHelper helper) {
        for (String c : new String[]{"helmet", "chestplate", "leggings", "boots", "shield"}) only(helper, c, VillagerProfession.ARMORER);
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void weaponsNeedAWeaponsmith(GameTestHelper helper) {
        for (String c : new String[]{"sword", "trident"}) only(helper, c, VillagerProfession.WEAPONSMITH);
        helper.succeed();
    }

    // bows and crossbows (longbows and heavy crossbows from mods are in these categories) need a fletcher
    @GameTest(template = "gametest/empty")
    public static void bowsNeedAFletcher(GameTestHelper helper) {
        for (String c : new String[]{"bow", "crossbow"}) only(helper, c, VillagerProfession.FLETCHER);
        helper.succeed();
    }

    @GameTest(template = "gametest/empty")
    public static void axesTakeEitherSmith(GameTestHelper helper) {
        only(helper, "axe", VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH);
        helper.succeed();
    }

    // a relic whose category isn't there any more (removed from the datapack) still goes to any blacksmith
    @GameTest(template = "gametest/empty")
    public static void unknownCategoryTakesAnyBlacksmith(GameTestHelper helper) {
        ItemStack relic = relic("magic", "pickaxe");
        relic.getTag().putString("Category", "somepack:gone");
        helper.assertTrue(identifies(helper, VillagerProfession.ARMORER, relic.copy(), 0), "an armorer refused a relic of a removed category");
        helper.assertFalse(identifies(helper, VillagerProfession.FARMER, relic.copy(), 1), "a farmer identified a relic");
        helper.succeed();
    }
}
