package com.flankrat.tarnishedrelics.relic;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WeightedTest {

    private record E(String name, int weight) {
    }

    @Test
    void nothingWithWeightPicksNull() {
        assertNull(Weighted.pick(List.<E>of(), E::weight, RandomSource.create(1)));
        assertNull(Weighted.pick(List.of(new E("a", 0), new E("b", -5)), E::weight, RandomSource.create(1)));
    }

    @Test
    void zeroAndNegativeWeightsAreNeverPicked() {
        List<E> list = List.of(new E("zero", 0), new E("neg", -10), new E("only", 3));
        RandomSource r = RandomSource.create(7);
        for (int i = 0; i < 200; i++) assertEquals("only", Weighted.pick(list, E::weight, r).name());
    }

    @Test
    void picksInProportionToWeight() {
        List<E> list = List.of(new E("a", 1), new E("b", 3));
        RandomSource r = RandomSource.create(42);
        Map<String, Integer> n = new HashMap<>();
        for (int i = 0; i < 40000; i++) n.merge(Weighted.pick(list, E::weight, r).name(), 1, Integer::sum);
        assertEquals(0.75, n.get("b") / 40000.0, 0.02);
    }

    @Test
    void sameSeedSamePick() {
        List<E> list = List.of(new E("a", 5), new E("b", 5), new E("c", 5));
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(Weighted.pick(list, E::weight, RandomSource.create(seed)), Weighted.pick(list, E::weight, RandomSource.create(seed)));
        }
    }

    // Bug (round 5): the weights were added up as an int, so two weights over a billion overflowed to a negative
    // total and nothing was picked (no relic dropped)
    @Test
    void hugeWeightsDontOverflow() {
        List<E> list = List.of(new E("a", 1_500_000_000), new E("b", 1_500_000_000));
        RandomSource r = RandomSource.create(3);
        for (int i = 0; i < 100; i++) assertNotNull(Weighted.pick(list, E::weight, r));
    }
}
