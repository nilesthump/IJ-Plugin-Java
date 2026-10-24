package edu.courseassist.editor;

import edu.courseassist.contract.CaptureContracts.SourceRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceRangesTest {
    @Test
    void mapsMultilineUtf16OffsetsWithAnExclusiveEnd() {
        String source = "class Demo {\n  String value = \"😀\";\n}\n";
        int start = source.indexOf("value");
        int end = source.indexOf(';') + 1;

        SourceRange range = SourceRanges.fromOffsets(source, start, end);

        assertEquals(new SourceRange(2, 10, 2, 23), range);
        assertEquals(start, SourceRanges.startOffset(source, range));
        assertEquals(end, SourceRanges.endOffset(source, range));
    }

    @Test
    void mapsAnEndAtTheBeginningOfTheNextLine() {
        String source = "one\ntwo";
        SourceRange range = SourceRanges.fromOffsets(source, 0, 4);

        assertEquals(new SourceRange(1, 1, 2, 1), range);
        assertEquals(4, SourceRanges.endOffset(source, range));
    }

    @Test
    void rejectsCoordinatesOutsideTheFrozenDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceRanges.fromOffsets("abc", 1, 4));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRanges.endOffset("abc", new SourceRange(1, 1, 1, 5)));
    }
}
