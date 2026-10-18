package edu.courseassist.core.corpus;

import edu.courseassist.contract.ApplicationContracts.CorpusBase;
import edu.courseassist.contract.ApplicationContracts.MaterialStatus;
import edu.courseassist.contract.ApplicationContracts.MaterialView;
import edu.courseassist.contract.PdfContracts.PdfPage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Manager-internal immutable corpus data. No provider defaults or credentials are stored here. */
public final class CorpusData {
    private CorpusData() {}

    public record EmbeddingSpace(String provider, URI endpoint, String model, int dimensions,
                                 String documentPrefix, String queryPrefix, String revision) {
        public EmbeddingSpace {
            text(provider); text(model); text(revision); Objects.requireNonNull(endpoint);
            Objects.requireNonNull(documentPrefix); Objects.requireNonNull(queryPrefix);
            if (!endpoint.isAbsolute() || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null || dimensions < 1) throw new IllegalArgumentException("Invalid embedding space");
        }
    }

    /** UTF-16 offsets into exact page text; cuts never split a surrogate pair. No cross-page chunks. */
    public record Processing(String readerRevision, int maxChars, int overlapChars) {
        public Processing {
            text(readerRevision);
            if (maxChars < 2 || overlapChars < 0 || overlapChars >= maxChars - 1)
                throw new IllegalArgumentException("Invalid page chunk configuration");
        }
    }

    /** Explicit measured/candidate parameters, bound to fixed JVector 4.0.1 COSINE semantics. */
    public record IndexSettings(int degree, int beamWidth, float overflow, float alpha) {
        public IndexSettings {
            if (degree < 2 || beamWidth < degree || !Float.isFinite(overflow) || overflow < 1
                    || !Float.isFinite(alpha) || alpha < 1) throw new IllegalArgumentException("Invalid graph parameters");
        }
    }

    public record Settings(EmbeddingSpace space, Processing processing, IndexSettings index) {
        public Settings { Objects.requireNonNull(space); Objects.requireNonNull(processing); Objects.requireNonNull(index); }
        public String derivationKey() { return hash(encode(new Settings(space, processing, new IndexSettings(2, 2, 1, 1)))); }
    }

    public record Source(String documentId, String versionId, String md5, String name) {
        public Source { text(documentId); text(versionId); text(md5); Objects.requireNonNull(name); }
    }

    public static final class Chunk {
        private final String id;
        private final int page, start, end;
        private final String text;
        private final float[] vector;
        public Chunk(String id, int page, int start, int end, String text, float[] vector, int dimensions) {
            CorpusData.text(id); Objects.requireNonNull(text);
            if (page < 1 || start < 0 || end <= start || end - start != text.length())
                throw new IllegalArgumentException("Invalid chunk range");
            validateVector(vector, dimensions);
            this.id = id; this.page = page; this.start = start; this.end = end; this.text = text; this.vector = vector.clone();
        }
        public String id() { return id; }
        public int page() { return page; }
        public int start() { return start; }
        public int end() { return end; }
        public String text() { return text; }
        public float[] vector() { return vector.clone(); }
    }

    public record Derived(Source source, String derivationId, Settings settings, List<PdfPage> pages, List<Chunk> chunks) {
        public Derived {
            Objects.requireNonNull(source); text(derivationId); Objects.requireNonNull(settings);
            pages = List.copyOf(pages); chunks = List.copyOf(chunks);
            for (int i = 0; i < pages.size(); i++) if (pages.get(i).physicalPage() != i + 1)
                throw new IllegalArgumentException("Missing physical page");
            Set<String> ids = new HashSet<>();
            for (Chunk c : chunks) {
                if (!ids.add(c.id()) || c.page() > pages.size() || c.end() > pages.get(c.page() - 1).text().length()
                        || !pages.get(c.page() - 1).text().substring(c.start(), c.end()).equals(c.text()))
                    throw new IllegalArgumentException("Chunk mapping does not match page text");
                validateVector(c.vector(), settings.space().dimensions());
            }
        }
        public String dataKey() {
            return digest(out -> {
                for (String value : List.of(source.documentId(), source.versionId(), source.md5(), source.name(), settings.derivationKey())) writeText(out, value);
                out.writeInt(pages.size());
                for (PdfPage p : pages) {
                    out.writeInt(p.physicalPage()); writeText(out, p.text()); writeText(out, p.status().name());
                    out.writeBoolean(p.printedLabel().isPresent()); if (p.printedLabel().isPresent()) writeText(out, p.printedLabel().get());
                }
                out.writeInt(chunks.size());
                for (Chunk c : chunks) {
                    writeText(out, c.id()); out.writeInt(c.page()); out.writeInt(c.start()); out.writeInt(c.end()); writeText(out, c.text());
                    for (float f : c.vector()) out.writeFloat(f);
                }
            });
        }
    }

    public record Generation(String id, Settings settings, List<Derived> members) {
        public Generation {
            text(id); Objects.requireNonNull(settings); members = List.copyOf(members);
            Set<String> docs = new HashSet<>(), chunks = new HashSet<>();
            for (Derived d : members) {
                if (!docs.add(d.source().documentId()) || !d.settings().derivationKey().equals(settings.derivationKey()))
                    throw new IllegalArgumentException("Mixed corpus space/processing or duplicate document");
                for (Chunk c : d.chunks()) if (!chunks.add(c.id())) throw new IllegalArgumentException("Duplicate chunk identity");
            }
        }
        public Set<String> sourceIds() {
            Set<String> ids = new HashSet<>(); members.forEach(d -> ids.add(d.source().versionId())); return Set.copyOf(ids);
        }
        public String dataKey() {
            return digest(out -> {
                writeText(out, id); out.write(encode(settings)); out.writeInt(members.size());
                for (Derived d : members) { writeText(out, d.derivationId()); writeText(out, d.dataKey()); }
            });
        }
    }

    public record Evidence(String generationId, String chunkId, Source source, int physicalPage,
                           int startOffset, int endOffset, String text, float score) {}
    public record SearchSettings(int topK, float minimumScore) {
        public SearchSettings {
            if (topK < 1 || !Float.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1)
                throw new IllegalArgumentException("Score is JVector COSINE (1 + cosine) / 2 in [0,1]");
        }
    }
    public enum Availability { EMPTY, RESTORING, READY, FAILED }
    public record Snapshot(CorpusBase base, Availability availability, List<MaterialView> materials, List<JobState> jobs) {
        public Snapshot { materials = List.copyOf(materials); jobs = List.copyOf(jobs); }
    }
    public record JobState(String id, String documentId, String sourceId, String state) {}
    public enum Disposition { PUBLISHED, DUPLICATE_READY, EXISTS_NOT_READY, REINDEX_REQUIRED, UNCHANGED }
    public record Change(Disposition disposition, String documentId, Optional<String> sourceId, CorpusBase base) {}
    public record CleanupPreview(CorpusBase base, Set<String> sourceIds, Set<String> blobMd5s) {
        public CleanupPreview { sourceIds = Set.copyOf(sourceIds); blobMd5s = Set.copyOf(blobMd5s); }
    }
    public static final class Conflict extends IllegalStateException { public Conflict(String message) { super(message); } }

    public static void validateVector(float[] vector, int dimensions) {
        if (vector == null || vector.length != dimensions) throw new IllegalArgumentException("Embedding dimensions differ from space");
        double norm = 0;
        for (float v : vector) {
            if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite vector");
            norm += (double) v * v;
        }
        if (!(norm > 0) || !Double.isFinite(norm)) throw new IllegalArgumentException("Zero or invalid vector norm");
    }

    public static byte[] encode(Settings settings) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeInt(1); var s = settings.space();
            for (String value : List.of(s.provider(), s.endpoint().toString(), s.model(), s.documentPrefix(), s.queryPrefix(), s.revision())) writeText(out, value);
            out.writeInt(s.dimensions()); writeText(out, settings.processing().readerRevision());
            out.writeInt(settings.processing().maxChars()); out.writeInt(settings.processing().overlapChars());
            var index = settings.index(); out.writeInt(index.degree()); out.writeInt(index.beamWidth()); out.writeFloat(index.overflow()); out.writeFloat(index.alpha());
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    public static Settings decode(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("Unsupported corpus settings version");
            var s = new EmbeddingSpace(readText(in), URI.create(readText(in)), readText(in), 1, readText(in), readText(in), readText(in));
            var space = new EmbeddingSpace(s.provider(), s.endpoint(), s.model(), in.readInt(), s.documentPrefix(), s.queryPrefix(), s.revision());
            var result = new Settings(space, new Processing(readText(in), in.readInt(), in.readInt()), new IndexSettings(in.readInt(), in.readInt(), in.readFloat(), in.readFloat()));
            if (in.available() != 0) throw new IOException("Unexpected settings bytes");
            return result;
        }
    }
    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] b = value.getBytes(StandardCharsets.UTF_8); out.writeInt(b.length); out.write(b);
    }
    private static String readText(DataInputStream in) throws IOException {
        int size = in.readInt(); if (size < 0 || size > in.available()) throw new IOException("Corrupt settings text");
        return new String(in.readNBytes(size), StandardCharsets.UTF_8);
    }
    private static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @FunctionalInterface private interface DigestWriter { void write(DataOutputStream out) throws IOException; }
    private static String digest(DigestWriter writer) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var out = new DataOutputStream(new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest))) { writer.write(out); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void text(String value) { if (Objects.requireNonNull(value).isBlank()) throw new IllegalArgumentException("Blank identity"); }
}
