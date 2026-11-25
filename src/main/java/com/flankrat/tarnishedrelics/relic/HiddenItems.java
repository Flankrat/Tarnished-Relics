package com.flankrat.tarnishedrelics.relic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Keeps an unidentified relic's real item off the network. What a client is sent has a key ("HiddenKey") in place of
 * the real item ("Hidden"); the server remembers key -> real item here. Clients never need the real item (the smith
 * identifies relics on the server), but a creative player's client sends its own copy of an item back whenever it's
 * moved in the inventory, and that copy only has the key: {@link #restore} puts the real item back into it.
 * <p>
 * The key is worked out from the real item, so copies of one relic share it. The table is filled every time the
 * server sends a relic, which always happens before a client can send it back, and keeps the most recent ones.
 */
public final class HiddenItems {

    private static final int MAX = 16_384;
    // key -> real item; accessed from the server thread and the network threads
    private static final Map<String, CompoundTag> KNOWN = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompoundTag> eldest) {
            return size() > MAX;
        }
    };

    private HiddenItems() {
    }

    /** The tag to send to clients: a copy without the real item, with its key instead. Null/other tags unchanged. */
    @Nullable
    public static CompoundTag forClient(@Nullable CompoundTag tag) {
        if (tag == null || !tag.contains("Hidden")) return tag;
        CompoundTag hidden = tag.getCompound("Hidden");
        String key = UUID.nameUUIDFromBytes(hidden.toString().getBytes(StandardCharsets.UTF_8)).toString();
        synchronized (KNOWN) {
            KNOWN.put(key, hidden.copy());
        }
        CompoundTag sent = tag.copy();
        sent.remove("Hidden");
        sent.putString("HiddenKey", key);
        return sent;
    }

    /** Puts the real item back into a relic a client sent (creative inventory). Unknown keys are left alone. */
    public static void restore(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.contains("Hidden") || !tag.contains("HiddenKey")) return;
        CompoundTag hidden;
        synchronized (KNOWN) {
            hidden = KNOWN.get(tag.getString("HiddenKey"));
        }
        if (hidden == null) return;
        tag.put("Hidden", hidden.copy());
        tag.remove("HiddenKey");
    }
}
