package com.flankrat.tarnishedrelics;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nullable;

import java.util.List;

/** config/tarnishedrelics-common.toml */
public final class TRConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> LOOT_TYPES;
    public static final ForgeConfigSpec.BooleanValue ENTITY_NEEDS_PLAYER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDE_TABLES;
    public static final ForgeConfigSpec.IntValue CONFIRM_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SHOW_RARITY_COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("loot");
        ENABLED = b.comment("Relics can turn up in loot.").define("enabled", true);
        CHANCE = b.comment("Chance that one unidentified relic is added each time loot of the kinds below is generated (each chest opened, ...).")
                .defineInRange("chance", 0.03, 0.0, 1.0);
        LOOT_TYPES = b.comment("Which kinds of loot can get one: chest, entity (mob drops), fishing, archaeology, gift, barter.",
                        "Chests only by default: mob drops are rolled on every kill, so even a few percent there adds up fast.")
                .defineListAllowEmpty(List.of("lootTypes"), () -> List.of("chest"), o -> o instanceof String);
        ENTITY_NEEDS_PLAYER = b.comment("Mob drops only get relics when a player made the kill (stops mob farms).")
                .define("entityNeedsPlayerKill", true);
        EXCLUDE_TABLES = b.comment("Loot tables that never get relics, e.g. \"minecraft:chests/spawn_bonus_chest\". \"*\" works as a wildcard.")
                .defineListAllowEmpty(List.of("excludeTables"), () -> List.of("minecraft:chests/spawn_bonus_chest"), o -> o instanceof String);
        b.pop();

        b.push("identify");
        CONFIRM_SECONDS = b.comment("After a blacksmith names the price, how long you have to use the item on them again to pay.")
                .defineInRange("confirmSeconds", 10, 1, 120);
        b.pop();

        b.push("display");
        SHOW_RARITY_COLOR = b.comment("Unidentified items show their rarity's color (a hint of the tier). Off: plain white until identified.")
                .define("showRarityColorUnidentified", false);
        b.pop();
        SPEC = b.build();
    }

    // the loaded config file (set when Forge loads it), for ConfigFile.write
    @Nullable
    private static volatile ModConfig loaded;

    private TRConfig() {
    }

    /** Mod event bus: remembers the loaded config file. */
    public static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) loaded = event.getConfig();
    }

    @Nullable
    public static ModConfig loaded() {
        return loaded;
    }
}
