package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.api.RelicGeneratedEvent;
import com.flankrat.tarnishedrelics.data.Rarity;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
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
 *   <li>jlme: generated legendaries' signature enchantments with that mod's enchantments installed, and lifesteal
 *       with its protection enchantments (they change the damage after armor).</li>
 * </ul>
 */
@GameTestHolder(TarnishedRelics.MODID)
@PrefixGameTestTemplate(false)
public class CompatGameTests {

    private static final String BATCH = "compat";
    private static Map<ResourceLocation, JsonElement> original;

    // a test affix: 50% lifesteal
    @BeforeBatch(batch = BATCH)
    public static void load(ServerLevel level) {
        original = TestUtil.currentFiles();
        Map<ResourceLocation, JsonElement> files = TestUtil.currentFiles();
        files.put(new ResourceLocation("trtest", "affixes/steal"), JsonParser.parseString(
                "{\"pool\": \"trtest\", \"weight\": 0, \"effects\": [{\"type\": \"lifesteal\", \"value\": 0.5}]}"));
        TRData.load(files);
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        TRData.load(original);
    }

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

    // JLME's protection enchantments lower the damage after armor (in LivingDamageEvent). Lifesteal heals from the
    // damage the hit really did, after them: a real attack on a zombie in armor with Advanced Protection heals half of
    // the health the zombie lost, not half of the damage before the enchantment.
    @GameTest(template = "gametest/empty", batch = BATCH)
    public static void jlmeLifestealHealsFromTheProtectedDamage(GameTestHelper helper) {
        var protection = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("jlme", "advanced_protection"));
        if (protection == null) {
            helper.succeed();
            return;
        }
        ServerPlayer p = TestUtil.player(helper);
        try {
            Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
            z.setNoAi(true);
            plain(z);
            ItemStack armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
            armor.enchant(protection, protection.getMaxLevel());
            z.setItemSlot(EquipmentSlot.CHEST, armor);
            // a stone sword: packs often lock better weapons behind levels, and a locked one doesn't attack
            p.setItemInHand(InteractionHand.MAIN_HAND, TestUtil.relic(Items.STONE_SWORD, "rare", "sword", "mainhand", "trtest:steal", 0.5));
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(LivingEntity.class, p, 1000, "f_20922_");   // fully charged
            // what lifesteal heals, from the heal event (a health mod like First Aid keeps the player's health itself)
            float[] healed = {0};
            java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHealEvent> heals = e -> {
                if (e.getEntity() == p) healed[0] += e.getAmount();
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST, false,
                    net.minecraftforge.event.entity.living.LivingHealEvent.class, heals);
            float before = z.getHealth();
            try {
                p.attack(z);
            } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(heals);
            }
            float dealt = before - z.getHealth();
            TarnishedRelics.LOGGER.info("[Tarnished Relics test] JLME protected hit: zombie lost {}, lifesteal healed {}", dealt, healed[0]);
            helper.assertTrue(dealt > 0, "the attack did no damage");
            helper.assertTrue(Math.abs(healed[0] - dealt * 0.5F) < 1e-3, "50% lifesteal healed " + healed[0] + " for a hit that took " + dealt);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }

    // not an Infernal Mobs elite (1 in 20 mobs is one in some packs; their modifiers change hits: Sticky steals the
    // attacker's weapon)
    private static void plain(LivingEntity e) {
        try {
            Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore").getMethod("removeEntFromElites", LivingEntity.class).invoke(null, e);
        } catch (ReflectiveOperationException ignored) {
            // Infernal Mobs isn't installed
        }
    }

    // Mods' undead and arthropods count for "vs undead / arthropods" bonuses (of Smiting, of the Bane) like vanilla's:
    // the game's own mob type (the one Smite and Bane of Arthropods use) counts, not only the tag, which lists vanilla's
    // mobs. Every mod mob in the pack that is undead or an arthropod, hit by a sword of Smiting / of the Bane, takes
    // more damage than 10.
    @GameTest(template = "gametest/empty", batch = BATCH, timeoutTicks = 400)
    public static void moddedUndeadAndArthropodsCount(GameTestHelper helper) {
        ServerPlayer p = TestUtil.player(helper);
        StringBuilder missed = new StringBuilder();
        int checked = 0;
        try {
            ItemStack smiting = TestUtil.relic(Items.STONE_SWORD, "rare", "sword", "mainhand", "tarnishedrelics:of_smiting", 0.5);
            ItemStack bane = TestUtil.relic(Items.STONE_SWORD, "rare", "sword", "mainhand", "tarnishedrelics:of_the_bane", 0.5);
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
                if (id == null || id.getNamespace().equals("minecraft")) continue;
                LivingEntity mob;
                try {
                    if (!(type.create(helper.getLevel()) instanceof LivingEntity l)) continue;
                    mob = l;
                } catch (Throwable t) {
                    continue;   // some mods' mobs can't be made outside their own spawning
                }
                var kind = mob.getMobType();
                ItemStack sword = kind == net.minecraft.world.entity.MobType.UNDEAD ? smiting : kind == net.minecraft.world.entity.MobType.ARTHROPOD ? bane : null;
                if (sword == null) continue;
                checked++;
                p.setItemInHand(InteractionHand.MAIN_HAND, sword);
                var hurt = new net.minecraftforge.event.entity.living.LivingHurtEvent(mob, p.damageSources().playerAttack(p), 10);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(hurt);
                if (hurt.getAmount() <= 10.001F && missed.length() < 1500) missed.append(id).append(" (").append(kind == net.minecraft.world.entity.MobType.UNDEAD ? "undead" : "arthropod").append("); ");
            }
            TarnishedRelics.LOGGER.info("[Tarnished Relics test] modded undead/arthropods checked: {}", checked);
            helper.assertTrue(missed.isEmpty(), "mod mobs that didn't take the bonus: " + missed);
            helper.succeed();
        } finally {
            TestUtil.remove(p);
        }
    }
}
