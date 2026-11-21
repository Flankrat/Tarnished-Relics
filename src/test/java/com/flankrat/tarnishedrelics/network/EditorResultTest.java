package com.flankrat.tarnishedrelics.network;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.data.TRData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The editor's save result packet, with the errors a broken file really gives. */
class EditorResultTest {

    // Bug (round 3): an error over 4096 characters (here a long pasted line in a rarity's items) made the result
    // packet fail to encode, so the op's editor never heard back and stayed on "Saving..." forever.
    @Test
    void longErrorStillEncodes() {
        Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>(BuiltInData.files());
        String pasted = "minecraft:iron_sword minecraft:diamond_sword ".repeat(120);
        files.put(new ResourceLocation("testpack", "rarities/pasted"), JsonParser.parseString("{\"items\": [\"" + pasted + "\"]}"));
        List<String> errors = TRData.parseOnly(files).errors();
        assertTrue(errors.get(0).length() > 4096, "the error isn't long: " + errors.get(0).length());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        assertDoesNotThrow(() -> TRNetwork.EditorResult.encode(new TRNetwork.EditorResult(false, errors), buf));
        TRNetwork.EditorResult back = TRNetwork.EditorResult.decode(buf);
        assertTrue(back.errors().get(0).startsWith("testpack:rarities/pasted: bad item entry"), back.errors().get(0));
    }
}
