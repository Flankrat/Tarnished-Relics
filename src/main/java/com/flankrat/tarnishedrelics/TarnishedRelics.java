package com.flankrat.tarnishedrelics;

import com.flankrat.tarnishedrelics.client.RelicTooltips;
import com.flankrat.tarnishedrelics.client.TRClient;
import com.flankrat.tarnishedrelics.command.TRCommands;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.data.TRDataLoader;
import com.flankrat.tarnishedrelics.editor.ModpackPack;
import com.flankrat.tarnishedrelics.effect.RelicEffects;
import com.flankrat.tarnishedrelics.network.TRNetwork;
import com.flankrat.tarnishedrelics.smith.Blacksmith;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Tarnished Relics: rare unidentified weapons, armor and tools in loot, identified by blacksmith villagers into items
 * with random affixes and names. All content (categories, rarities, affixes, names) comes from datapacks under
 * data/&lt;namespace&gt;/tarnished_relics/. See the README for the formats. The relic item isn't in a creative tab:
 * an empty one has nothing inside, so use /tarnishedrelics give.
 */
@Mod(TarnishedRelics.MODID)
public final class TarnishedRelics {

    public static final String MODID = "tarnishedrelics";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TarnishedRelics() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TRConfig.SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        TRItems.register(modBus);
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(TRNetwork::register));
        modBus.addListener(ModpackPack::onAddPackFinders);

        IEventBus forge = MinecraftForge.EVENT_BUS;
        forge.addListener((AddReloadListenerEvent e) -> e.addListener(new TRDataLoader()));
        forge.addListener((TagsUpdatedEvent e) -> TRData.get().clearCaches());   // item tags changed: re-resolve item lists
        forge.register(RelicEffects.class);
        forge.register(Blacksmith.class);
        forge.register(TRCommands.class);
        forge.register(TRNetwork.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TRClient.register(modBus);
            forge.register(RelicTooltips.class);
        }
    }
}
