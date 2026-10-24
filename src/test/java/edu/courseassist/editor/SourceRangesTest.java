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

    @Test
    void roundTripsFileBoundariesAndAnEmptyRange() {
        String source = "first\n\nlast";

        SourceRange wholeFile = SourceRanges.fromOffsets(source, 0, source.length());
        SourceRange emptyAtBlankLine = SourceRanges.fromOffsets(source, 6, 6);

        assertEquals(new SourceRange(1, 1, 3, 5), wholeFile);
        assertEquals(0, SourceRanges.startOffset(source, wholeFile));
        assertEquals(source.length(), SourceRanges.endOffset(source, wholeFile));
        assertEquals(new SourceRange(2, 1, 2, 1), emptyAtBlankLine);
        assertEquals(6, SourceRanges.startOffset(source, emptyAtBlankLine));
        assertEquals(6, SourceRanges.endOffset(source, emptyAtBlankLine));
    }

    @Test
    void usesUtf16ColumnsAcrossCrLfAndSurrogatePairs() {
        String source = "a😀\r\nnext";
        int start = source.indexOf("😀");
        int end = source.indexOf("next") + "next".length();

        SourceRange range = SourceRanges.fromOffsets(source, start, end);

        assertEquals(new SourceRange(1, 2, 2, 5), range);
        assertEquals(start, SourceRanges.startOffset(source, range));
        assertEquals(end, SourceRanges.endOffset(source, range));
    }

    @Test
    void rejectsNegativeReversedAndMissingLineCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> SourceRanges.fromOffsets("abc", -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRanges.fromOffsets("abc", 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> SourceRanges.startOffset("abc", new SourceRange(2, 1, 2, 1)));
    }
}
