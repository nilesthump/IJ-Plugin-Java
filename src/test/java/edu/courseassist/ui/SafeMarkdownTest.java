package edu.courseassist.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeMarkdownTest {
    @Test
    void keepsUsefulMarkdownButEscapesHtmlAndNeverEmitsImages() {
        String html = SafeMarkdown.render("# 标题\n\n- 列表\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n"
                + "<script>alert('x')</script>\n\n![remote](https://example.invalid/image.png)\n\n"
                + "[unsafe](javascript:alert(1))\n\n```java\nint x = 1;\n");
        assertTrue(html.contains("<table>"));
        assertTrue(html.contains("<li>"));
        assertTrue(html.contains("int x = 1;"));
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("image.png"));
        assertFalse(html.contains("javascript:"));
    }

    @Test
    void separatesIncompleteJavaFenceWithoutChangingSource() {
        String original = "说明\n\n```java\nclass Demo {\n";
        SafeMarkdown.Rendered rendered = SafeMarkdown.renderView(original, true);
        assertEquals(1, rendered.codeBlocks().size());
        assertEquals("java", rendered.codeBlocks().get(0).language());
        assertTrue(rendered.codeBlocks().get(0).source().contains("class Demo"));
        assertFalse(rendered.html().contains("class Demo"));
        assertTrue(rendered.html().contains("代码块 1"));
        assertEquals("说明\n\n```java\nclass Demo {\n", original);
    }
}
