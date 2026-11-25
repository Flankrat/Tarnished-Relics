package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.data.Affix;
import com.flankrat.tarnishedrelics.data.Effect;
import com.flankrat.tarnishedrelics.data.ValueSpec;
import net.minecraft.resources.ResourceLocation;

/**
 * The looter's Luck pushes an affix's 0-1 roll toward its good end, so a lucky player finds better numbers (and
 * milder drawbacks), and Bad Luck pushes it the other way.
 * <p>
 * Which end is good depends on the effect: for most a higher value is better (more damage, more durability, a
 * smaller penalty like -0.1 instead of -0.15), but for damage_taken a lower value is (-0.25 = take 25% less).
 * An affix's effects share one roll, so its good end is the one most of its rolled effects agree on; when they
 * cancel out (one gets better as the other gets worse), Luck leaves the roll alone.
 * <p>
 * How it bends: with a = strength x |luck|, a roll u becomes u^(1/(1+a)) (pushed up) or 1-(1-u)^(1/(1+a))
 * (pushed down). It stays in 0-1, a perfect roll can still be had at any Luck, and at strength 0.25 one point of
 * Luck moves the average roll from 50% to about 56%, four points to 67% (like keeping the best of two rolls).
 */
public final class LuckRoll {

    private LuckRoll() {
    }

    /**
     * +1: a higher roll is better for this affix on this rarity; -1: a lower one is; 0: its rolled effects
     * disagree, or none of them is a range (a fixed number doesn't care about the roll).
     */
    public static int direction(Affix affix, ResourceLocation rarity) {
        int sum = 0;
        for (Effect e : affix.effects()) {
            if (!e.type.number) continue;
            ValueSpec.Range r = e.value.rangeFor(rarity);
            if (r.max() == r.min()) continue;                                  // fixed: the roll changes nothing
            int up = r.max() > r.min() ? 1 : -1;                               // which way a higher roll moves the value
            int good = e.type == Effect.Type.DAMAGE_TAKEN ? -1 : 1;            // which way of the value is better
            sum += up * good;
        }
        return Integer.signum(sum);
    }

    /**
     * The roll u (0-1) after Luck. luck: the loot context's (negative for Bad Luck); strength: the config's
     * luckRollBonus; direction: {@link #direction}. Unchanged when any of them is 0.
     */
    public static float apply(float u, float luck, double strength, int direction) {
        double push = luck * strength * direction;   // > 0: toward 1, < 0: toward 0
        if (push == 0) return u;
        double k = 1 / (1 + Math.abs(push));
        double out = push > 0 ? Math.pow(u, k) : 1 - Math.pow(1 - u, k);
        // in [0, 1) like nextFloat: rounding to float could otherwise turn 0.99999999 into exactly 1
        return Math.min(Math.nextDown(1f), Math.max(0f, (float) out));
    }
}
