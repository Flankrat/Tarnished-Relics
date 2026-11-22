package com.flankrat.tarnishedrelics.client;

import com.flankrat.tarnishedrelics.client.compat.TooltipOverhaulCompat;
import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.Legendary;
import com.flankrat.tarnishedrelics.data.IdOrTag;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.data.ValueSpec;
import com.flankrat.tarnishedrelics.effect.SetBonuses;
import com.flankrat.tarnishedrelics.name.NameFallback;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Tooltip of an identified relic: its rarity under the name, then one line per affix effect that isn't a plain
 * attribute (attributes already show in the item's normal stat lines). Any effect can set "tooltip" (a lang key) to
 * use its own text, or "hidden": true to show nothing.
 */
public final class RelicTooltips {

    private RelicTooltips() {
    }

    /** The relic's tier name ("Unique") in the tier's color, or null if the stack isn't an identified relic. */
    @Nullable
    public static Component tierName(ItemStack stack) {
        CompoundTag relic = RelicTag.get(stack);
        return relic == null ? null : tierName(relic);
    }

    private static Component tierName(CompoundTag relic) {
        ResourceLocation rarityId = RelicTag.rarity(relic);
        Rarity rarity = rarityId == null ? null : TRData.get().rarities().get(rarityId);
        String rarityKey = rarity != null ? rarity.nameKey() : "tarnishedrelics.rarity.unknown";
        String fallback = rarityId != null ? NameFallback.title(rarityId.toString()) : "Relic";
        return Component.translatableWithFallback(rarityKey, fallback).withStyle(s -> s.withColor(relic.getInt("Color")));
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CompoundTag relic = RelicTag.get(event.getItemStack());
        if (relic == null) return;
        List<Component> lines = new ArrayList<>();
        ResourceLocation rarityId = RelicTag.rarity(relic);
        // Tooltip Overhaul's rating line under the name already shows the tier
        if (!TooltipOverhaulCompat.showsRating()) lines.add(tierName(relic));
        // a legendary's lore lines (lang keys or plain text)
        var lore = relic.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.getString(i);
            lines.add(Component.translatableWithFallback(line, line).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        // Shift: every rolled number also shows its range and how good the roll is ("+3.4 attack damage (2 to 5, 47%
        // roll)"), so a great roll can be told from a bad one. Without Shift, a hint when there is any.
        boolean shift = Screen.hasShiftDown();
        boolean anyRange = false;
        for (RelicTag.Rolled rolled : RelicTag.affixes(relic)) {
            for (Effect e : rolled.affix().effects()) {
                Component roll = rollNote(e, rarityId, rolled.roll());
                if (roll != null) anyRange = true;
                Component line = describe(e, e.value.get(rarityId, rolled.roll()));
                // attributes are in vanilla's stat lines; with Shift they get a line here too, for their roll
                if (line == null && shift && roll != null && e.type == Effect.Type.ATTRIBUTE) line = attributeLine(e, e.value.get(rarityId, rolled.roll()));
                if (line == null) continue;
                if (shift && roll != null) line = Component.empty().append(line).append(roll);
                // an effect that works from another slot says so (attributes already show "When in ...")
                if (e.slot != null && e.slot != RelicTag.slot(relic)) {
                    line = Component.empty().append(line).append(" ").append(Component.translatable("tarnishedrelics.effect.in_slot",
                            Component.translatable("tarnishedrelics.slot." + e.slot.getName())));
                }
                lines.add(Component.literal(" ").append(line).withStyle(ChatFormatting.BLUE));
            }
        }
        setLines(relic, event.getItemStack(), lines);
        if (!shift && anyRange) lines.add(Component.translatable("tarnishedrelics.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    static Component describe(Effect e, double v) {
        if (e.json.has("hidden") && e.json.get("hidden").getAsBoolean()) return null;
        // a lang key, or plain text (the fallback is formatted with the same %1$s/%2$s)
        if (e.json.has("tooltip")) {
            String t = e.json.get("tooltip").getAsString();
            return Component.translatableWithFallback(t, t, percent(v), number(v));
        }
        String key = "tarnishedrelics.effect." + e.type.key();
        MutableComponent line = switch (e.type) {
            case ATTRIBUTE -> null;   // shown by vanilla's stat lines
            case DAMAGE_VS -> e.targets.isEmpty() ? Component.translatable(key + ".all", percent(v))
                    : Component.translatable(key, percent(v), names(e.targets));
            case DAMAGE_TAKEN -> e.damageTypes.isEmpty() ? Component.translatable(key + ".all", percent(v))
                    : Component.translatable(key, percent(v), names(e.damageTypes));
            case ON_HIT_EFFECT, ON_HURT_EFFECT -> Component.translatable(key + (e.onSelf ? ".self" : ""),
                    chance(e), effectName(e), number(e.duration / 20.0));
            case PASSIVE_EFFECT -> Component.translatable(key, effectName(e));
            case ON_HIT_FIRE -> Component.translatable(key, chance(e), number(v));
            case LOOTING -> Component.translatable(key, signed(Math.round(v)));
            case FUNCTION -> null;    // only shown when it has its own "tooltip"
            default -> Component.translatable(key, percent(v));   // lifesteal, thorns, crits, break speed, durability, xp
        };
        // "(sometimes)": it doesn't always work. A chance counts too, unless the line already says it ("35% chance on hit")
        boolean sometimes = e.conditions.hasOtherConditions() || (e.conditions.chance() < 1 && !showsChance(e.type));
        if (line != null && sometimes) line.append(Component.translatable("tarnishedrelics.effect.conditional"));
        return line;
    }

    // the effect types whose line starts with the chance
    private static boolean showsChance(Effect.Type type) {
        return type == Effect.Type.ON_HIT_EFFECT || type == Effect.Type.ON_HURT_EFFECT || type == Effect.Type.ON_HIT_FIRE;
    }

    /**
     * A legendary set piece: "Set: Dawnbreaker, Crest of the Dawn (1/2)" and the set bonus, in blue while the whole set
     * is on (what the player holds and wears, each in its own slot), in gray otherwise. Values use the main piece's
     * roll: this one's, or a worn main piece's; without either, the lowest value "(up to" the highest ")".
     */
    private static void setLines(CompoundTag relic, ItemStack stack, List<Component> lines) {
        ResourceLocation id = relic.contains("Legendary") ? ResourceLocation.tryParse(relic.getString("Legendary")) : null;
        if (id == null) return;
        Legendary set = null;
        for (Legendary l : TRData.get().legendaries().values()) {
            if (!l.setEffects().isEmpty() && (l.id().equals(id) || l.companions().contains(id))) set = l;
        }
        if (set == null) return;
        List<ResourceLocation> members = new ArrayList<>();
        members.add(set.id());
        members.addAll(set.companions());
        var player = Minecraft.getInstance().player;
        Map<ResourceLocation, ItemStack> worn = player != null ? SetBonuses.pieces(player) : Map.of();
        long have = members.stream().filter(worn::containsKey).count();
        boolean complete = have == members.size();
        MutableComponent names = Component.empty();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) names.append(", ");
            Legendary m = TRData.get().legendaries().get(members.get(i));
            names.append(m == null ? Component.literal(members.get(i).toString()) : Component.translatableWithFallback(m.name(), m.name()));
        }
        lines.add(Component.translatable("tarnishedrelics.tooltip.set", names, have, members.size()).withStyle(ChatFormatting.GOLD));
        ItemStack main = set.id().equals(id) ? stack : worn.get(set.id());
        ResourceLocation rarity = RelicTag.rarity(main != null ? RelicTag.get(main) : relic);
        for (Effect e : set.setEffects()) {
            double v = main != null ? e.value.get(rarity, SetBonuses.roll(main)) : e.value.rangeFor(rarity).min();
            Component line = e.type == Effect.Type.ATTRIBUTE ? attributeLine(e, v) : describe(e, v);
            if (line == null) continue;
            if (main == null && e.type.number && e.value.rangeFor(rarity).min() != e.value.rangeFor(rarity).max()) {
                line = Component.empty().append(line).append(Component.translatable("tarnishedrelics.tooltip.up_to", format(e, e.value.rangeFor(rarity).max())));
            }
            lines.add(Component.literal(" ").append(line).withStyle(complete ? ChatFormatting.BLUE : ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * " (2 to 5, 47% roll)": the range the value was rolled in, and where in it the item's roll is (0% the lowest, 100%
     * the highest), in the same units as the line. Null for an effect without a rolled number (fixed values, potion
     * effects, functions).
     */
    @Nullable
    static Component rollNote(Effect e, @Nullable ResourceLocation rarity, double roll) {
        if (!e.type.number) return null;
        ValueSpec.Range r = e.value.rangeFor(rarity);
        if (r.min() == r.max()) return null;
        return Component.translatable("tarnishedrelics.tooltip.roll", format(e, r.min()), format(e, r.max()),
                Math.round(roll * 100) + "%").withStyle(ChatFormatting.DARK_GRAY);
    }

    // a value as its line shows it: a percent, except on-hit fire (seconds), looting (levels) and added attributes
    private static String format(Effect e, double v) {
        return switch (e.type) {
            case ON_HIT_FIRE -> number(v);
            case LOOTING -> signed(Math.round(v));
            case ATTRIBUTE -> e.operation == AttributeModifier.Operation.ADDITION ? (v >= 0 ? "+" : "") + number(v) : percent(v);
            default -> percent(v);
        };
    }

    // an attribute's own line, for its roll ("+3.4 Attack Damage"; vanilla's stat lines show it otherwise)
    static Component attributeLine(Effect e, double v) {
        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(e.attribute);
        Component name = attribute != null ? Component.translatable(attribute.getDescriptionId()) : Component.literal(String.valueOf(e.attribute));
        return Component.literal(format(e, v) + " ").append(name);
    }

    private static String percent(double v) {
        long p = Math.round(v * 100);
        return (p >= 0 ? "+" : "") + p + "%";
    }

    private static String signed(long v) {
        return (v >= 0 ? "+" : "") + v;
    }

    private static String number(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String chance(Effect e) {
        return Math.round(e.conditions.chance() * 100) + "%";
    }

    private static Component effectName(Effect e) {
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(e.mobEffect);
        MutableComponent name = effect != null ? effect.getDisplayName().copy() : Component.literal(String.valueOf(e.mobEffect));
        if (e.amplifier > 0) name.append(" ").append(Component.translatable("enchantment.level." + (e.amplifier + 1)));
        return name;
    }

    // "undead, arthropods": a lang key per id/tag (tarnishedrelics.target.<namespace>.<path>), else the path made readable
    private static <T> Component names(List<IdOrTag<T>> list) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) out.append(", ");
            ResourceLocation id = list.get(i).id();
            String fallback = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace('_', ' ');
            out.append(Component.translatableWithFallback("tarnishedrelics.target." + id.getNamespace() + "." + id.getPath().replace('/', '.'), fallback));
        }
        return out;
    }
}
