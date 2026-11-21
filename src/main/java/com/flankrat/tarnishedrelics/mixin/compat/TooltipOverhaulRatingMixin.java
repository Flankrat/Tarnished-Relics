package com.flankrat.tarnishedrelics.mixin.compat;

import com.flankrat.tarnishedrelics.client.RelicTooltips;
import dev.xylonity.tooltipoverhaul.client.render.TooltipContext;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tooltip Overhaul (optional, client only) shows a rating under the item's name, taken from the item's vanilla rarity
 * ("Common" for nearly every modded item). For an identified relic this shows the relic's own tier instead ("Unique"
 * in the tier's color). @Pseudo: skipped when Tooltip Overhaul isn't installed.
 */
@Pseudo
@Mixin(targets = "dev.xylonity.tooltipoverhaul.client.util.TextUtils", remap = false)
public abstract class TooltipOverhaulRatingMixin {

    @Inject(method = "getRatingText", at = @At("HEAD"), cancellable = true, require = 0)
    private static void tarnishedrelics$relicTier(TooltipContext ctx, CallbackInfoReturnable<Component> cir) {
        Component tier = RelicTooltips.tierName(ctx.getStack());
        if (tier != null) cir.setReturnValue(tier);
    }
}
