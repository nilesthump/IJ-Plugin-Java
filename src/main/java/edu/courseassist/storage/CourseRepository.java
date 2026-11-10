package edu.courseassist.storage;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.PdfContracts.*;
import edu.courseassist.core.corpus.CorpusData;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static edu.courseassist.core.corpus.CorpusData.*;

/** Corpus schema v1. Every method is used on a background thread; all JDBC goes through H2Store.
 * No transaction spans file copying, PDF, embedding, graph construction or remote waiting. */
public final class CourseRepository {
    private final H2Store store;
    public CourseRepository(H2Store store) { this.store = Objects.requireNonNull(store); }
    private <T> T tx(H2Store.Transaction<T> action) { return store.transaction(action).join(); }
    public void initialize() {
        tx(c -> {
            // DDL may auto-commit in H2; version is installed last and never overwritten on reopen.
            sql(c, "CREATE TABLE IF NOT EXISTS ca_corpus_meta(singleton INT PRIMARY KEY, schema_version INT NOT NULL, revision BIGINT NOT NULL, generation_id VARCHAR)");
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT schema_version FROM ca_corpus_meta WHERE singleton=1")) {
                if (r.next() && r.getInt(1) != 1) throw new SQLException("Unsupported corpus schema version");
            }
            sql(c, "CREATE TABLE IF NOT EXISTS ca_blob(md5 VARCHAR PRIMARY KEY, byte_size BIGINT NOT NULL, deleting BOOLEAN NOT NULL)");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_document(id VARCHAR PRIMARY KEY, name VARCHAR NOT NULL, revision BIGINT NOT NULL, active_source VARCHAR, status VARCHAR NOT NULL, changed_at BIGINT NOT NULL)");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_source(id VARCHAR PRIMARY KEY, document_id VARCHAR NOT NULL REFERENCES ca_document(id), md5 VARCHAR NOT NULL REFERENCES ca_blob(md5), name VARCHAR NOT NULL, UNIQUE(document_id,md5))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_derivation(id VARCHAR PRIMARY KEY, source_id VARCHAR NOT NULL REFERENCES ca_source(id), config_key VARCHAR NOT NULL, settings BLOB NOT NULL, page_count INT NOT NULL, chunk_count INT NOT NULL, data_hash VARCHAR NOT NULL, UNIQUE(source_id,config_key))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_page(derivation_id VARCHAR NOT NULL REFERENCES ca_derivation(id) ON DELETE CASCADE, page_no INT NOT NULL, body CLOB NOT NULL, page_status VARCHAR NOT NULL, label VARCHAR, PRIMARY KEY(derivation_id,page_no))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_chunk(id VARCHAR PRIMARY KEY, derivation_id VARCHAR NOT NULL REFERENCES ca_derivation(id) ON DELETE CASCADE, position INT NOT NULL, page_no INT NOT NULL, start_offset INT NOT NULL, end_offset INT NOT NULL, body CLOB NOT NULL, vector_data BLOB NOT NULL, UNIQUE(derivation_id,position))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_generation(id VARCHAR PRIMARY KEY, settings BLOB NOT NULL, member_count INT NOT NULL, chunk_count INT NOT NULL, data_hash VARCHAR NOT NULL)");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_member(generation_id VARCHAR NOT NULL REFERENCES ca_generation(id) ON DELETE CASCADE, position INT NOT NULL, derivation_id VARCHAR NOT NULL REFERENCES ca_derivation(id), PRIMARY KEY(generation_id,position), UNIQUE(generation_id,derivation_id))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_ordinal(generation_id VARCHAR NOT NULL REFERENCES ca_generation(id) ON DELETE CASCADE, ordinal INT NOT NULL, chunk_id VARCHAR NOT NULL REFERENCES ca_chunk(id), PRIMARY KEY(generation_id,ordinal))");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_corpus_job(id VARCHAR PRIMARY KEY, document_id VARCHAR NOT NULL, source_id VARCHAR NOT NULL, state VARCHAR NOT NULL)");
            sql(c, "CREATE TABLE IF NOT EXISTS ca_source_ref(owner_id VARCHAR NOT NULL, source_id VARCHAR NOT NULL REFERENCES ca_source(id), PRIMARY KEY(owner_id,source_id))");
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT (SELECT COUNT(*) FROM ca_corpus_meta),(SELECT COUNT(*) FROM ca_document)")) {
                r.next(); if (r.getInt(1) == 0 && r.getInt(2) > 0) throw new SQLException("Corpus root missing from existing catalog");
            }
            update(c, "INSERT INTO ca_corpus_meta SELECT 1,1,0,NULL WHERE NOT EXISTS (SELECT 1 FROM ca_corpus_meta WHERE singleton=1)");
            // No task resumes network work after restart. Old ready catalog/generation stays unchanged.
            update(c, "UPDATE ca_document SET status='FAILED' WHERE status='PREPARING' AND active_source IS NULL");
            update(c, "UPDATE ca_corpus_job SET state='INTERRUPTED' WHERE state='PREPARING'");
            List<String> removed = new ArrayList<>();
            try (var statement = c.createStatement(); var rows = statement.executeQuery("SELECT id FROM ca_document WHERE status='REMOVED'")) {
                while (rows.next()) removed.add(rows.getString(1));
            }
            for (String document : removed) deleteDocument(c, document);
            if (!removed.isEmpty()) update(c, "UPDATE ca_corpus_meta SET revision=revision+1 WHERE singleton=1");
            var base = base(c); Map<String,String> catalog = new HashMap<>(), generation = new HashMap<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT id,active_source FROM ca_document WHERE status='READY'")) {
                while (r.next()) { if (r.getString(2) == null) throw new SQLException("Ready material has no source"); catalog.put(r.getString(1), r.getString(2)); }
            }
            if (base.generationId().isPresent()) {
                try (var p = c.prepareStatement("SELECT s.document_id,s.id FROM ca_member m JOIN ca_derivation d ON d.id=m.derivation_id JOIN ca_source s ON s.id=d.source_id WHERE m.generation_id=?")) {
                    p.setString(1, base.generationId().get()); try (var r = p.executeQuery()) {
                        while (r.next()) if (generation.put(r.getString(1), r.getString(2)) != null) throw new SQLException("Duplicate active document");
                    }
                }
            }
            if (!catalog.equals(generation)) throw new SQLException("Catalog and active generation differ");
            return null;
        });
    }
    public CorpusBase base() { return tx(CourseRepository::base); }
    private static CorpusBase base(Connection c) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT revision,generation_id FROM ca_corpus_meta WHERE singleton=1")) {
            if (!r.next()) throw new SQLException("Missing corpus root");
            return new CorpusBase(r.getLong(1), Optional.ofNullable(r.getString(2)));
        }
    }
    private static void expectBase(Connection c, CorpusBase expected) throws SQLException {
        if (!base(c).equals(expected)) throw new Conflict("Corpus base changed; prepare against latest generation");
    }
    private static MaterialView material(Connection c, String id) throws SQLException {
        return materials(c).stream().filter(m -> m.documentId().equals(id)).findFirst().orElseThrow(() -> new Conflict("Material does not exist"));
    }
    private static void expectTarget(Connection c, MaterialTarget t) throws SQLException {
        var m = material(c, t.documentId());
        if (m.materialRevision() != t.expectedMaterialRevision() || !m.sourceVersionId().equals(t.expectedSourceVersionId()) || m.status() == MaterialStatus.REMOVED)
            throw new Conflict("Material version/revision changed or removed");
    }
    /** Remove an unpublished row without inventing or rebuilding a retrieval generation. */
    public CorpusBase removeUnpublished(CorpusBase expected, MaterialTarget target) {
        return tx(c -> {
            expectBase(c, expected); expectTarget(c, target);
            if (material(c, target.documentId()).sourceVersionId().isPresent())
                throw new Conflict("Material is already published");
            deleteDocument(c, target.documentId());
            // Fence candidates prepared before this removal, including first-import jobs.
            update(c, "UPDATE ca_corpus_meta SET revision=revision+1 WHERE singleton=1");
            return base(c);
        });
    }
    public List<MaterialView> materials() { return tx(CourseRepository::materials); }
    private static List<MaterialView> materials(Connection c) throws SQLException {
        List<MaterialView> result = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM ca_document ORDER BY id")) {
            while (r.next()) {
                String active = r.getString("active_source"); Optional<Integer> pages = Optional.empty();
                if (active != null) {
                    try (var p = c.prepareStatement("SELECT COUNT(*) FROM ca_page p JOIN ca_derivation d ON d.id=p.derivation_id JOIN ca_member m ON m.derivation_id=d.id WHERE d.source_id=? AND m.generation_id=?")) {
                        p.setString(1, active); p.setString(2, base(c).generationId().orElse(null));
                        try (var n = p.executeQuery()) { n.next(); pages = Optional.of(n.getInt(1)); }
                    }
                }
                result.add(new MaterialView(r.getString("id"), r.getString("name"), r.getLong("revision"), Optional.ofNullable(active),
                        MaterialStatus.valueOf(r.getString("status")), pages, Instant.ofEpochMilli(r.getLong("changed_at")), Optional.empty()));
            }
        }
        return List.copyOf(result);
    }
    public List<JobState> jobs() {
        return tx(c -> {
            List<JobState> result = new ArrayList<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM ca_corpus_job ORDER BY id")) {
                while (r.next()) result.add(new JobState(r.getString(1), r.getString(2), r.getString(3), r.getString(4)));
            }
            return List.copyOf(result);
        });
    }
    public record Reservation(Source source, Optional<Change> duplicate) {}
    public Reservation reserve(String job, String name, ManagedPdfStore.Blob blob, CorpusBase expected,
                               Optional<MaterialTarget> target, Settings settings) {
        return tx(c -> {
            expectBase(c, expected);
            if (target.isPresent()) expectTarget(c, target.get());
            else {
                try (var p = c.prepareStatement("SELECT s.document_id,s.id,d.status FROM ca_source s JOIN ca_document d ON s.document_id=d.id WHERE s.md5=? AND d.status<>'REMOVED' ORDER BY CASE WHEN d.active_source=s.id THEN 0 ELSE 1 END,s.id")) {
                    p.setString(1, blob.md5());
                    try (var r = p.executeQuery()) {
                        if (r.next()) {
                            var source = source(c, r.getString(2)); var m = material(c, source.documentId());
                            Disposition disposition = Disposition.EXISTS_NOT_READY;
                            if (m.status() == MaterialStatus.READY && m.sourceVersionId().equals(Optional.of(source.versionId()))) {
                                Generation active = load(c, expected.generationId().orElseThrow());
                                disposition = active.settings().derivationKey().equals(settings.derivationKey()) ? Disposition.DUPLICATE_READY : Disposition.REINDEX_REQUIRED;
                            }
                            return new Reservation(source, Optional.of(new Change(disposition, source.documentId(), Optional.of(source.versionId()), expected)));
                        }
                    }
                }
            }
            try (var p = c.prepareStatement("SELECT byte_size,deleting FROM ca_blob WHERE md5=?")) {
                p.setString(1, blob.md5());
                try (var r = p.executeQuery()) {
                    if (r.next()) {
                        if (r.getBoolean(2) || r.getLong(1) != blob.size()) throw new Conflict("Blob pending cleanup or size mismatch");
                    } else update(c, "INSERT INTO ca_blob VALUES(?,?,FALSE)", blob.md5(), blob.size());
                }
            }
            String doc = target.map(MaterialTarget::documentId).orElseGet(CourseRepository::id);
            if (target.isEmpty()) update(c, "INSERT INTO ca_document VALUES(?,?,0,NULL,'PREPARING',?)", doc, name, System.currentTimeMillis());
            Source source;
            try (var p = c.prepareStatement("SELECT id FROM ca_source WHERE document_id=? AND md5=?")) {
                p.setString(1, doc); p.setString(2, blob.md5());
                try (var r = p.executeQuery()) {
                    if (r.next()) source = source(c, r.getString(1));
                    else {
                        source = new Source(doc, id(), blob.md5(), name);
                        update(c, "INSERT INTO ca_source VALUES(?,?,?,?)", source.versionId(), doc, blob.md5(), name);
                    }
                }
            }
            update(c, "INSERT INTO ca_corpus_job VALUES(?,?,?,'PREPARING')", job, doc, source.versionId());
            return new Reservation(source, Optional.empty());
        });
    }
    public ManagedPdfStore.Blob blob(String sourceId) {
        return tx(c -> {
            try (var p = c.prepareStatement("SELECT s.md5,b.byte_size,b.deleting FROM ca_source s JOIN ca_blob b ON s.md5=b.md5 WHERE s.id=?")) {
                p.setString(1, sourceId); try (var r = p.executeQuery()) {
                    if (!r.next() || r.getBoolean(3)) throw new SQLException("Source blob unavailable");
                    return new ManagedPdfStore.Blob(r.getString(1), r.getLong(2));
                }
            }
        });
    }
    public Source source(String sourceId) { return tx(c -> source(c, sourceId)); }
    private static Source source(Connection c, String sourceId) throws SQLException {
        try (var p = c.prepareStatement("SELECT * FROM ca_source WHERE id=?")) {
            p.setString(1, sourceId); try (var r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Missing source version");
                return new Source(r.getString("document_id"), sourceId, r.getString("md5"), r.getString("name"));
            }
        }
    }
    public Optional<Derived> findDerived(String sourceId, Settings settings) {
        return tx(c -> {
            try (var p = c.prepareStatement("SELECT id FROM ca_derivation WHERE source_id=? AND config_key=?")) {
                p.setString(1, sourceId); p.setString(2, settings.derivationKey());
                try (var r = p.executeQuery()) { return r.next() ? Optional.of(derived(c, r.getString(1))) : Optional.empty(); }
            }
        });
    }
    public Derived saveDerived(Derived value) {
        return tx(c -> {
            try (var p = c.prepareStatement("SELECT id FROM ca_derivation WHERE source_id=? AND config_key=?")) {
                p.setString(1, value.source().versionId()); p.setString(2, value.settings().derivationKey());
                try (var r = p.executeQuery()) { if (r.next()) return derived(c, r.getString(1)); }
            }
            update(c, "INSERT INTO ca_derivation VALUES(?,?,?,?,?,?,?)", value.derivationId(), value.source().versionId(), value.settings().derivationKey(), encode(value.settings()), value.pages().size(), value.chunks().size(), value.dataKey());
            for (PdfPage page : value.pages()) update(c, "INSERT INTO ca_page VALUES(?,?,?,?,?)", value.derivationId(), page.physicalPage(), page.text(), page.status().name(), page.printedLabel().orElse(null));
            int position = 0;
            for (Chunk chunk : value.chunks()) update(c, "INSERT INTO ca_chunk VALUES(?,?,?,?,?,?,?,?)", chunk.id(), value.derivationId(), position++, chunk.page(), chunk.start(), chunk.end(), chunk.text(), vectorBytes(chunk.vector()));
            return value;
        });
    }
    private static Derived derived(Connection c, String id) throws Exception {
        Source source; Settings settings; int pageCount, chunkCount; String dataHash;
        try (var p = c.prepareStatement("SELECT source_id,settings,config_key,page_count,chunk_count,data_hash FROM ca_derivation WHERE id=?")) {
            p.setString(1, id); try (var r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Missing derivation");
                source = source(c, r.getString(1)); settings = decode(r.getBytes(2));
                if (!settings.derivationKey().equals(r.getString(3))) throw new SQLException("Derivation configuration mismatch");
                pageCount = r.getInt(4); chunkCount = r.getInt(5);
                dataHash = r.getString(6);
            }
        }
        List<PdfPage> pages = new ArrayList<>(); List<Chunk> chunks = new ArrayList<>();
        try (var p = c.prepareStatement("SELECT * FROM ca_page WHERE derivation_id=? ORDER BY page_no")) {
            p.setString(1, id); try (var r = p.executeQuery()) {
                while (r.next()) pages.add(new PdfPage(r.getInt("page_no"), r.getString("body"), PageTextStatus.valueOf(r.getString("page_status")), Optional.ofNullable(r.getString("label"))));
            }
        }
        try (var p = c.prepareStatement("SELECT * FROM ca_chunk WHERE derivation_id=? ORDER BY position")) {
            p.setString(1, id); try (var r = p.executeQuery()) {
                while (r.next()) {
                    if (r.getInt("position") != chunks.size()) throw new SQLException("Chunk order incomplete");
                    chunks.add(new Chunk(r.getString("id"), r.getInt("page_no"), r.getInt("start_offset"), r.getInt("end_offset"), r.getString("body"), vector(r.getBytes("vector_data"), settings.space().dimensions()), settings.space().dimensions()));
                }
            }
        }
        if (pages.size() != pageCount || chunks.size() != chunkCount) throw new SQLException("Incomplete derived pages/chunks");
        var value = new Derived(source, id, settings, pages, chunks);
        if (!value.dataKey().equals(dataHash)) throw new SQLException("Derived content/vector fingerprint mismatch");
        return value;
    }
    public Generation load(String id) { return tx(c -> load(c, id)); }
    private static Generation load(Connection c, String id) throws Exception {
        Settings settings; int memberCount, chunkCount; String dataHash;
        try (var p = c.prepareStatement("SELECT settings,member_count,chunk_count,data_hash FROM ca_generation WHERE id=?")) {
            p.setString(1, id); try (var r = p.executeQuery()) { if (!r.next()) throw new SQLException("Missing generation"); settings = decode(r.getBytes(1)); memberCount = r.getInt(2); chunkCount = r.getInt(3); dataHash = r.getString(4); }
        }
        List<Derived> members = new ArrayList<>();
        try (var p = c.prepareStatement("SELECT position,derivation_id FROM ca_member WHERE generation_id=? ORDER BY position")) {
            p.setString(1, id); try (var r = p.executeQuery()) {
                while (r.next()) { if (r.getInt(1) != members.size()) throw new SQLException("Member order incomplete"); members.add(derived(c, r.getString(2))); }
            }
        }
        List<String> chunks = members.stream().flatMap(d -> d.chunks().stream()).map(Chunk::id).toList();
        if (memberCount != members.size() || chunkCount != chunks.size()) throw new SQLException("Incomplete generation");
        try (var p = c.prepareStatement("SELECT ordinal,chunk_id FROM ca_ordinal WHERE generation_id=? ORDER BY ordinal")) {
            p.setString(1, id); try (var r = p.executeQuery()) {
                int i = 0;
                while (r.next()) {
                    if (i >= chunks.size() || r.getInt(1) != i || !chunks.get(i).equals(r.getString(2))) throw new SQLException("Ordinal/source mapping damaged");
                    i++;
                }
                if (i != chunks.size()) throw new SQLException("Ordinal mapping incomplete");
            }
        }
        var generation = new Generation(id, settings, members);
        if (!generation.dataKey().equals(dataHash)) throw new SQLException("Generation fingerprint mismatch");
        return generation;
    }
    /** Caller holds its publication/cancellation fence until commit and matching memory swap finish. */
    public CorpusBase publish(Generation g, CorpusBase expected, Optional<MaterialTarget> target,
                              Optional<Source> changed, boolean remove, Optional<String> job) {
        return tx(c -> {
            expectBase(c, expected); if (target.isPresent()) expectTarget(c, target.get());
            if (job.isPresent()) {
                try (var p = c.prepareStatement("SELECT state FROM ca_corpus_job WHERE id=?")) {
                    p.setString(1, job.get()); try (var r = p.executeQuery()) {
                        if (!r.next() || !r.getString(1).equals("PREPARING")) throw new Conflict("Preparation job invalidated");
                    }
                }
            }
            Set<String> wanted = new HashSet<>();
            for (var m : materials(c)) if (m.status() == MaterialStatus.READY) wanted.add(m.documentId());
            if (changed.isPresent()) wanted.add(changed.get().documentId());
            if (remove) wanted.remove(target.orElseThrow().documentId());
            if (!wanted.equals(g.members().stream().map(d -> d.source().documentId()).collect(java.util.stream.Collectors.toSet())))
                throw new Conflict("Candidate loses active materials");
            for (Derived d : g.members()) {
                Optional<String> expectedSource = changed.filter(s -> s.documentId().equals(d.source().documentId())).map(Source::versionId);
                if (expectedSource.isEmpty()) expectedSource = material(c, d.source().documentId()).sourceVersionId();
                if (!expectedSource.equals(Optional.of(d.source().versionId()))) throw new Conflict("Candidate source changed");
            }
            update(c, "INSERT INTO ca_generation VALUES(?,?,?,?,?)", g.id(), encode(g.settings()), g.members().size(), g.members().stream().mapToInt(d -> d.chunks().size()).sum(), g.dataKey());
            int position = 0, ordinal = 0;
            for (Derived d : g.members()) {
                // Match the immutable, committed derivation descriptor to the constructed graph candidate.
                try (var p = c.prepareStatement("SELECT source_id,config_key,data_hash FROM ca_derivation WHERE id=?")) {
                    p.setString(1, d.derivationId()); try (var r = p.executeQuery()) {
                        if (!r.next() || !r.getString(1).equals(d.source().versionId()) || !r.getString(2).equals(g.settings().derivationKey()) || !r.getString(3).equals(d.dataKey()))
                            throw new SQLException("Undurable candidate");
                    }
                }
                update(c, "INSERT INTO ca_member VALUES(?,?,?)", g.id(), position++, d.derivationId());
                for (Chunk chunk : d.chunks()) update(c, "INSERT INTO ca_ordinal VALUES(?,?,?)", g.id(), ordinal++, chunk.id());
            }
            if (changed.isPresent()) {
                Source s = changed.get(); update(c, "UPDATE ca_document SET active_source=?,name=?,status='READY',revision=revision+1,changed_at=? WHERE id=?", s.versionId(), s.name(), System.currentTimeMillis(), s.documentId());
            }

            update(c, "UPDATE ca_corpus_meta SET revision=revision+1,generation_id=? WHERE singleton=1", g.id());
            if (remove) deleteDocument(c, target.orElseThrow().documentId());
            if (job.isPresent()) update(c, "UPDATE ca_corpus_job SET state='PUBLISHED' WHERE id=?", job.get());
            return base(c);
        });
    }
    /** Deletes every version and dependent payload; chat archives are deliberately separate. */
    private static void deleteDocument(Connection c, String document) throws SQLException {
        update(c, "DELETE FROM ca_generation WHERE id<>COALESCE((SELECT generation_id FROM ca_corpus_meta WHERE singleton=1),'') AND id IN (SELECT m.generation_id FROM ca_member m JOIN ca_derivation d ON d.id=m.derivation_id JOIN ca_source s ON s.id=d.source_id WHERE s.document_id=?)", document);
        update(c, "DELETE FROM ca_source_ref WHERE source_id IN (SELECT id FROM ca_source WHERE document_id=?)", document);
        update(c, "DELETE FROM ca_derivation WHERE source_id IN (SELECT id FROM ca_source WHERE document_id=?)", document);
        update(c, "DELETE FROM ca_corpus_job WHERE document_id=?", document);
        update(c, "UPDATE ca_blob SET deleting=TRUE WHERE md5 IN (SELECT md5 FROM ca_source WHERE document_id=?) AND md5 NOT IN (SELECT md5 FROM ca_source WHERE document_id<>?)", document, document);
        update(c, "DELETE FROM ca_source WHERE document_id=?", document);
        update(c, "DELETE FROM ca_document WHERE id=?", document);
    }
    public Set<String> generationSources(String generation) {
        return tx(c -> {
            Set<String> result=new HashSet<>();
            try(var p=c.prepareStatement("SELECT d.source_id FROM ca_member m JOIN ca_derivation d ON d.id=m.derivation_id WHERE m.generation_id=?")) {
                p.setString(1,generation); try(var r=p.executeQuery()) { while(r.next()) result.add(r.getString(1)); }
            }
            return Set.copyOf(result);
        });
    }
    public Set<String> documentSources(String document) {
        return tx(c -> {
            Set<String> result = new HashSet<>();
            try (var p = c.prepareStatement("SELECT id FROM ca_source WHERE document_id=?")) {
                p.setString(1, document); try (var r = p.executeQuery()) { while(r.next()) result.add(r.getString(1)); }
            }
            return Set.copyOf(result);
        });
    }
    public Set<String> deletingBlobs() {
        return tx(c -> {
            Set<String> result = new HashSet<>();
            try (var statement=c.createStatement(); var r=statement.executeQuery("SELECT md5 FROM ca_blob WHERE deleting=TRUE")) {
                while(r.next()) result.add(r.getString(1));
            }
            return Set.copyOf(result);
        });
    }
    public boolean hasSource(String source) {
        return tx(c -> {
            try (var p=c.prepareStatement("SELECT 1 FROM ca_source WHERE id=?")) {
                p.setString(1,source); try(var r=p.executeQuery()) { return r.next(); }
            }
        });
    }
    public void fail(String job, String state) {
        tx(c -> {
            update(c, "UPDATE ca_corpus_job SET state=? WHERE id=? AND state='PREPARING'", state, job);
            update(c, "UPDATE ca_document SET status='FAILED' WHERE active_source IS NULL AND status='PREPARING' AND id IN (SELECT document_id FROM ca_corpus_job WHERE id=?)", job);
            return null;
        });
    }
    public void retain(String owner, Set<String> sources) {
        tx(c -> { for (String source : sources) update(c, "MERGE INTO ca_source_ref KEY(owner_id,source_id) VALUES(?,?)", owner, source); return null; });
    }
    public void release(String owner) { tx(c -> { update(c, "DELETE FROM ca_source_ref WHERE owner_id=?", owner); return null; }); }
    public Set<String> cleanable(Set<String> pinned) {
        return tx(c -> cleanable(c, pinned));
    }
    private static Set<String> cleanable(Connection c, Set<String> pinned) throws SQLException {
        Set<String> ids = new HashSet<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT id FROM ca_source WHERE id NOT IN (SELECT active_source FROM ca_document WHERE active_source IS NOT NULL) AND id NOT IN (SELECT source_id FROM ca_source_ref) AND id NOT IN (SELECT source_id FROM ca_corpus_job WHERE state='PREPARING')")) {
            while (r.next()) if (!pinned.contains(r.getString(1))) ids.add(r.getString(1));
        }
        return Set.copyOf(ids);
    }
    /** Cross-medium cleanup records remain until the corresponding file has really been removed. */
    public Set<String> retire(CleanupPreview preview, Set<String> pinned) {
        return tx(c -> {
            expectBase(c, preview.base());
            if (!cleanable(c, pinned).containsAll(preview.sourceIds())) throw new Conflict("Cleanup references changed; preview again");
            for (String source : preview.sourceIds()) {
                update(c, "DELETE FROM ca_generation WHERE id<>COALESCE((SELECT generation_id FROM ca_corpus_meta WHERE singleton=1),'') AND id IN (SELECT m.generation_id FROM ca_member m JOIN ca_derivation d ON m.derivation_id=d.id WHERE d.source_id=?)", source);
                update(c, "DELETE FROM ca_derivation WHERE source_id=?", source);
                update(c, "DELETE FROM ca_corpus_job WHERE source_id=?", source);
                update(c, "DELETE FROM ca_source WHERE id=?", source);
            }
            update(c, "UPDATE ca_blob SET deleting=TRUE WHERE md5 NOT IN (SELECT md5 FROM ca_source)");
            Set<String> blobs = new HashSet<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT md5 FROM ca_blob WHERE deleting=TRUE")) { while (r.next()) blobs.add(r.getString(1)); }
            if (!preview.blobMd5s().containsAll(blobs)) throw new Conflict("Blob cleanup set changed; preview again");
            return Set.copyOf(blobs);
        });
    }
    public Set<String> knownBlobs() {
        return tx(c -> { Set<String> result = new HashSet<>(); try (var s = c.createStatement(); var r = s.executeQuery("SELECT md5 FROM ca_blob")) { while (r.next()) result.add(r.getString(1)); } return result; });
    }
    public Set<String> previewBlobs(Set<String> sourcesToDelete) {
        return tx(c -> {
            Map<String, Set<String>> sources = new HashMap<>();
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT b.md5,s.id FROM ca_blob b LEFT JOIN ca_source s ON s.md5=b.md5")) {
                while (r.next()) { var ids = sources.computeIfAbsent(r.getString(1), ignored -> new HashSet<>()); if (r.getString(2) != null) ids.add(r.getString(2)); }
            }
            Set<String> result = new HashSet<>(); sources.forEach((md5, ids) -> { if (sourcesToDelete.containsAll(ids)) result.add(md5); }); return result;
        });
    }
    public void finishBlobDelete(String md5) { tx(c -> { update(c, "DELETE FROM ca_blob WHERE md5=? AND deleting=TRUE", md5); return null; }); }
    private static byte[] vectorBytes(float[] v) { var b = ByteBuffer.allocate(v.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN); for (float f : v) b.putFloat(f); return b.array(); }
    private static float[] vector(byte[] bytes, int dimensions) throws SQLException {
        if (bytes.length != (long) dimensions * Float.BYTES) throw new SQLException("Invalid float32-le vector payload");
        var b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); float[] v = new float[dimensions]; for (int i = 0; i < dimensions; i++) v[i] = b.getFloat(); return v;
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static void sql(Connection c, String sql) throws SQLException { try (var s = c.createStatement()) { s.execute(sql); } }
    private static void update(Connection c, String sql, Object... args) throws SQLException {
        try (var s = c.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); s.executeUpdate(); }
    }
}
