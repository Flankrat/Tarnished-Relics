package com.flankrat.tarnishedrelics.client;

import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.IdOrTag;
import com.flankrat.tarnishedrelics.data.Rarity;
import com.flankrat.tarnishedrelics.data.TRData;
import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tooltip of an identified relic: its rarity under the name, then one line per affix effect that isn't a plain
 * attribute (attributes already show in the item's normal stat lines). Any effect can set "tooltip" (a lang key) to
 * use its own text, or "hidden": true to show nothing.
 */
public final class RelicTooltips {

    private RelicTooltips() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        CompoundTag relic = RelicTag.get(event.getItemStack());
        if (relic == null) return;
        List<Component> lines = new ArrayList<>();
        ResourceLocation rarityId = RelicTag.rarity(relic);
        Rarity rarity = rarityId == null ? null : TRData.get().rarities().get(rarityId);
        String rarityKey = rarity != null ? rarity.nameKey() : "tarnishedrelics.rarity.unknown";
        lines.add(Component.translatable(rarityKey).withStyle(s -> s.withColor(relic.getInt("Color"))));
        for (RelicTag.Rolled rolled : RelicTag.affixes(relic)) {
            for (Effect e : rolled.affix().effects()) {
                Component line = describe(e, e.value.get(rarityId, rolled.roll()));
                if (line != null) lines.add(Component.literal(" ").append(line).withStyle(ChatFormatting.BLUE));
            }
        }
        event.getToolTip().addAll(Math.min(1, event.getToolTip().size()), lines);
    }

    static Component describe(Effect e, double v) {
        if (e.json.has("hidden") && e.json.get("hidden").getAsBoolean()) return null;
        if (e.json.has("tooltip")) return Component.translatable(e.json.get("tooltip").getAsString(), percent(v), number(v));
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
        if (line != null && e.conditions.hasOtherConditions()) line.append(Component.translatable("tarnishedrelics.effect.conditional"));
        return line;
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
