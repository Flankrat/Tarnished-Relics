package com.flankrat.tarnishedrelics.relic;

import net.minecraft.util.RandomSource;

import java.util.function.ToIntFunction;
import javax.annotation.Nullable;

/** Weighted random picks, used for rarities, categories, affixes and legendaries. */
public final class Weighted {

    private Weighted() {
    }

    /**
     * One entry, picked with a chance of its weight out of the total (weights below 0 count as 0), or null when
     * nothing has any weight. Uses exactly one random number, so the same seed gives the same pick.
     */
    @Nullable
    public static <T> T pick(Iterable<T> entries, ToIntFunction<T> weight, RandomSource random) {
        // a long: int weights added up as an int overflowed (two of 1.5 billion went negative, and nothing was picked)
        long total = 0;
        for (T e : entries) total += Math.max(0, weight.applyAsInt(e));
        if (total <= 0) return null;
        long pick = total <= Integer.MAX_VALUE ? random.nextInt((int) total) : (long) (random.nextDouble() * total);
        for (T e : entries) {
            pick -= Math.max(0, weight.applyAsInt(e));
            if (pick < 0) return e;
        }
        return null;
    }
}
