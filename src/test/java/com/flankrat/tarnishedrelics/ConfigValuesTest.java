package com.flankrat.tarnishedrelics;

import com.electronwill.nightconfig.core.CommentedConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Values in config/tarnishedrelics-common.toml that the game can't use are refused (Forge then puts the default back
 * and logs it). lootTypes took any word, so a typo ("chests") matched no kind of loot and relics silently stopped
 * dropping; the editor checked it, a hand-edited file didn't.
 */
class ConfigValuesTest {

    private static CommentedConfig config(List<String> lootTypes) {
        CommentedConfig c = CommentedConfig.inMemory();
        TRConfig.SPEC.correct(c);   // every value at its default
        c.set(TRConfig.LOOT_TYPES.getPath(), lootTypes);
        return c;
    }

    @Test
    void unknownLootTypeIsRefused() {
        assertFalse(TRConfig.SPEC.isCorrect(config(List.of("chests"))), "\"chests\" was accepted");
        assertFalse(TRConfig.SPEC.isCorrect(config(List.of("chest", "mobs"))), "\"mobs\" was accepted");
    }

    @Test
    void everyLootTypeIsFine() {
        assertTrue(TRConfig.SPEC.isCorrect(config(List.of("chest", "entity", "fishing", "archaeology", "gift", "barter"))));
        assertTrue(TRConfig.SPEC.isCorrect(config(List.of())));
    }
}
