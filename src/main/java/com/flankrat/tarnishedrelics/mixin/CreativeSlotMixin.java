package com.flankrat.tarnishedrelics.mixin;

import com.flankrat.tarnishedrelics.relic.HiddenItems;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creative player's client sends its own copy of an item when it's moved in the inventory. Clients aren't sent an
 * unidentified relic's real item (see HiddenItems), so the copy would replace the relic with an empty one: the real
 * item is put back first. Forge has no event for this packet.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotMixin {

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"))
    private void tarnishedrelics$restoreHiddenItem(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        HiddenItems.restore(packet.getItem());
    }
}
