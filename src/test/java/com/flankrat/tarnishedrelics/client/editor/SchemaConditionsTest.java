package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.data.Conditions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The in-game editor offers every condition the loader knows, so a new condition can't be left out of the editor. */
class SchemaConditionsTest {

    @Test
    void editorHasEveryCondition() {
        Set<String> editor = Schema.CONDITIONS.stream().map(Schema.Field::key).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(Conditions.keys()), editor);
    }
}
