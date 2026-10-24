package edu.courseassist.ui;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Image;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** HTML is a disposable view of the exact Markdown source, never an archive or model input. */
public final class SafeMarkdown {
    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

    private SafeMarkdown() {}

    public static String render(String source) {
        return renderView(source, false).html();
    }

    static Rendered renderView(String source, boolean separateCode) {
        Parser parser = Parser.builder().extensions(EXTENSIONS).build();
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS)
                .escapeHtml(true).sanitizeUrls(true).build();
        Node document = parser.parse(Objects.requireNonNull(source, "source"));
        ArrayList<CodeBlock> codeBlocks = new ArrayList<>();
        sanitize(document, separateCode, codeBlocks);
        return new Rendered("<html><body>" + renderer.render(document) + "</body></html>",
                List.copyOf(codeBlocks));
    }

    private static void sanitize(Node parent, boolean separateCode, List<CodeBlock> blocks) {
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            if (child instanceof Image) replace(child, new Text("[图片未加载]"));
            else if (separateCode && child instanceof FencedCodeBlock code) {
                blocks.add(new CodeBlock(code.getLiteral(), language(code.getInfo())));
                replace(child, placeholder(blocks.size()));
            } else if (separateCode && child instanceof IndentedCodeBlock code) {
                blocks.add(new CodeBlock(code.getLiteral(), ""));
                replace(child, placeholder(blocks.size()));
            } else sanitize(child, separateCode, blocks);
            child = next;
        }
    }

    private static void replace(Node original, Node replacement) {
        original.insertBefore(replacement);
        original.unlink();
    }

    private static Paragraph placeholder(int number) {
        Paragraph result = new Paragraph();
        result.appendChild(new Text("代码块 " + number + "（下方使用 IDEA 高亮显示）"));
        return result;
    }

    private static String language(String info) {
        if (info == null || info.isBlank()) return "";
        return info.strip().split("\\s+", 2)[0];
    }

    record CodeBlock(String source, String language) {}
    record Rendered(String html, List<CodeBlock> codeBlocks) {}
}
