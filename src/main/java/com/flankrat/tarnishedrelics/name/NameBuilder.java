package com.flankrat.tarnishedrelics.name;

import com.flankrat.tarnishedrelics.relic.RelicTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The name of an identified relic:
 * <pre>  [Quality word] [name prefix from other mods, e.g. sockets] [affix prefix] [base item] [affix suffix | of &lt;unique name&gt;]</pre>
 * e.g. "Legendary Flaming Champion's Iron Sword of Ithireal", in the rarity's color. Built every time the name is
 * asked for (from ItemStackMixin), so a later reforge or socket change shows up. A name set in an anvil wins.
 */
public final class NameBuilder {

    private NameBuilder() {
    }

    @Nullable
    public static Component build(ItemStack stack, Component base) {
        CompoundTag relic = RelicTag.get(stack);
        if (relic == null) return null;
        int color = relic.getInt("Color");
        MutableComponent out = Component.empty().withStyle(s -> s.withColor(color).withItalic(false));
        // a legendary always has its own name, nothing added
        if (relic.contains("LegendaryName")) {
            String name = relic.getString("LegendaryName");
            return out.append(Component.translatableWithFallback(name, name));
        }
        boolean first = true;

        Component quality = QualityCompat.word(stack);
        if (quality != null) first = append(out, quality, first);
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(RelicTag.NAME_PREFIX)) {
            try {
                Component prefix = Component.Serializer.fromJson(tag.getString(RelicTag.NAME_PREFIX));
                if (prefix != null) first = append(out, prefix, first);
            } catch (RuntimeException ignored) {
                // a bad prefix from another mod is just left out
            }
        }
        if (relic.contains("Prefix")) first = append(out, Component.translatable(relic.getString("Prefix")), first);
        first = append(out, base, first);
        if (relic.contains("UniqueName")) {
            append(out, Component.translatable("tarnishedrelics.name.unique", relic.getString("UniqueName")), first);
        } else if (relic.contains("Suffix")) {
            append(out, Component.translatable(relic.getString("Suffix")), first);
        }
        return out;
    }

    // adds a part with a space before it (except the first)
    private static boolean append(MutableComponent out, Component part, boolean first) {
        if (!first) out.append(" ");
        out.append(part);
        return false;
    }
}
