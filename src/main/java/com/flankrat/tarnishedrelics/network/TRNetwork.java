package com.flankrat.tarnishedrelics.network;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sends the datapack's Tarnished Relics files to clients when they join and after /reload, so tooltips and item
 * stats (attribute modifiers are also worked out on the client) use the server's affixes.
 */
public final class TRNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarnishedRelics.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private TRNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, SyncData.class, SyncData::encode, SyncData::decode, SyncData::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
}
