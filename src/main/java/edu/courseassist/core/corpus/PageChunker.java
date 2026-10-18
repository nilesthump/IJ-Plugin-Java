package edu.courseassist.core.corpus;

import edu.courseassist.contract.PdfContracts.PdfPage;
import java.util.ArrayList;
import java.util.List;
import static edu.courseassist.core.corpus.CorpusData.*;

public final class PageChunker {
    private PageChunker() {}
    public record Slice(int physicalPage, int start, int end, String text) {}
    public static List<Slice> split(List<PdfPage> pages, Processing config) {
        List<Slice> result = new ArrayList<>();
        for (PdfPage page : pages) {
            String text = page.text();
            if (text.isBlank()) continue;
            for (int start = 0; start < text.length();) {
                int end = boundary(text, Math.min(text.length(), start + config.maxChars()));
                String body = text.substring(start, end);
                if (!body.isBlank()) result.add(new Slice(page.physicalPage(), start, end, body));
                if (end == text.length()) break;
                int next = boundary(text, end - config.overlapChars());
                start = next > start ? next : end;
            }
        }
        return List.copyOf(result);
    }
    private static int boundary(String text, int offset) {
        return offset > 0 && offset < text.length() && Character.isLowSurrogate(text.charAt(offset))
                && Character.isHighSurrogate(text.charAt(offset - 1)) ? offset - 1 : offset;
    }
}
