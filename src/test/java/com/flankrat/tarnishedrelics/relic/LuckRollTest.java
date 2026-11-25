package com.flankrat.tarnishedrelics.relic;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.data.TRData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Luck pushes affix rolls toward their good end (LuckRoll), checked against the real built-in affixes: which end
 * is good for each kind, and how far the rolls move.
 */
class LuckRollTest {

    private static final int N = 10_000;

    private static ResourceLocation tr(String path) {
        return new ResourceLocation("tarnishedrelics", path);
    }

    private static int direction(String affix, String rarity) {
        TRData data = BuiltInData.data();
        return LuckRoll.direction(data.affixes().get(tr(affix)), tr(rarity));
    }

    // the average of the pushed rolls over evenly spread starting rolls (exact, no random noise)
    private static double mean(float luck, double strength, int direction) {
        double sum = 0;
        for (int i = 0; i < N; i++) sum += LuckRoll.apply((i + 0.5f) / N, luck, strength, direction);
        return sum / N;
    }

    @Test
    void goodEndPerEffect() {
        assertEquals(1, direction("sturdy", "magic"), "durability: more is better");
        assertEquals(1, direction("of_smiting", "rare"), "damage_vs: more is better");
        assertEquals(-1, direction("of_fire_warding", "magic"), "damage_taken [-0.1, -0.05]: -0.1 is better, so a low roll");
        assertEquals(-1, direction("drawback_exposed", "legendary"), "damage_taken drawback [0.1, 0.2]: 0.1 is milder");
        assertEquals(1, direction("drawback_glass", "legendary"), "attribute drawback [-0.25, -0.15]: -0.15 is milder");
        assertEquals(1, direction("heavy", "rare"), "heavy: more damage and a smaller speed penalty both at a high roll");
        assertEquals(0, direction("drawback_bloodprice", "legendary"), "no rolled number: Luck has nothing to move");
    }

    @Test
    void noLuckChangesNothing() {
        for (float u : new float[]{0f, 0.3f, 0.77f, Math.nextDown(1f)}) {
            assertEquals(u, LuckRoll.apply(u, 0, 0.25, 1));
            assertEquals(u, LuckRoll.apply(u, 3, 0, 1), "luckRollBonus 0 turns it off");
            assertEquals(u, LuckRoll.apply(u, 3, 0.25, 0), "an affix whose effects disagree is left alone");
        }
    }

    @Test
    void luckMovesTheAverage() {
        assertEquals(0.5, mean(0, 0.25, 1), 1e-3);
        assertEquals(0.556, mean(1, 0.25, 1), 2e-3, "Luck 1 at the default strength");
        assertEquals(0.667, mean(4, 0.25, 1), 2e-3, "Luck 4: like the best of two rolls");
        assertEquals(0.444, mean(1, 0.25, -1), 2e-3, "a low-is-good affix goes the other way");
        assertEquals(0.444, mean(-1, 0.25, 1), 2e-3, "Bad Luck pushes toward the bad end");
    }

    @Test
    void rollsStayInRangeAndInOrder() {
        for (float luck : new float[]{-10, -1, 1, 10, 1000}) {
            for (int dir : new int[]{-1, 1}) {
                float last = -1;
                for (int i = 0; i <= 1000; i++) {
                    float u = Math.min(Math.nextDown(1f), i / 1000f);
                    float out = LuckRoll.apply(u, luck, 0.25, dir);
                    assertTrue(out >= 0 && out < 1, "roll " + out + " out of [0, 1) at luck " + luck);
                    // a better starting roll is never worse after Luck: Luck shifts the odds, not the order
                    assertTrue(out >= last, "order broke at u=" + u + " luck " + luck);
                    last = out;
                }
            }
        }
    }
}
