import java.nio.file.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;

/** Synthetic, redistributable manual-test data. No API calls or user course content. */
class GenerateBlackBoxFixtures {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output);
        pdf(output.resolve("course-a-v1.pdf"), "Course A. Revision ONE. Marker F0-ALPHA.",
                "F0-ALPHA: the lab retry interval is exactly 17 minutes.");
        pdf(output.resolve("course-a-v2.pdf"), "Course A. Revision TWO. Marker F0-BETA.",
                "F0-BETA: the lab retry interval is exactly 23 minutes.");
        pdf(output.resolve("course-b.pdf"), "Course B. Marker F0-GAMMA.",
                "F0-GAMMA: the lab retry interval is exactly 31 minutes.");
        Files.writeString(output.resolve("broken.pdf"), "This is intentionally not a valid PDF.\n");
        System.out.println("Generated three 3-page synthetic PDFs (blank physical page 2) and one invalid fixture.");
    }
    static void pdf(Path path, String first, String third) throws Exception {
        try (var document = new PDDocument()) {
            for (String text : new String[]{first, "", third}) {
                var page = new PDPage(); document.addPage(page);
                if (!text.isEmpty()) try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    stream.newLineAtOffset(40, 720); stream.showText(text); stream.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
