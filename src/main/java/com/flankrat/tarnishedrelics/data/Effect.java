package com.flankrat.tarnishedrelics.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * One effect of an affix. Every effect has a "type", optional "conditions", and the fields its type uses:
 * <pre>
 *   attribute      attribute, operation (add | multiply_base | multiply_total), value
 *   damage_vs      value (+0.2 = +20% damage, -0.1 = -10%), target (optional entity id/#tag or list; none = everything)
 *   damage_taken   damage (damage type id/#tag, or list; empty = all), value (-0.2 = take 20% less)
 *   on_hit_effect  effect, duration (ticks), amplifier, apply_to (target | self)
 *   on_hit_fire    value (seconds)
 *   lifesteal      value (fraction of damage dealt healed)
 *   thorns         value (fraction of damage taken sent back to the attacker)
 *   on_hurt_effect effect, duration, amplifier, apply_to (attacker | self)
 *   passive_effect effect, amplifier (kept up while held/worn)
 *   crit_chance    value (extra chance for a player's hit to crit)
 *   crit_damage    value (+0.5 = crits hit 50% harder)
 *   break_speed    value (+0.3 = mine 30% faster), blocks (optional block id/#tag); its "chance" condition is ignored
 *   durability     value (chance each point of wear is ignored; negative wears out faster)
 *   xp_bonus       value (+0.5 = 50% more XP from kills)
 *   looting        value (extra Looting levels)
 *   function       function (datapack function id), trigger (hit | kill | hurt | tick), as (wielder | target)
 * </pre>
 * Any effect can also have "slot" (mainhand, offhand, head, chest, legs, feet): it works while the item is in that
 * slot instead of its category's slot (a dagger that grants health from the offhand).
 */
public final class Effect {

    public enum Type {
        ATTRIBUTE, DAMAGE_VS, DAMAGE_TAKEN, ON_HIT_EFFECT, ON_HIT_FIRE, LIFESTEAL, THORNS, ON_HURT_EFFECT,
        PASSIVE_EFFECT, CRIT_CHANCE, CRIT_DAMAGE, BREAK_SPEED, DURABILITY, XP_BONUS, LOOTING, FUNCTION;

        static Type parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("unknown effect type \"" + s + "\"");
            }
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Type type;
    public final Conditions conditions;
    public final ValueSpec value;
    public final JsonObject json;   // the original JSON, kept for the client sync and tooltips

    // Fields used by some types (null/empty when unused)
    @Nullable public final ResourceLocation attribute;
    public final AttributeModifier.Operation operation;
    public final List<IdOrTag<EntityType<?>>> targets;
    public final List<IdOrTag<DamageType>> damageTypes;
    @Nullable public final IdOrTag<Block> blocks;
    @Nullable public final ResourceLocation mobEffect;
    public final int duration, amplifier;
    public final boolean onSelf;          // apply_to self (otherwise target/attacker)
    @Nullable public final ResourceLocation function;
    public final String trigger;          // function trigger
    public final boolean asTarget;        // function runs as the target
    @Nullable public final EquipmentSlot slot;   // works in this slot instead of the item's own

    private Effect(JsonObject o) {
        json = o;
        type = Type.parse(o.get("type").getAsString());
        conditions = Conditions.parse(o.get("conditions"));
        value = ValueSpec.parse(o.get("value"));
        attribute = o.has("attribute") ? new ResourceLocation(o.get("attribute").getAsString()) : null;
        operation = switch (o.has("operation") ? o.get("operation").getAsString() : "add") {
            case "add" -> AttributeModifier.Operation.ADDITION;
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> throw new JsonParseException("operation must be add, multiply_base or multiply_total");
        };
        targets = Conditions.strings(o.get("target")).stream().map(s -> IdOrTag.parse(s, Registries.ENTITY_TYPE)).toList();
        damageTypes = Conditions.strings(o.get("damage")).stream().map(s -> IdOrTag.parse(s, Registries.DAMAGE_TYPE)).toList();
        blocks = o.has("blocks") ? IdOrTag.parse(o.get("blocks").getAsString(), Registries.BLOCK) : null;
        mobEffect = o.has("effect") ? new ResourceLocation(o.get("effect").getAsString()) : null;
        duration = o.has("duration") ? o.get("duration").getAsInt() : 100;
        amplifier = o.has("amplifier") ? o.get("amplifier").getAsInt() : 0;
        String applyTo = o.has("apply_to") ? o.get("apply_to").getAsString() : "target";
        // anything else used to count as "target": "wielder" gave the enemy the buff
        if (!Set.of("target", "attacker", "self").contains(applyTo))
            throw new JsonParseException("unknown apply_to \"" + applyTo + "\" (target, attacker or self)");
        onSelf = applyTo.equals("self");
        function = o.has("function") ? new ResourceLocation(o.get("function").getAsString()) : null;
        trigger = o.has("trigger") ? o.get("trigger").getAsString() : "hit";
        String as = o.has("as") ? o.get("as").getAsString() : "wielder";
        if (!as.equals("wielder") && !as.equals("target")) throw new JsonParseException("unknown as \"" + as + "\" (wielder or target)");
        asTarget = as.equals("target");
        slot = o.has("slot") ? EquipmentSlot.byName(o.get("slot").getAsString()) : null;
        validate();
    }

    /** Every field an effect can have; any other key is a typo (it used to be ignored, so its default was used). */
    private static final Set<String> FIELDS = Set.of("type", "value", "attribute", "operation", "target", "damage", "effect",
            "duration", "amplifier", "apply_to", "blocks", "function", "trigger", "as", "conditions", "tooltip", "hidden", "slot");

    public static Effect parse(JsonObject o) {
        if (!o.has("type")) throw new JsonParseException("effect needs a \"type\"");
        for (String key : o.keySet()) if (!FIELDS.contains(key)) throw new JsonParseException("unknown effect field \"" + key + "\"");
        return new Effect(o);
    }

    // Catch missing fields at load time instead of when the effect fires
    private void validate() {
        switch (type) {
            case ATTRIBUTE -> require(attribute != null, "attribute");
            case ON_HIT_EFFECT, ON_HURT_EFFECT, PASSIVE_EFFECT -> require(mobEffect != null, "effect");
            case FUNCTION -> {
                require(function != null, "function");
                if (!Set.of("hit", "kill", "hurt", "tick").contains(trigger))
                    throw new JsonParseException("function trigger must be hit, kill, hurt or tick");
            }
            default -> { }
        }
    }

    private void require(boolean ok, String field) {
        if (!ok) throw new JsonParseException(type.key() + " effect needs \"" + field + "\"");
    }
}
