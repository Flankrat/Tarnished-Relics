package com.flankrat.tarnishedrelics.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildcardTest {

    @Test
    void starMatchesAnyText() {
        assertTrue(Wildcard.matches("somemod:chests/boss_*", "somemod:chests/boss_dragon"));
        assertTrue(Wildcard.matches("*", "minecraft:chests/simple_dungeon"));
        assertTrue(Wildcard.matches("minecraft:*", "minecraft:chests/a/b"));
        assertFalse(Wildcard.matches("somemod:chests/boss_*", "othermod:chests/boss_dragon"));
    }

    @Test
    void everythingElseIsLiteral() {
        // regex characters in an id are not regex
        assertFalse(Wildcard.matches("minecraft:chests.village", "minecraft:chestsXvillage"));
        assertTrue(Wildcard.matches("minecraft:chests/(old)", "minecraft:chests/(old)"));
        assertFalse(Wildcard.matches("minecraft:chests/a", "minecraft:chests/ab"));
    }
}
