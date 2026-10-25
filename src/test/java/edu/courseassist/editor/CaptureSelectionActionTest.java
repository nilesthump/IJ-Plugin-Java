package edu.courseassist.editor;

import edu.courseassist.contract.CaptureContracts.SelectionStart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CaptureSelectionActionTest {
    @Test
    void freezesTheExactSourceVersionTextAndUtf16Range() {
        String source = "class Demo {\n  String value = \"😀\";\n}\n";
        int startOffset = source.indexOf("value");
        int endOffset = source.indexOf(';') + 1;

        SelectionStart start = CaptureSelectionAction.freezeSelection(
                "project-instance", "file:///Demo.java", 42, "JAVA", source, startOffset, endOffset);

        assertEquals("project-instance", start.projectInstanceId());
        assertEquals("file:///Demo.java", start.source().fileRef());
        assertEquals(42, start.source().documentVersion());
        assertEquals("JAVA", start.source().languageId());
        assertEquals("value = \"😀\";", start.selectedText());
        assertEquals(2, start.selection().startLine());
        assertEquals(10, start.selection().startColumn());
        assertEquals(2, start.selection().endLine());
        assertEquals(23, start.selection().endColumn());
    }

    @Test
    void repeatedCaptureOfTheSameTextGetsANewIdentity() {
        SelectionStart first = CaptureSelectionAction.freezeSelection(
                "project-instance", "file:///Demo.java", 7, "JAVA", "abc", 0, 3);
        SelectionStart second = CaptureSelectionAction.freezeSelection(
                "project-instance", "file:///Demo.java", 7, "JAVA", "abc", 0, 3);

        assertNotEquals(first.captureId(), second.captureId());
        assertEquals(first.projectInstanceId(), second.projectInstanceId());
        assertEquals(first.source(), second.source());
        assertEquals(first.selectedText(), second.selectedText());
        assertEquals(first.selection(), second.selection());
    }

    @Test
    void rejectsEmptyOrOutOfBoundsSelection() {
        assertThrows(IllegalArgumentException.class,
                () -> CaptureSelectionAction.freezeSelection(
                        "project-instance", "file:///Demo.java", 1, "JAVA", "abc", 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> CaptureSelectionAction.freezeSelection(
                        "project-instance", "file:///Demo.java", 1, "JAVA", "abc", 0, 4));
    }
}
