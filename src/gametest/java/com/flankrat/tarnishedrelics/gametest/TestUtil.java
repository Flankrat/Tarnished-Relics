package com.flankrat.tarnishedrelics.gametest;

import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Map;

/** Shared helpers for the in-world tests. */
final class TestUtil {

    private TestUtil() {
    }

    static ResourceLocation tr(String path) {
        return new ResourceLocation("tarnishedrelics", path);
    }

    /**
     * A survival test player standing in the test area. Remove it with {@link #remove} when done. Vanilla's mock
     * player has a connection with no channel, and Forge's packet sending (the mod syncs its data on login) needs
     * one, so this player's connection gets an in-memory channel that just keeps what's sent.
     */
    static ServerPlayer player(GameTestHelper helper) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);   // sets the connection's channel (channelActive)
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "trtest"));
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    /** Makes the player an op (level 4), like a server admin using the editor. */
    static void op(ServerPlayer p) {
        // PlayerList.op uses the server's op level, which is 0 on the test server
        p.server.getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4, false));
        p.server.getPlayerList().sendPlayerPermissionLevel(p);
    }

    static void remove(ServerPlayer p) {
        p.server.getPlayerList().deop(p.getGameProfile());
        p.server.getPlayerList().remove(p);
    }

    static BlockPos abs(GameTestHelper helper, int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y, z));
    }

    /** An identified relic as the roller stores it: this item, category, slot, and these affixes (id, roll). */
    static ItemStack relic(Item item, String rarity, String category, String slot, Object... affixRolls) {
        ItemStack stack = new ItemStack(item);
        CompoundTag relic = new CompoundTag();
        relic.putString("Rarity", "tarnishedrelics:" + rarity);
        relic.putString("Category", "tarnishedrelics:" + category);
        relic.putString("Slot", slot);
        relic.putInt("Color", 0xFFFFFF);
        ListTag affixes = new ListTag();
        for (int i = 0; i < affixRolls.length; i += 2) {
            CompoundTag a = new CompoundTag();
            a.putString("Id", (String) affixRolls[i]);
            a.putFloat("Roll", ((Number) affixRolls[i + 1]).floatValue());
            affixes.add(a);
        }
        relic.put("Affixes", affixes);
        stack.getOrCreateTag().put(RelicTag.KEY, relic);
        return stack;
    }

    /** The current data's files, to put back after a test swapped in its own. */
    static Map<ResourceLocation, JsonElement> currentFiles() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
        TRData.get().rawJson().forEach((id, s) -> files.put(id, JsonParser.parseString(s)));
        return files;
    }
}
