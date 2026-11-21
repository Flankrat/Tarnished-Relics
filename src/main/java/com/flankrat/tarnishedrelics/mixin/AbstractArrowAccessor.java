package com.flankrat.tarnishedrelics.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The item an arrow-like projectile carries: a thrown trident's trident, a modded javelin's javelin (an arrow's is
 * the arrow item). Vanilla has it as the protected getPickupItem, so this opens it up for RelicEffects, which uses it
 * to let a thrown relic weapon hit with its own effects.
 */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {

    @Invoker("getPickupItem")
    ItemStack tarnishedrelics$pickupItem();
}
