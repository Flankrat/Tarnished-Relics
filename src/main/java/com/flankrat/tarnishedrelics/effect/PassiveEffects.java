package com.flankrat.tarnishedrelics.effect;

import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Passive effects (kept up while the relic is held or worn) and "tick" functions, checked once a second per
 * player. Split out of {@link RelicEffects}.
 */
public final class PassiveEffects {

    private PassiveEffects() {
    }

    private static final String PASSIVE_KEY = "TarnishedRelicsPassive";
    private static final int PASSIVE_DURATION = 400;
    private static final int PASSIVE_REFRESH_BELOW = 220;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % 20 != 0) return;
        // the strongest amplifier each effect is given at (two items with the same effect: the stronger one counts)
        Map<MobEffect, Integer> wanted = new LinkedHashMap<>();
        for (RelicEffects.Active a : RelicEffects.active(player, Effect.Type.PASSIVE_EFFECT)) {
            if (!a.effect().conditions.test(player, null, player.getRandom())) continue;
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(a.effect().mobEffect);
            if (effect != null) wanted.merge(effect, a.effect().amplifier, Math::max);
        }
        ListTag given = new ListTag();
        wanted.forEach((effect, amp) -> {
            given.add(StringTag.valueOf(String.valueOf(ForgeRegistries.MOB_EFFECTS.getKey(effect))));
            keepUp(player, effect, amp);
        });
        // the long duration would outlast the item: take back effects we gave that nothing gives any more
        ListTag before = player.getPersistentData().getList(PASSIVE_KEY, Tag.TAG_STRING);
        for (int i = 0; i < before.size(); i++) {
            if (given.contains(before.get(i))) continue;
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(before.getString(i)));
            if (effect != null && isOurs(player.getEffect(effect))) player.removeEffect(effect);
        }
        if (given.isEmpty()) player.getPersistentData().remove(PASSIVE_KEY);
        else player.getPersistentData().put(PASSIVE_KEY, given);
        RelicEffects.runFunctions(player, null, "tick");
        setAttributes(player);
    }

    private static final String SET_ATTRIBUTES_KEY = "TarnishedRelicsSetAttributes";
    private static final String SET_MODIFIER = "Tarnished Relics set";

    /**
     * Set bonus attributes. Item attributes come with one item (ItemAttributeModifierEvent knows only that item), so a
     * set's go on the player here: a modifier per set effect while the set is complete, taken off (within a second)
     * when it isn't. Transient: never saved, so a set taken off while logged out leaves nothing behind. The
     * attribute|uuid of each one given is kept in the player's data to know what to take off.
     */
    private static void setAttributes(Player player) {
        ListTag given = new ListTag();
        for (SetBonuses.Worn w : SetBonuses.complete(SetBonuses.pieces(player))) {
            ResourceLocation rarity = RelicTag.rarity(RelicTag.get(w.main()));
            double roll = SetBonuses.roll(w.main());
            List<Effect> effects = w.legendary().setEffects();
            for (int i = 0; i < effects.size(); i++) {
                Effect e = effects.get(i);
                if (e.type != Effect.Type.ATTRIBUTE) continue;
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(e.attribute);
                AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
                if (instance == null) continue;   // an attribute from a mod that isn't installed, or players don't have
                // the same UUID for the same set effect, so it's replaced, never stacked
                UUID uuid = UUID.nameUUIDFromBytes(("tarnishedrelics:set:" + w.legendary().id() + ":" + i).getBytes(StandardCharsets.UTF_8));
                double value = e.value.get(rarity, roll);
                AttributeModifier now = instance.getModifier(uuid);
                if (now == null || now.getAmount() != value || now.getOperation() != e.operation) {
                    instance.removeModifier(uuid);
                    instance.addTransientModifier(new AttributeModifier(uuid, SET_MODIFIER, value, e.operation));
                }
                given.add(StringTag.valueOf(e.attribute + "|" + uuid));
            }
        }
        // take off the ones a set gave that it doesn't any more
        ListTag before = player.getPersistentData().getList(SET_ATTRIBUTES_KEY, Tag.TAG_STRING);
        for (int i = 0; i < before.size(); i++) {
            if (given.contains(before.get(i))) continue;
            String[] parts = before.getString(i).split("\\|");
            Attribute attribute = parts.length == 2 ? ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(parts[0])) : null;
            AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
            if (instance != null) instance.removeModifier(UUID.fromString(parts[1]));
        }
        if (given.isEmpty()) player.getPersistentData().remove(SET_ATTRIBUTES_KEY);
        else player.getPersistentData().put(SET_ATTRIBUTES_KEY, given);
    }

    /**
     * Gives a passive effect, or keeps it going. Topped up only when it runs low, not every second: night vision
     * flickers under 10 seconds, and effects that act on a timer (regeneration heals when the duration is a multiple
     * of 50) would act too often if the duration was reset all the time. Ambient and without particles so it
     * doesn't clutter the screen.
     * <ul>
     *   <li>weaker (a potion or an item taken off): replaced right away. Before, anything at the same or a lower
     *       amplifier blocked the change until it ran low, so a jump boost I potion kept jump boost II boots at I,
     *       and swapping to stronger boots took up to 9 seconds;</li>
     *   <li>stronger, but ours (a stronger item was taken off): replaced with this one right away, instead of
     *       lasting its full 20 seconds;</li>
     *   <li>stronger and not ours (a potion): left alone.</li>
     * </ul>
     */
    private static void keepUp(Player player, MobEffect effect, int amp) {
        MobEffectInstance now = player.getEffect(effect);
        boolean give = now == null || now.getAmplifier() < amp || (now.getAmplifier() == amp && now.getDuration() < PASSIVE_REFRESH_BELOW);
        if (now != null && now.getAmplifier() > amp && isOurs(now)) {
            player.removeEffect(effect);
            give = true;
        }
        // a weaker potion under it is kept by the game and comes back when this one ends
        if (give) player.addEffect(new MobEffectInstance(effect, PASSIVE_DURATION, amp, true, false, true));
    }

    // our kind of instance (ambient, no particles, not longer than we give), so a potion is never taken away
    private static boolean isOurs(@Nullable MobEffectInstance i) {
        return i != null && i.isAmbient() && !i.isVisible() && i.getDuration() <= PASSIVE_DURATION;
    }
}
