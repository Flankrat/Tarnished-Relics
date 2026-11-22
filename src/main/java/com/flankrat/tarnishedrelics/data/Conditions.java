package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Optional "conditions" on an effect. Every listed condition must pass. Example:
 * <pre>
 *   "conditions": { "chance": 0.25, "health_below": 0.5, "is_night": true, "target": ["#minecraft:undead"] }
 * </pre>
 * health_below/health_above are fractions of the wielder's max health. is_night/is_day: in a dimension with days and
 * nights (neither is true in the Nether or the End). target and target_in_air only mean something for
 * effects that have a target (on hit, damage against...). target_in_air: the target isn't standing on anything, swimming
 * or riding (flying, jumping, falling, knocked up).
 */
public final class Conditions {

    public static final Conditions NONE = new Conditions();

    private double chance = 1;
    private double healthBelow = -1, healthAbove = -1;
    private Boolean inWater, isNight, isDay, sneaking, onFire, targetInAir;
    private Double yBelow, yAbove;
    private final List<ResourceLocation> dimensions = new ArrayList<>();
    private final List<IdOrTag<Biome>> biomes = new ArrayList<>();
    private final List<IdOrTag<EntityType<?>>> targets = new ArrayList<>();

    private static final Set<String> KEYS = Set.of("chance", "health_below", "health_above", "in_water", "is_night", "is_day",
            "sneaking", "on_fire", "y_below", "y_above", "dimensions", "biomes", "target", "target_in_air");

    /** Every condition name the loader knows (the editor offers each one). */
    public static Set<String> keys() {
        return KEYS;
    }

    public static Conditions parse(@Nullable JsonElement json) {
        if (json == null) return NONE;
        JsonObject o = json.getAsJsonObject();
        // a misspelled condition used to be skipped, so the effect worked all the time
        for (String key : o.keySet()) if (!KEYS.contains(key)) throw new JsonParseException("unknown condition \"" + key + "\"");
        Conditions c = new Conditions();
        if (o.has("chance")) c.chance = o.get("chance").getAsDouble();
        if (o.has("health_below")) c.healthBelow = o.get("health_below").getAsDouble();
        if (o.has("health_above")) c.healthAbove = o.get("health_above").getAsDouble();
        if (o.has("in_water")) c.inWater = o.get("in_water").getAsBoolean();
        if (o.has("is_night")) c.isNight = o.get("is_night").getAsBoolean();
        if (o.has("is_day")) c.isDay = o.get("is_day").getAsBoolean();
        if (o.has("sneaking")) c.sneaking = o.get("sneaking").getAsBoolean();
        if (o.has("on_fire")) c.onFire = o.get("on_fire").getAsBoolean();
        if (o.has("y_below")) c.yBelow = o.get("y_below").getAsDouble();
        if (o.has("y_above")) c.yAbove = o.get("y_above").getAsDouble();
        for (String s : strings(o.get("dimensions"))) c.dimensions.add(new ResourceLocation(s));
        for (String s : strings(o.get("biomes"))) c.biomes.add(IdOrTag.parse(s, Registries.BIOME));
        for (String s : strings(o.get("target"))) c.targets.add(IdOrTag.parse(s, Registries.ENTITY_TYPE));
        if (o.has("target_in_air")) c.targetInAir = o.get("target_in_air").getAsBoolean();
        return c;
    }

    /** A JSON string or array of strings, as a list. */
    static List<String> strings(@Nullable JsonElement json) {
        List<String> out = new ArrayList<>();
        if (json == null) return out;
        if (json.isJsonArray()) {
            for (JsonElement e : (JsonArray) json) out.add(e.getAsString());
        } else {
            out.add(json.getAsString());
        }
        return out;
    }

    /** The "chance" condition (1 when not set), for tooltips. */
    public double chance() {
        return chance;
    }

    /** True when anything besides chance is set, for the tooltip's "(conditional)" note. */
    public boolean hasOtherConditions() {
        return healthBelow >= 0 || healthAbove >= 0 || inWater != null || isNight != null || isDay != null || sneaking != null
                || onFire != null || yBelow != null || yAbove != null || !dimensions.isEmpty() || !biomes.isEmpty() || !targets.isEmpty()
                || targetInAir != null;
    }

    public boolean hasTargetFilter() {
        return !targets.isEmpty();
    }

    /** Checks everything. wielder is whoever holds or wears the item; target may be null. */
    public boolean test(LivingEntity wielder, @Nullable LivingEntity target, RandomSource random) {
        if (chance < 1 && random.nextDouble() >= chance) return false;
        return testIgnoringChance(wielder, target);
    }

    /**
     * Checks everything but "chance". For effects the client works out too (break speed): a random roll on each
     * side would disagree, so for those chance doesn't apply.
     */
    public boolean testIgnoringChance(LivingEntity wielder, @Nullable LivingEntity target) {
        float frac = wielder.getHealth() / Math.max(1, wielder.getMaxHealth());
        if (healthBelow >= 0 && frac >= healthBelow) return false;
        if (healthAbove >= 0 && frac <= healthAbove) return false;
        if (inWater != null && wielder.isInWater() != inWater) return false;
        // the level's own day and night: both false in a dimension without days (the Nether, the End), where "not
        // night" used to count as day
        if (isNight != null && wielder.level().isNight() != isNight) return false;
        if (isDay != null && wielder.level().isDay() != isDay) return false;
        if (sneaking != null && wielder.isShiftKeyDown() != sneaking) return false;
        if (onFire != null && wielder.isOnFire() != onFire) return false;
        if (yBelow != null && wielder.getY() >= yBelow) return false;
        if (yAbove != null && wielder.getY() <= yAbove) return false;
        if (!dimensions.isEmpty() && !dimensions.contains(wielder.level().dimension().location())) return false;
        if (!biomes.isEmpty()) {
            var biome = wielder.level().getBiome(wielder.blockPosition());
            if (biomes.stream().noneMatch(b -> b.matches(biome))) return false;
        }
        if (!targets.isEmpty()) {
            if (target == null) return false;
            if (targets.stream().noneMatch(t -> isTarget(t, target))) return false;
        }
        if (targetInAir != null && (target == null || inAir(target) != targetInAir)) return false;
        return true;
    }

    private static final ResourceLocation UNDEAD = new ResourceLocation("tarnishedrelics", "undead");
    private static final ResourceLocation ARTHROPODS = new ResourceLocation("tarnishedrelics", "arthropods");

    /**
     * True if the mob is this target (an entity id or #tag). #tarnishedrelics:undead and #tarnishedrelics:arthropods
     * also take every mob the game itself counts as undead or an arthropod (the mob type vanilla's Smite and Bane of
     * Arthropods check): the tags only list vanilla's, so mods' zombies, skeletons, liches and spiders didn't count.
     */
    public static boolean isTarget(IdOrTag<EntityType<?>> t, LivingEntity mob) {
        if (t.matches(mob.getType().builtInRegistryHolder())) return true;
        if (!t.isTag()) return false;
        if (t.id().equals(UNDEAD)) return mob.getMobType() == MobType.UNDEAD;
        if (t.id().equals(ARTHROPODS)) return mob.getMobType() == MobType.ARTHROPOD;
        return false;
    }

    /** In the air: not standing on anything, not in water or lava, not riding (flying, jumping, falling, knocked up). */
    public static boolean inAir(LivingEntity e) {
        return !e.onGround() && !e.isInWater() && !e.isInLava() && !e.isPassenger();
    }
}
