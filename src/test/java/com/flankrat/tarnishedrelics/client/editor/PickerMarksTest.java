package com.flankrat.tarnishedrelics.client.editor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The picker's multi-select: what Select picks after shift-clicks. */
class PickerMarksTest {

    @Test
    void nothingMarkedPicksTheHighlightedOne() {
        PickerMarks marks = new PickerMarks();
        assertEquals(List.of("bettersurvival:itemlivingdagger"), marks.picked("bettersurvival:itemlivingdagger"));
        assertEquals(List.of(), marks.picked(null));
    }

    @Test
    void markedValuesComeInClickOrderWithTheHighlightedOneLast() {
        PickerMarks marks = new PickerMarks();
        marks.toggle("bettersurvival:itemlivinghammer");
        marks.toggle("bettersurvival:itemlivingdagger");
        assertEquals(List.of("bettersurvival:itemlivinghammer", "bettersurvival:itemlivingdagger", "bettersurvival:itemlivingspear"),
                marks.picked("bettersurvival:itemlivingspear"));
    }

    @Test
    void highlightingAMarkedValueDoesNotAddItTwice() {
        PickerMarks marks = new PickerMarks();
        marks.toggle("bettersurvival:itemlivingdagger");
        assertEquals(List.of("bettersurvival:itemlivingdagger"), marks.picked("bettersurvival:itemlivingdagger"));
    }

    @Test
    void shiftClickingAgainUnmarks() {
        PickerMarks marks = new PickerMarks();
        marks.toggle("bettersurvival:itemlivingdagger");
        assertTrue(marks.isMarked("bettersurvival:itemlivingdagger"));
        marks.toggle("bettersurvival:itemlivingdagger");
        assertFalse(marks.isMarked("bettersurvival:itemlivingdagger"));
        assertEquals(0, marks.size());
    }
}
