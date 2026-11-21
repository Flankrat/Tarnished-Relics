package com.flankrat.tarnishedrelics.data;

import java.util.regex.Pattern;

/** Ids with "*" wildcards, as in loot table lists ("somemod:chests/boss_*"). */
public final class Wildcard {

    private Wildcard() {
    }

    /** True if the id matches the pattern; "*" stands for any text (also "/" and ":"), everything else is literal. */
    public static boolean matches(String pattern, String id) {
        return id.matches(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
    }
}
