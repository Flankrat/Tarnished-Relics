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
 * in-game editor's packets (see EditorServer). Big data is split over several packets (see BUDGET).
 */
public final class TRNetwork {

    private static final String VERSION = "5";

    /**
     * Most data one packet carries. A server-to-client packet can be at most 1 MB (1048576 bytes) or the player is
     * disconnected, so the datapack files are split over as many packets as they need, each under this, and put
     * back together on the client. Can be lowered with -Dtarnishedrelics.packetBudget=<bytes> to test splitting.
     */
    static final int BUDGET = Integer.getInteger("tarnishedrelics.packetBudget", 900_000);
    private static final java.util.concurrent.atomic.AtomicInteger BATCHES = new java.util.concurrent.atomic.AtomicInteger();
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
        List<Map<ResourceLocation, String>> parts = split(TRData.get().rawJson(), BUDGET);
        int batch = BATCHES.incrementAndGet();
        for (int i = 0; i < parts.size(); i++) {
            SyncData packet = new SyncData(batch, i, parts.size(), parts.get(i));
            if (event.getPlayer() != null) CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
            else CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    /** Roughly how many bytes an entry takes in a packet (UTF-8 text, plus length prefixes). */
    private static int size(ResourceLocation id, String value) {
        return id.toString().length() + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 10;
    }

    /** Splits a map into parts of at most budget bytes each (an entry bigger than that gets a part to itself). */
    static List<Map<ResourceLocation, String>> split(Map<ResourceLocation, String> map, int budget) {
        List<Map<ResourceLocation, String>> parts = new java.util.ArrayList<>();
        Map<ResourceLocation, String> part = new LinkedHashMap<>();
        int used = 0;
        for (Map.Entry<ResourceLocation, String> e : map.entrySet()) {
            int n = size(e.getKey(), e.getValue());
            if (!part.isEmpty() && used + n > budget) {
                parts.add(part);
                part = new LinkedHashMap<>();
                used = 0;
            }
            part.put(e.getKey(), e.getValue());
            used += n;
        }
        parts.add(part);   // always at least one part, so an empty datapack still syncs
        return parts;
    }

    /**
     * Puts split packets back together on the client. Keeps only the newest batch: if a new one starts (another
     * /reload) before the old one is complete, the old parts are dropped.
     */
    static final class Assembler {
        private int batch = -1;
        private final Map<Integer, Object[]> parts = new java.util.HashMap<>();

        /** Adds a part; returns all parts in order once every one is in, else null. */
        @javax.annotation.Nullable
        synchronized List<Object[]> add(int batch, int index, int count, Object[] part) {
            if (batch != this.batch) {
                this.batch = batch;
                parts.clear();
            }
            parts.put(index, part);
            if (parts.size() < count) return null;
            List<Object[]> out = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) out.add(parts.get(i));
            parts.clear();
            this.batch = -1;
            return out;
        }
    }

    private static final Assembler SYNC_PARTS = new Assembler();
    private static final Assembler EDITOR_PARTS = new Assembler();

    /** One part of the datapack files (id -> JSON text): part index of count, all with the same batch number. */
    public record SyncData(int batch, int index, int count, Map<ResourceLocation, String> files) {

        static void encode(SyncData p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.batch);
            buf.writeVarInt(p.index);
            buf.writeVarInt(p.count);
            writeMap(buf, p.files);
        }

        static SyncData decode(FriendlyByteBuf buf) {
            return new SyncData(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readMap(buf));
        }

        @SuppressWarnings("unchecked")
        static void handle(SyncData p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                List<Object[]> all = SYNC_PARTS.add(p.batch, p.index, p.count, new Object[]{p.files});
                if (all == null) return;   // more parts to come
                Map<ResourceLocation, String> files = new LinkedHashMap<>();
                for (Object[] part : all) files.putAll((Map<ResourceLocation, String>) part[0]);
                TRData.loadFromSync(files);
            });
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

    /** Sends the editor's files and origins, split like the sync (half the budget for each map in a part). */
    public static void sendEditor(ServerPlayer player, Map<ResourceLocation, String> files, Map<ResourceLocation, String> origins, boolean refresh) {
        List<Map<ResourceLocation, String>> f = split(files, BUDGET / 2);
        List<Map<ResourceLocation, String>> o = split(origins, BUDGET / 2);
        int count = Math.max(f.size(), o.size());
        int batch = BATCHES.incrementAndGet();
        for (int i = 0; i < count; i++) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditor(batch, i, count,
                    i < f.size() ? f.get(i) : Map.of(), i < o.size() ? o.get(i) : Map.of(), refresh));
        }
    }

    /** Server -> op: one part of the editor's files and the packs they come from (refresh: keep the screen's state). */
    public record OpenEditor(int batch, int index, int count, Map<ResourceLocation, String> files, Map<ResourceLocation, String> origins,
                             boolean refresh) {

        static void encode(OpenEditor p, FriendlyByteBuf buf) {
            buf.writeVarInt(p.batch);
            buf.writeVarInt(p.index);
            buf.writeVarInt(p.count);
            writeMap(buf, p.files);
            writeMap(buf, p.origins);
            buf.writeBoolean(p.refresh);
        }

        static OpenEditor decode(FriendlyByteBuf buf) {
            return new OpenEditor(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readMap(buf), readMap(buf), buf.readBoolean());
        }

        @SuppressWarnings("unchecked")
        static void handle(OpenEditor p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                List<Object[]> all = EDITOR_PARTS.add(p.batch, p.index, p.count, new Object[]{p.files, p.origins});
                if (all == null) return;   // more parts to come
                Map<ResourceLocation, String> files = new LinkedHashMap<>();
                Map<ResourceLocation, String> origins = new LinkedHashMap<>();
                for (Object[] part : all) {
                    files.putAll((Map<ResourceLocation, String>) part[0]);
                    origins.putAll((Map<ResourceLocation, String>) part[1]);
                }
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.flankrat.tarnishedrelics.client.editor.EditorClient.open(files, origins, p.refresh));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Op -> server: one changed file ("" = removed). Sent one per packet, then EditorCommit. */
    public record EditorFile(ResourceLocation id, String json) {

        static void encode(EditorFile p, FriendlyByteBuf buf) {
            buf.writeResourceLocation(p.id);
            buf.writeUtf(p.json, 32767);
        }

        static EditorFile decode(FriendlyByteBuf buf) {
            return new EditorFile(buf.readResourceLocation(), buf.readUtf(32767));
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

    /** Op -> server: give me a relic of this rarity/category, or this legendary ("" = any), unidentified or identified. */
    public record EditorGive(String rarity, String category, String legendary, boolean identified) {

        static void encode(EditorGive p, FriendlyByteBuf buf) {
            buf.writeUtf(p.rarity);
            buf.writeUtf(p.category);
            buf.writeUtf(p.legendary);
            buf.writeBoolean(p.identified);
        }

        static EditorGive decode(FriendlyByteBuf buf) {
            return new EditorGive(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean());
        }

        static void handle(EditorGive p, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (player != null) EditorServer.give(player, p.rarity.isEmpty() ? null : ResourceLocation.tryParse(p.rarity),
                        p.category.isEmpty() ? null : ResourceLocation.tryParse(p.category),
                        p.legendary.isEmpty() ? null : ResourceLocation.tryParse(p.legendary), p.identified);
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
