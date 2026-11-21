package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.Roller;
import com.flankrat.tarnishedrelics.relic.UnidentifiedItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.TreeMap;

/**
 * Relics with other mods that change enchantments, checked in a real modpack: each test passes when its mod isn't
 * installed, so the normal dev run passes. Run them in a modpack with ./gradlew gametestJar (see the README's Testing
 * section).
 * <ul>
 *   <li>jlme: generated legendaries' signature enchantments with that mod's enchantments installed.</li>
 * </ul>
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class CompatGameTests {

    private static final String BATCH = "compat";

    private static boolean has(String mod) {
        return ModList.get().isLoaded(mod);
    }

    // ---- jlme ----

    // Generated legendaries pick signature enchantments from every installed enchantment that fits the item. The
    // mod hides some of its enchantments from loot (isDiscoverable false: found another way); those must not be
    // picked, and every pick must be one the item can take (the mod's restrictions are part of canEnchant).
    @GameTest(template = "gametest/empty", batch = BATCH, timeoutTicks = 400)
    public static void jlmeLegendaryEnchantmentsAreLootable(GameTestHelper helper) {
        if (!has("jlme")) {
            helper.succeed();
            return;
        }
        TRData data = TRData.get();
        Rarity legendary = data.rarities().values().stream().filter(Rarity::legendary).findFirst().orElse(null);
        helper.assertTrue(legendary != null, "no legendary rarity");
        RandomSource random = RandomSource.create(42);
        Map<String, Integer> hidden = new TreeMap<>(), unfit = new TreeMap<>(), modded = new TreeMap<>();
        int made = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack relic : Roller.generateAll(random, legendary, null, null, RelicGeneratedEvent.Source.COMMAND, null)) {
                ItemStack real = UnidentifiedItem.hidden(relic);
                var tag = com.flankrat.tarnishedrelics.relic.RelicTag.get(real);
                if (tag == null || !tag.contains("LegendaryName")) continue;
                made++;
                ItemStack fresh = new ItemStack(real.getItem());
                // some mods put their own enchantments on every new stack of their items (BetterNether's ruby tools):
                // those come with the item, they aren't picks
                var builtIn = EnchantmentHelper.getEnchantments(fresh).keySet();
                for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(real).entrySet()) {
                    if (builtIn.contains(e.getKey())) continue;
                    String id = String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(e.getKey()));
                    if (id.startsWith("jlme:")) modded.merge(id + " " + e.getValue() + "/" + e.getKey().getMaxLevel(), 1, Integer::sum);
                    if (!e.getKey().isDiscoverable()) hidden.merge(id + " on " + ForgeRegistries.ITEMS.getKey(real.getItem()), 1, Integer::sum);
                    if (!e.getKey().canEnchant(fresh)) unfit.merge(id + " on " + ForgeRegistries.ITEMS.getKey(real.getItem()), 1, Integer::sum);
                }
            }
        }
        TarnishedRelics.LOGGER.info("[Tarnished Relics test] {} legendaries; the mod's enchantments as level/max: {}", made, modded);
        helper.assertTrue(made > 100, "only " + made + " legendaries made");
        helper.assertTrue(hidden.isEmpty(), "enchantments hidden from loot on legendaries: " + hidden);
        helper.assertTrue(unfit.isEmpty(), "enchantments the item can't take on legendaries: " + unfit);
        helper.succeed();
    }

}
