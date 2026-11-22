package com.flankrat.tarnishedrelics.effect;

import com.flankrat.tarnishedrelics.TarnishedRelics;
import com.flankrat.tarnishedrelics.data.Conditions;
import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.IdOrTag;
import com.flankrat.tarnishedrelics.mixin.AbstractArrowAccessor;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LootingLevelEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Makes relic affixes do things. An affix only works while its item is in the slot its category names (a sword in the
 * main hand, a helmet on the head...), or the slot an effect names with "slot". Numbers come from the current datapack and the roll stored on the item.
 * Passive effects and "tick" functions are in {@link PassiveEffects}.
 */
public final class RelicEffects {

    private RelicEffects() {
    }

    /** One working effect: its definition, its rolled value, and the item it's on. */
    record Active(Effect effect, double value, ItemStack stack) {
    }

    /**
     * Every working effect of the given type on everything this entity holds or wears, plus complete legendary sets'
     * bonuses (SetBonuses).
     */
    static List<Active> active(LivingEntity entity, Effect.Type type) {
        List<Active> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            collect(stack, slot, type, out);
        }
        SetBonuses.collect(entity, type, out::add);   // and the bonuses of complete legendary sets
        return out;
    }

    /**
     * The effects that count for a hit the attacker made. Usually everything they hold and wear. A relic weapon that
     * was thrown (a trident, a modded javelin) hits with its own effects, as if it were in its slot, plus the
     * armor's; what's in the hands then didn't make that hit, so it doesn't count (nor a set bonus: the thrown piece
     * isn't in its slot).
     */
    static List<Active> forHit(LivingEntity attacker, DamageSource source, Effect.Type type) {
        ItemStack thrown = thrownRelic(source, attacker);
        if (thrown.isEmpty()) return active(attacker, type);
        List<Active> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) collect(attacker.getItemBySlot(slot), slot, type, out);
        }
        collect(thrown, RelicTag.slot(RelicTag.get(thrown)), type, out);
        return out;
    }

    /** The relic weapon a projectile hit was thrown as, or EMPTY (not a projectile, or its item isn't a relic: arrows). */
    static ItemStack thrownRelic(DamageSource source, LivingEntity attacker) {
        Entity direct = source.getDirectEntity();
        if (direct == null || direct == attacker) return ItemStack.EMPTY;
        ItemStack item = direct instanceof AbstractArrow arrow ? ((AbstractArrowAccessor) arrow).tarnishedrelics$pickupItem()
                : direct instanceof ThrowableItemProjectile thrown ? thrown.getItem() : ItemStack.EMPTY;
        return RelicTag.isRelic(item) ? item : ItemStack.EMPTY;
    }

    private static void collect(ItemStack stack, @Nullable EquipmentSlot slot, Effect.Type type, List<Active> out) {
        collect(stack, slot, type, out::add);
    }

    /** The working effects of one item (of one type, or all with type null), in its order, to "out". */
    private static void collect(ItemStack stack, @Nullable EquipmentSlot slot, @Nullable Effect.Type type, Consumer<Active> out) {
        CompoundTag relic = RelicTag.get(stack);
        if (relic == null) return;
        EquipmentSlot own = RelicTag.slot(relic);
        ResourceLocation rarity = RelicTag.rarity(relic);
        for (RelicTag.Rolled rolled : RelicTag.affixes(relic)) {
            for (Effect e : rolled.affix().effects()) {
                // only in its own slot (or the slot the effect names, e.g. a dagger that helps from the offhand)
                if (slot != null && (e.slot != null ? e.slot : own) != slot) continue;
                if (type == null || e.type == type) out.accept(new Active(e, e.value.get(rarity, rolled.roll()), stack));
            }
        }
    }

    /**
     * Every working effect on an entity's gear (or of a hit, see gearForHit), by type: read once and then looked up per
     * type. A hit needs about ten types from both sides; looking each up on its own read every item's relic data again
     * each time. Each type's effects are in the same order as active/forHit give them.
     */
    record Gear(EnumMap<Effect.Type, List<Active>> byType) {
        List<Active> of(Effect.Type type) {
            return byType.getOrDefault(type, List.of());
        }

        private void add(Active a) {
            byType.computeIfAbsent(a.effect().type, t -> new ArrayList<>()).add(a);
        }
    }

    /** Everything held and worn (like active, for every type). */
    static Gear gear(LivingEntity entity) {
        Gear g = new Gear(new EnumMap<>(Effect.Type.class));
        for (EquipmentSlot slot : EquipmentSlot.values()) collect(entity.getItemBySlot(slot), slot, null, g::add);
        SetBonuses.collect(entity, null, g::add);
        return g;
    }

    /** The effects that count for a hit the attacker made (like forHit, for every type). */
    static Gear gearForHit(LivingEntity attacker, DamageSource source) {
        ItemStack thrown = thrownRelic(source, attacker);
        if (thrown.isEmpty()) return gear(attacker);
        Gear g = new Gear(new EnumMap<>(Effect.Type.class));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) collect(attacker.getItemBySlot(slot), slot, null, g::add);
        }
        collect(thrown, RelicTag.slot(RelicTag.get(thrown)), null, g::add);
        return g;
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
        // each side's gear, read once for the whole hit (the attacker's only for a hit with their weapon: nothing of
        // theirs counts for other damage they cause)
        boolean weaponHit = attacker != null && isWeaponHit(source, attacker);
        boolean projectile = attacker != null && isProjectileHit(source, attacker);
        Gear attackerGear = weaponHit || projectile ? gearForHit(attacker, source) : null;
        Gear victimGear = gear(victim);
        if (attackerGear != null) amount = dealt(amount, attackerGear, attacker, victim, projectile, random);
        amount *= takenMultiplier(victimGear, victim, attacker, source, random);
        event.setAmount(amount);
        if (attacker == null) return;
        if (weaponHit) onWeaponHit(attackerGear, attacker, victim, random);
        // the victim's gear answers (not for thorns damage, so two thorns wearers don't loop)
        if (attacker != victim && !source.is(DamageTypes.THORNS)) onHurtBy(victimGear, victim, attacker, amount, random);
    }

    /**
     * The attacker's damage_vs (and a projectile hit's crit), for a hit with their weapon: a melee hit, a thrown relic,
     * or a projectile they shot. Other damage they cause (their thorns, an explosion they set off, a potion) isn't a
     * hit with what they're holding, so onHurt only calls this for a weapon hit or a shot (gear: the hit's, gearForHit).
     */
    private static float dealt(float amount, Gear gear, LivingEntity attacker, LivingEntity victim, boolean projectile, RandomSource random) {
        // damage_vs: +/- % damage against matching targets (or everything)
        amount *= (float) multiplier(sum(gear.of(Effect.Type.DAMAGE_VS),
                e -> e.targets.isEmpty() || e.targets.stream().anyMatch(t -> Conditions.isTarget(t, victim)), attacker, victim, random));
        // crits for a projectile hit (an arrow or bolt, or a thrown relic): Forge's crit event (onCrit) only fires
        // for melee attacks, so crit_chance and crit_damage did nothing on bows, crossbows and thrown relics
        if (projectile) amount *= projectileCrit(gear, attacker, victim, random);
        return amount;
    }

    // damage_taken: +/- % damage from matching damage types (armor and held items of the victim). One without a damage
    // type list counts for everything except damage meant to get through everything (falling out of the world, /kill:
    // #minecraft:bypasses_invulnerability), like vanilla's protection; it made you last longer in the void
    private static float takenMultiplier(Gear gear, LivingEntity victim, @Nullable LivingEntity attacker, DamageSource source, RandomSource random) {
        boolean unstoppable = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return (float) multiplier(sum(gear.of(Effect.Type.DAMAGE_TAKEN),
                e -> e.damageTypes.isEmpty() ? !unstoppable : e.damageTypes.stream().anyMatch(t -> matchesDamage(t, source)), victim, attacker, random));
    }

    // on-hit effects, on-hit fire and "hit" functions: only for melee hits and thrown relic weapons (not arrows,
    // explosions or thorns)
    private static void onWeaponHit(Gear gear, LivingEntity attacker, LivingEntity victim, RandomSource random) {
        for (Active a : gear.of(Effect.Type.ON_HIT_EFFECT)) {
            if (a.effect.conditions.test(attacker, victim, random)) applyEffect(a.effect, a.effect.onSelf ? attacker : victim);
        }
        for (Active a : gear.of(Effect.Type.ON_HIT_FIRE)) {
            if (a.effect.conditions.test(attacker, victim, random)) victim.setSecondsOnFire((int) Math.round(a.value));
        }
        runFunctions(attacker, victim, "hit", gear.of(Effect.Type.FUNCTION));
    }

    // the victim's gear when they're hit: thorns (a share of the damage, after damage_vs/damage_taken), on-hurt
    // effects and "hurt" functions
    private static void onHurtBy(Gear gear, LivingEntity victim, LivingEntity attacker, float amount, RandomSource random) {
        for (Active a : gear.of(Effect.Type.THORNS)) {
            if (a.effect.conditions.test(victim, attacker, random) && a.value > 0) {
                attacker.hurt(victim.damageSources().thorns(victim), (float) (amount * a.value));
            }
        }
        for (Active a : gear.of(Effect.Type.ON_HURT_EFFECT)) {
            if (a.effect.conditions.test(victim, attacker, random)) applyEffect(a.effect, a.effect.onSelf ? victim : attacker);
        }
        runFunctions(victim, attacker, "hurt", gear.of(Effect.Type.FUNCTION));
    }

    /** The values of the effects whose conditions pass, added up (wielder: who has them; target may be null). */
    static double sum(List<Active> effects, LivingEntity wielder, @Nullable LivingEntity target, RandomSource random) {
        return sum(effects, e -> true, wielder, target, random);
    }

    /**
     * Same, only for effects that fit (checked first: an effect that doesn't fit never rolls its "chance", so the
     * random rolls stay the same as before for the rest).
     */
    static double sum(List<Active> effects, Predicate<Effect> fits, LivingEntity wielder, @Nullable LivingEntity target, RandomSource random) {
        double total = 0;
        for (Active a : effects) if (fits.test(a.effect) && a.effect.conditions.test(wielder, target, random)) total += a.value;
        return total;
    }

    // a +/- fraction (0.3 = +30%) as a multiplier, never below 0 (a double, like the sums: callers cast once at the end)
    static double multiplier(double bonus) {
        return Math.max(0, 1 + bonus);
    }

    /** A hit with a weapon itself: a melee hit, or a thrown relic weapon (whose effects then count, see forHit). */
    static boolean isWeaponHit(DamageSource source, LivingEntity attacker) {
        return isMelee(source, attacker) || !thrownRelic(source, attacker).isEmpty();
    }

    /**
     * A hit by a projectile the attacker shot or threw (not a melee hit, not thorns). Not a thrown potion's magic
     * (Harming): that's a potion, not a shot.
     */
    static boolean isProjectileHit(DamageSource source, LivingEntity attacker) {
        return source.getDirectEntity() instanceof Projectile && source.getDirectEntity() != attacker
                && !source.is(DamageTypes.INDIRECT_MAGIC);
    }

    /**
     * The damage multiplier for a projectile hit: crit_chance rolls a crit (1.5x like a melee crit), and crit_damage
     * makes that crit hit harder. 1 when it doesn't crit. Only crits rolled here count: an arrow from a fully drawn
     * bow is a vanilla "crit arrow" on almost every shot, and crit_damage boosting all of those would be much stronger
     * than on melee crits.
     */
    static float projectileCrit(Gear gear, LivingEntity attacker, LivingEntity victim, RandomSource random) {
        double chance = sum(gear.of(Effect.Type.CRIT_CHANCE), attacker, victim, random);
        if (chance <= 0 || random.nextDouble() >= chance) return 1;
        double damage = sum(gear.of(Effect.Type.CRIT_DAMAGE), attacker, victim, random);
        // the crit sparks a melee crit shows, so players can see the arrow crit
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.5), victim.getZ(), 12, victim.getBbWidth() / 2, victim.getBbHeight() / 4, victim.getBbWidth() / 2, 0.2);
        }
        return (float) (1.5 * multiplier(damage));
    }

    /**
     * Damage types that are a melee hit ({@code #tarnishedrelics:melee}): vanilla's player and mob attacks and a bee's
     * sting. Combat mods give melee hits their own damage types (by weapon: slashing, blunt...); with only vanilla's
     * types counted, a sword's on-hit effects, on-hit fire, lifesteal and hit functions did nothing with those mods.
     * The tag lists the ones we know of as optional entries (only there when their mod is), plus the shared melee tag
     * of Progressive Mechanics Library (#pml:is_melee_attack), which combat mods built on it fill. Packs add others.
     */
    public static final TagKey<DamageType> MELEE = TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(TarnishedRelics.MODID, "melee"));

    // A melee hit: one dealt by the attacker itself with a melee damage type. Checking only "direct entity ==
    // attacker" also counted thorns (the thorns owner is both), so thorns set off on-hit effects and lifesteal.
    static boolean isMelee(DamageSource source, LivingEntity attacker) {
        if (source.getDirectEntity() != attacker) return false;
        return source.is(MELEE);
    }

    private static boolean matchesDamage(IdOrTag<DamageType> t, DamageSource source) {
        return t.isTag() ? source.is(t.tag()) : source.typeHolder().unwrapKey().map(k -> k.location().equals(t.id())).orElse(false);
    }

    // Lifesteal heals from the final damage: after armor, and after every other mod's change to it. Other mods change
    // the amount in this event too (JLME's protection enchantments after armor) or cancel the hit, so it's read last
    // (LOWEST; a cancelled hit doesn't get here). Read at the normal priority, a hit another mod reduced healed as if
    // it hadn't been, and a hit cancelled later healed too. Damage itself is only changed in LivingHurtEvent.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || !isWeaponHit(source, attacker)) return;
        double steal = sum(forHit(attacker, source, Effect.Type.LIFESTEAL), attacker, event.getEntity(), attacker.getRandom());
        if (steal > 0) attacker.heal((float) (event.getAmount() * steal));
    }

    @SubscribeEvent
    public static void onCrit(CriticalHitEvent event) {
        Player player = event.getEntity();
        // The client runs the attack too (for its own crit particles), but its random roll isn't the server's:
        // rolling there showed crits that didn't happen and hid ones that did. The server's crit is sent to clients.
        if (player.level().isClientSide) return;
        LivingEntity target = event.getTarget() instanceof LivingEntity l ? l : null;
        double chance = sum(active(player, Effect.Type.CRIT_CHANCE), player, target, player.getRandom());
        double damage = sum(active(player, Effect.Type.CRIT_DAMAGE), player, target, player.getRandom());
        // a crit so far: allowed, or vanilla's and nobody refused it (DENY). A refused hit isn't a crit, but crit_chance
        // still rolls, and a crit it rolls overrides the refusal (counting a refused vanilla crit as one skipped the roll)
        boolean crit = event.getResult() == Event.Result.ALLOW || (event.isVanillaCritical() && event.getResult() != Event.Result.DENY);
        if (!crit && chance > 0 && player.getRandom().nextDouble() < chance) {
            event.setResult(Event.Result.ALLOW);
            // a hit that isn't a vanilla crit starts at 1.0: without this the crit did no extra damage
            event.setDamageModifier(Math.max(event.getDamageModifier(), 1.5F));
            crit = true;
        }
        if (crit && damage != 0) event.setDamageModifier((float) (event.getDamageModifier() * multiplier(damage)));
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
        if (bonus != 0) event.setNewSpeed((float) (event.getNewSpeed() * multiplier(bonus)));
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        // what made the kill, like looting: a thrown relic's own bonus (not the hands'), else what's held and worn
        DamageSource killedBy = event.getEntity().getLastDamageSource();
        // (only a hit by this player: the last damage can be someone else's, or lava after the player's hit)
        List<Active> effects = killedBy != null && killedBy.getEntity() == player ? forHit(player, killedBy, Effect.Type.XP_BONUS)
                : active(player, Effect.Type.XP_BONUS);
        double bonus = sum(effects, player, event.getEntity(), player.getRandom());
        if (bonus != 0) event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * multiplier(bonus)));
    }

    @SubscribeEvent
    public static void onLooting(LootingLevelEvent event) {
        if (event.getDamageSource() == null || !(event.getDamageSource().getEntity() instanceof LivingEntity killer)) return;
        int extra = 0;
        for (Active a : forHit(killer, event.getDamageSource(), Effect.Type.LOOTING)) {
            if (a.effect.conditions.test(killer, event.getEntity(), killer.getRandom())) extra += (int) Math.round(a.value);
        }
        if (extra != 0) event.setLootingLevel(Math.max(0, event.getLootingLevel() + extra));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof LivingEntity killer) {
            runFunctions(killer, event.getEntity(), "kill", forHit(killer, event.getSource(), Effect.Type.FUNCTION));
        }
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
    static void runFunctions(LivingEntity wielder, @Nullable LivingEntity target, String trigger) {
        runFunctions(wielder, target, trigger, active(wielder, Effect.Type.FUNCTION));
    }

    // functions: the function effects that count (everything held and worn, or a hit's, see forHit)
    static void runFunctions(LivingEntity wielder, @Nullable LivingEntity target, String trigger, List<Active> functions) {
        MinecraftServer server = wielder.getServer();
        if (server == null) return;
        for (Active a : functions) {
            if (!a.effect.trigger.equals(trigger) || !a.effect.conditions.test(wielder, target, wielder.getRandom())) continue;
            Entity runAs = a.effect.asTarget && target != null ? target : wielder;
            server.getFunctions().get(a.effect.function).ifPresent(fn -> server.getFunctions().execute(fn,
                    runAs.createCommandSourceStack().withPermission(2).withSuppressedOutput()));
        }
    }
}
