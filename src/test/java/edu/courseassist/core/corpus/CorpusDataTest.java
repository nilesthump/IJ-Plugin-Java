package edu.courseassist.core.corpus;

import edu.courseassist.contract.PdfContracts.*;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import static edu.courseassist.core.corpus.CorpusData.*;
import static org.junit.jupiter.api.Assertions.*;

class CorpusDataTest {
    @Test void pageChunksPreserveRawOffsetsBlankPagesAndSurrogatePairs() {
        String text = " a😀bcdef\n ";
        var pages = List.of(new PdfPage(1, text, PageTextStatus.TEXT, Optional.of("iv")),
                new PdfPage(2, " \n", PageTextStatus.EMPTY, Optional.empty()),
                new PdfPage(3, "last", PageTextStatus.TEXT, Optional.empty()));
        var chunks = PageChunker.split(pages, new Processing("synthetic-reader", 3, 1));
        assertTrue(chunks.stream().anyMatch(c -> c.physicalPage() == 3));
        assertFalse(chunks.stream().anyMatch(c -> c.physicalPage() == 2));
        for (var c : chunks) {
            assertEquals(pages.get(c.physicalPage() - 1).text().substring(c.start(), c.end()), c.text());
            assertFalse(Character.isLowSurrogate(c.text().charAt(0)));
            assertFalse(Character.isHighSurrogate(c.text().charAt(c.text().length() - 1)));
        }
        assertTrue(chunks.stream().anyMatch(c -> c.text().contains("😀")));
    }
    @Test void fullSpaceAndProcessingIdentityRoundTripAndRejectInvalidVectors() throws Exception {
        var space = new EmbeddingSpace("synthetic", URI.create("https://example.invalid"), "model-a", 3, "doc:", "query:", "r1");
        var settings = new Settings(space, new Processing("reader-r1", 200, 10), new IndexSettings(2, 8, 1.5f, 1.2f));
        assertEquals(settings, decode(encode(settings)));
        assertNotEquals(settings.derivationKey(), new Settings(new EmbeddingSpace("synthetic", space.endpoint(), "model-b", 3, "doc:", "query:", "r1"), settings.processing(), settings.index()).derivationKey());
        assertNotEquals(settings.derivationKey(), new Settings(space, new Processing("reader-r2", 200, 10), settings.index()).derivationKey());
        assertThrows(IllegalArgumentException.class, () -> validateVector(new float[]{0, 0, 0}, 3));
        assertThrows(IllegalArgumentException.class, () -> validateVector(new float[]{Float.NaN, 1, 0}, 3));
        assertThrows(IllegalArgumentException.class, () -> validateVector(new float[]{1, 0}, 3));
        float[] original = {1, 0, 0}; var chunk = new Chunk("c", 1, 0, 3, "raw", original, 3);
        original[0] = 9; chunk.vector()[0] = 8; assertEquals(1, chunk.vector()[0]);
    }
    @Test void overlapAroundAdjacentSupplementaryCharactersAlwaysAdvancesAtCodePointBoundary() {
        String text = "😀😀x";
        var slices = PageChunker.split(List.of(new PdfPage(1, text, PageTextStatus.TEXT, Optional.empty())), new Processing("reader", 3, 1));
        assertEquals(List.of("😀", "😀x"), slices.stream().map(PageChunker.Slice::text).toList());
        assertEquals(List.of(0, 2), slices.stream().map(PageChunker.Slice::start).toList());
    }
}
