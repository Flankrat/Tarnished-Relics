package com.flankrat.tarnishedrelics.mixin;

import com.flankrat.tarnishedrelics.effect.RelicEffects;
import com.flankrat.tarnishedrelics.name.NameBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two hooks on every ItemStack (both do nothing for items that aren't identified relics):
 * <ul>
 *   <li>getHoverName: the relic's built name (quality, prefixes, base name, suffix) unless a player renamed it.
 *       Vanilla has no event for item names, so this needs a mixin.</li>
 *   <li>hurt: "durability" affixes change how much wear the item takes. Forge 1.20.1 has no event for item wear.</li>
 * </ul>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow
    public abstract boolean hasCustomHoverName();

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void tarnishedrelics$relicName(CallbackInfoReturnable<Component> cir) {
        if (hasCustomHoverName()) return;
        Component built = NameBuilder.build((ItemStack) (Object) this, cir.getReturnValue());
        if (built != null) cir.setReturnValue(built);
    }

    @ModifyVariable(method = "hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int tarnishedrelics$durability(int amount, int unused, RandomSource random, ServerPlayer player) {
        return RelicEffects.adjustWear((ItemStack) (Object) this, amount, random);
    }
}
