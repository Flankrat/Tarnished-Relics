package com.flankrat.tarnishedrelics.network;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.editor.EditorServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sends the datapack's Tarnished Relics files to clients when they join and after /reload, so tooltips and item
 * stats (attribute modifiers are also worked out on the client) use the server's affixes. Also carries the
 * in-game editor's packets (see EditorServer).
 */
public final class TRNetwork {

    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarnishedRelics.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private TRNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, SyncData.class, SyncData::encode, SyncData::decode, SyncData::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, OpenEditor.class, OpenEditor::encode, OpenEditor::decode, OpenEditor::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, EditorFile.class, EditorFile::encode, EditorFile::decode, EditorFile::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, EditorCommit.class, EditorCommit::encode, EditorCommit::decode, EditorCommit::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, EditorResult.class, EditorResult::encode, EditorResult::decode, EditorResult::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, EditorGive.class, EditorGive::encode, EditorGive::decode, EditorGive::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    // joining (player set) or /reload (player null = everyone)
    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent event) {
        SyncData packet = new SyncData(TRData.get().rawJson());
        if (event.getPlayer() != null) CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        else CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /** All files as id -> JSON text. */
    public record SyncData(Map<ResourceLocation, String> files) {

        static void encode(SyncData p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.files.size());
            p.files.forEach((id, json) -> {
                buf.writeResourceLocation(id);
                buf.writeUtf(json, 262144);
            });
        }

        static SyncData decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<ResourceLocation, String> files = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) files.put(buf.readResourceLocation(), buf.readUtf(262144));
            return new SyncData(files);
        }

        static void handle(SyncData p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> TRData.loadFromSync(p.files));
            ctx.get().setPacketHandled(true);
        }
    }

    private static void writeMap(FriendlyByteBuf buf, Map<ResourceLocation, String> map) {
        buf.writeVarInt(map.size());
        map.forEach((id, v) -> {
            buf.writeResourceLocation(id);
            buf.writeUtf(v, 262144);
        });
    }

    private static Map<ResourceLocation, String> readMap(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<ResourceLocation, String> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readResourceLocation(), buf.readUtf(262144));
        return map;
    }

    /** Server -> op: open the editor with every file and the pack each comes from (refresh: keep the screen's state). */
    public record OpenEditor(Map<ResourceLocation, String> files, Map<ResourceLocation, String> origins, boolean refresh) {

        static void encode(OpenEditor p, FriendlyByteBuf buf) {
            writeMap(buf, p.files);
            writeMap(buf, p.origins);
            buf.writeBoolean(p.refresh);
        }

        static OpenEditor decode(FriendlyByteBuf buf) {
            return new OpenEditor(readMap(buf), readMap(buf), buf.readBoolean());
        }

        static void handle(OpenEditor p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.flankrat.tarnishedrelics.client.editor.EditorClient.open(p.files, p.origins, p.refresh)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Op -> server: one changed file ("" = removed). Sent one per packet, then EditorCommit. */
    public record EditorFile(ResourceLocation id, String json) {

        static void encode(EditorFile p, FriendlyByteBuf buf) {
            buf.writeResourceLocation(p.id);
            buf.writeUtf(p.json, 32000);
        }

        static EditorFile decode(FriendlyByteBuf buf) {
            return new EditorFile(buf.readResourceLocation(), buf.readUtf(32000));
        }

        static void handle(EditorFile p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) EditorServer.receiveFile(player, p.id, p.json);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Op -> server: check and save the files sent (save = false: forget them), to the world or the whole modpack. */
    public record EditorCommit(boolean save, boolean modpack) {

        static void encode(EditorCommit p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.save);
            buf.writeBoolean(p.modpack);
        }

        static EditorCommit decode(FriendlyByteBuf buf) {
            return new EditorCommit(buf.readBoolean(), buf.readBoolean());
        }

        static void handle(EditorCommit p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player == null) return;
                if (p.save) EditorServer.commit(player, p.modpack);
                else EditorServer.cancel(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Op -> server: give me a relic of this rarity/category ("" = any), unidentified or identified. */
    public record EditorGive(String rarity, String category, boolean identified) {

        static void encode(EditorGive p, FriendlyByteBuf buf) {
            buf.writeUtf(p.rarity);
            buf.writeUtf(p.category);
            buf.writeBoolean(p.identified);
        }

        static EditorGive decode(FriendlyByteBuf buf) {
            return new EditorGive(buf.readUtf(), buf.readUtf(), buf.readBoolean());
        }

        static void handle(EditorGive p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) EditorServer.give(player, p.rarity.isEmpty() ? null : ResourceLocation.tryParse(p.rarity),
                        p.category.isEmpty() ? null : ResourceLocation.tryParse(p.category), p.identified);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Server -> op: whether the save went through, and the errors (in the changes, or left in the data after). */
    public record EditorResult(boolean saved, List<String> errors) {

        static void encode(EditorResult p, FriendlyByteBuf buf) {
            buf.writeBoolean(p.saved);
            buf.writeCollection(p.errors, (b, s) -> b.writeUtf(s, 4096));
        }

        static EditorResult decode(FriendlyByteBuf buf) {
            return new EditorResult(buf.readBoolean(), buf.readList(b -> b.readUtf(4096)));
        }

        static void handle(EditorResult p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.flankrat.tarnishedrelics.client.editor.EditorClient.result(p.saved, p.errors)));
            ctx.get().setPacketHandled(true);
        }
    }
}
