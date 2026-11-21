package com.flankrat.tarnishedrelics.client.editor;

import com.flankrat.tarnishedrelics.BuiltInData;
import com.flankrat.tarnishedrelics.data.TRData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The editor's fields for each kind of file are exactly the keys the loader accepts: a key only the editor knew would
 * make the editor save files the loader refuses, and one only the loader knew couldn't be edited.
 */
class SchemaFileKeysTest {

    @BeforeAll
    static void bootstrap() {
        BuiltInData.files();
    }

    @ParameterizedTest
    @ValueSource(strings = {"categories", "rarities", "affixes", "legendaries", "names", "levels", "exclusions"})
    void editorFieldsAreTheLoadersKeys(String folder) {
        Set<String> editor = Schema.FILES.get(folder).stream().map(Schema.Field::key).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(TRData.keys(folder)), editor);
    }
}
