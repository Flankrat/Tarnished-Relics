package com.flankrat.tarnishedrelics.effect;

import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.IdOrTag;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Makes relic affixes do things. An affix only works while its item is in the slot its category names (a sword in the
 * main hand, a helmet on the head...), or the slot an effect names with "slot". Numbers come from the current datapack and the roll stored on the item.
 */
public final class RelicEffects {

    private RelicEffects() {
    }

    /** One working effect: its definition, its rolled value, and the item it's on. */
    record Active(Effect effect, double value, ItemStack stack) {
    }

    /** Every working effect of the given type on everything this entity holds or wears. */
    static List<Active> active(LivingEntity entity, Effect.Type type) {
        List<Active> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            collect(stack, slot, type, out);
        }
        return out;
    }

    private static void collect(ItemStack stack, @Nullable EquipmentSlot slot, Effect.Type type, List<Active> out) {
        CompoundTag relic = RelicTag.get(stack);
        if (relic == null) return;
        EquipmentSlot own = RelicTag.slot(relic);
        ResourceLocation rarity = RelicTag.rarity(relic);
        for (RelicTag.Rolled rolled : RelicTag.affixes(relic)) {
            for (Effect e : rolled.affix().effects()) {
                // only in its own slot (or the slot the effect names, e.g. a dagger that helps from the offhand)
                if (slot != null && (e.slot != null ? e.slot : own) != slot) continue;
                if (e.type == type) out.add(new Active(e, e.value.get(rarity, rolled.roll()), stack));
            }
        }
    }

    // ---- attributes (static stats, shown in the item tooltip like normal ones) ----

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        CompoundTag relic = RelicTag.get(event.getItemStack());
        if (relic == null) return;
        EquipmentSlot own = RelicTag.slot(relic);
        ResourceLocation rarity = RelicTag.rarity(relic);
        for (RelicTag.Rolled rolled : RelicTag.affixes(relic)) {
            List<Effect> effects = rolled.affix().effects();
            for (int i = 0; i < effects.size(); i++) {
                Effect e = effects.get(i);
                if (e.type != Effect.Type.ATTRIBUTE) continue;
                if ((e.slot != null ? e.slot : own) != event.getSlotType()) continue;
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(e.attribute);
                if (attribute == null) continue;   // attribute from a mod that isn't installed
                // Same UUID every time for the same affix/effect/slot, so modifiers replace instead of stacking up
                UUID uuid = UUID.nameUUIDFromBytes(("tarnishedrelics:" + rolled.affix().id() + ":" + i + ":" + event.getSlotType().getName())
                        .getBytes(StandardCharsets.UTF_8));
                event.addModifier(attribute, new AttributeModifier(uuid, "Tarnished Relics", e.value.get(rarity, rolled.roll()), e.operation));
            }
        }
    }

    // ---- damage dealt and taken, on-hit and on-hurt effects ----

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        LivingEntity attacker = source.getEntity() instanceof LivingEntity l ? l : null;
        RandomSource random = victim.getRandom();
        float amount = event.getAmount();

        if (attacker != null) {
            // damage_vs: +/- % damage against matching targets
            double bonus = 0;
            for (Active a : active(attacker, Effect.Type.DAMAGE_VS)) {
                var holder = victim.getType().builtInRegistryHolder();
                if ((a.effect.targets.isEmpty() || a.effect.targets.stream().anyMatch(t -> t.matches(holder))) && a.effect.conditions.test(attacker, victim, random)) bonus += a.value;
            }
            amount *= (float) Math.max(0, 1 + bonus);
        }

        // damage_taken: +/- % damage from matching damage types (armor and held items of the victim)
        double taken = 0;
        for (Active a : active(victim, Effect.Type.DAMAGE_TAKEN)) {
            boolean typeOk = a.effect.damageTypes.isEmpty() || a.effect.damageTypes.stream().anyMatch(t -> matchesDamage(t, source));
            if (typeOk && a.effect.conditions.test(victim, attacker, random)) taken += a.value;
        }
        amount *= (float) Math.max(0, 1 + taken);
        event.setAmount(amount);

        // On-hit effects only for melee hits (not arrows, explosions or thorns)
        if (attacker != null && isMelee(source, attacker)) {
            for (Active a : active(attacker, Effect.Type.ON_HIT_EFFECT)) {
                if (!a.effect.conditions.test(attacker, victim, random)) continue;
                applyEffect(a.effect, a.effect.onSelf ? attacker : victim);
            }
            for (Active a : active(attacker, Effect.Type.ON_HIT_FIRE)) {
                if (a.effect.conditions.test(attacker, victim, random)) victim.setSecondsOnFire((int) Math.round(a.value));
            }
            runFunctions(attacker, victim, "hit");
        }

        // On-hurt effects of the victim's gear (not for thorns damage, so two thorns wearers don't loop)
        if (attacker != null && attacker != victim && !source.is(DamageTypes.THORNS)) {
            for (Active a : active(victim, Effect.Type.THORNS)) {
                if (a.effect.conditions.test(victim, attacker, random) && a.value > 0) {
                    attacker.hurt(victim.damageSources().thorns(victim), (float) (amount * a.value));
                }
            }
            for (Active a : active(victim, Effect.Type.ON_HURT_EFFECT)) {
                if (a.effect.conditions.test(victim, attacker, random)) applyEffect(a.effect, a.effect.onSelf ? victim : attacker);
            }
            runFunctions(victim, attacker, "hurt");
        }
    }

    /**
     * A melee hit: a player or mob attack dealt by the attacker itself. Checking only "direct entity == attacker"
     * also counted thorns (the thorns owner is both), so thorns damage set off on-hit effects and lifesteal.
     */
    static boolean isMelee(DamageSource source, LivingEntity attacker) {
        if (source.getDirectEntity() != attacker) return false;
        return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
    }

    private static boolean matchesDamage(IdOrTag<DamageType> t, DamageSource source) {
        return t.isTag() ? source.is(t.tag()) : source.typeHolder().unwrapKey().map(k -> k.location().equals(t.id())).orElse(false);
    }

    // lifesteal uses the final damage (after armor)
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || !isMelee(source, attacker)) return;
        double steal = 0;
        for (Active a : active(attacker, Effect.Type.LIFESTEAL)) {
            if (a.effect.conditions.test(attacker, event.getEntity(), attacker.getRandom())) steal += a.value;
        }
        if (steal > 0) attacker.heal((float) (event.getAmount() * steal));
    }

    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        Player player = event.getEntity();
        // The client runs the attack too (for its own crit particles), but its random roll isn't the server's:
        // rolling there showed crits that didn't happen and hid ones that did. The server's crit is sent to clients.
        if (player.level().isClientSide) return;
        LivingEntity target = event.getTarget() instanceof LivingEntity l ? l : null;
        double chance = 0, damage = 0;
        for (Active a : active(player, Effect.Type.CRIT_CHANCE)) if (a.effect.conditions.test(player, target, player.getRandom())) chance += a.value;
        for (Active a : active(player, Effect.Type.CRIT_DAMAGE)) if (a.effect.conditions.test(player, target, player.getRandom())) damage += a.value;
        boolean crit = event.isVanillaCritical() || event.getResult() == Event.Result.ALLOW;
        if (!crit && chance > 0 && player.getRandom().nextDouble() < chance) {
            event.setResult(Event.Result.ALLOW);
            crit = true;
        }
        if (crit && damage != 0) event.setDamageModifier((float) (event.getDamageModifier() * Math.max(0, 1 + damage)));
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        double bonus = 0;
        for (Active a : active(player, Effect.Type.BREAK_SPEED)) {
            IdOrTag<Block> blocks = a.effect.blocks;
            if (blocks != null && !blocks.matches(event.getState().getBlockHolder())) continue;
            // the client works out the same speed to show the cracks, so no random "chance" here: a roll on each
            // side gave the two different speeds, and the block cracked at one speed and broke at the other
            if (a.effect.conditions.testIgnoringChance(player, null)) bonus += a.value;
        }
        if (bonus != 0) event.setNewSpeed((float) (event.getNewSpeed() * Math.max(0, 1 + bonus)));
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        double bonus = 0;
        for (Active a : active(player, Effect.Type.XP_BONUS)) if (a.effect.conditions.test(player, event.getEntity(), player.getRandom())) bonus += a.value;
        if (bonus != 0) event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * Math.max(0, 1 + bonus)));
    }

    @SubscribeEvent
    public static void onLooting(LootingLevelEvent event) {
        if (event.getDamageSource() == null || !(event.getDamageSource().getEntity() instanceof LivingEntity killer)) return;
        int extra = 0;
        for (Active a : active(killer, Effect.Type.LOOTING)) {
            if (a.effect.conditions.test(killer, event.getEntity(), killer.getRandom())) extra += (int) Math.round(a.value);
        }
        if (extra != 0) event.setLootingLevel(Math.max(0, event.getLootingLevel() + extra));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof LivingEntity killer) runFunctions(killer, event.getEntity(), "kill");
    }

    // passive effects and "tick" functions, once a second
    private static final String PASSIVE_KEY = "TarnishedRelicsPassive";
    private static final int PASSIVE_DURATION = 400;
    private static final int PASSIVE_REFRESH_BELOW = 220;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % 20 != 0) return;
        // the strongest amplifier each effect is given at (two items with the same effect: the stronger one counts)
        Map<MobEffect, Integer> wanted = new LinkedHashMap<>();
        for (Active a : active(player, Effect.Type.PASSIVE_EFFECT)) {
            if (!a.effect.conditions.test(player, null, player.getRandom())) continue;
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(a.effect.mobEffect);
            if (effect != null) wanted.merge(effect, a.effect.amplifier, Math::max);
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
        runFunctions(player, null, "tick");
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

    /** Durability: called from ItemStackMixin with the wear about to be applied. */
    public static int adjustWear(ItemStack stack, int amount, RandomSource random) {
        CompoundTag relic = RelicTag.get(stack);
        if (relic == null || amount <= 0) return amount;
        List<Active> list = new ArrayList<>();
        collect(stack, null, Effect.Type.DURABILITY, list);
        double chance = 0;
        for (Active a : list) chance += a.value;
        if (chance == 0) return amount;
        int out = 0;
        for (int i = 0; i < amount; i++) {
            if (chance > 0) {
                if (random.nextDouble() >= chance) out++;        // resisted this point of wear
            } else {
                out += random.nextDouble() < -chance ? 2 : 1;    // negative: sometimes wears twice as fast
            }
        }
        return out;
    }

    private static void applyEffect(Effect e, LivingEntity on) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(e.mobEffect);
        if (effect != null) on.addEffect(new MobEffectInstance(effect, e.duration, e.amplifier));
    }

    // "function" effects: run the datapack function as the wielder (or the target), at their position
    private static void runFunctions(LivingEntity wielder, @Nullable LivingEntity target, String trigger) {
        MinecraftServer server = wielder.getServer();
        if (server == null) return;
        for (Active a : active(wielder, Effect.Type.FUNCTION)) {
            if (!a.effect.trigger.equals(trigger) || !a.effect.conditions.test(wielder, target, wielder.getRandom())) continue;
            Entity runAs = a.effect.asTarget && target != null ? target : wielder;
            server.getFunctions().get(a.effect.function).ifPresent(fn -> server.getFunctions().execute(fn,
                    runAs.createCommandSourceStack().withPermission(2).withSuppressedOutput()));
        }
    }
}
