package edu.courseassist.editor;

import edu.courseassist.contract.CaptureContracts.SourceRange;

/** Converts immutable UTF-16 offsets to the public one-based source coordinates. */
final class SourceRanges {
    private SourceRanges() {}

    static SourceRange fromOffsets(CharSequence text, int startOffset, int endOffset) {
        validateOffsets(text, startOffset, endOffset);
        Position start = positionAt(text, startOffset);
        Position end = positionAt(text, endOffset);
        return new SourceRange(start.line(), start.column(), end.line(), end.column());
    }

    static int startOffset(CharSequence text, SourceRange range) {
        return offsetAt(text, range.startLine(), range.startColumn());
    }

    static int endOffset(CharSequence text, SourceRange range) {
        return offsetAt(text, range.endLine(), range.endColumn());
    }

    private static Position positionAt(CharSequence text, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        return new Position(line, offset - lineStart + 1);
    }

    private static int offsetAt(CharSequence text, int expectedLine, int column) {
        int line = 1;
        int lineStart = 0;
        for (int index = 0; index < text.length() && line < expectedLine; index++) {
            if (text.charAt(index) == '\n') {
                line++;
                lineStart = index + 1;
            }
        }
        if (line != expectedLine) {
            throw new IllegalArgumentException("Source range line is outside the document");
        }
        int lineEnd = text.length();
        for (int index = lineStart; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lineEnd = index;
                break;
            }
        }
        int offset = lineStart + column - 1;
        if (offset > lineEnd) {
            throw new IllegalArgumentException("Source range column is outside the document line");
        }
        return offset;
    }

    private static void validateOffsets(CharSequence text, int startOffset, int endOffset) {
        if (startOffset < 0 || endOffset < startOffset || endOffset > text.length()) {
            throw new IllegalArgumentException("Source offsets are outside the document");
        }
    }

    private record Position(int line, int column) {}
}
