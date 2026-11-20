package com.flankrat.tarnishedrelics.editor;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A datapack folder in the config folder, {@code config/tarnishedrelics/datapack}, that every world loads. The
 * editor's "Whole modpack" saves go here, so a modpack ships its Tarnished Relics changes with its config folder:
 * no KubeJS or datapack-loader mod needed. It's a normal datapack ({@code data/<namespace>/tarnished_relics/...},
 * tags under {@code data/<namespace>/tags/...}), so files can also be put there by hand.
 * <p>
 * Order: above mods and other packs when a world is made, below the world's own editor pack (which the editor moves
 * to the top), so a world can still change what the modpack set.
 */
public final class ModpackPack {

    public static final String ID = "tarnishedrelics:modpack";

    private ModpackPack() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("tarnishedrelics").resolve("datapack");
    }

    /** Makes the folder and its pack.mcmeta if missing. */
    public static void ensure() throws IOException {
        Path dir = folder();
        Files.createDirectories(dir.resolve("data"));
        Path meta = dir.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Tarnished Relics settings for the whole modpack (saved by the in-game editor)\"\n  }\n}\n");
        }
    }

    // mod event bus
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        try {
            ensure();
        } catch (IOException e) {
            TarnishedRelics.LOGGER.error("[Tarnished Relics] couldn't make {}", folder(), e);
            return;
        }
        event.addRepositorySource(add -> {
            // required: on in every world, also ones made before this folder existed
            Pack pack = Pack.readMetaAndCreate(ID, Component.literal("Tarnished Relics (modpack)"), true,
                    id -> new PathPackResources(id, folder(), false), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack != null) add.accept(pack);
        });
    }
}
