package edu.courseassist.storage;

import edu.courseassist.contract.ChatContracts.*;
import edu.courseassist.core.react.ReactData.*;
import java.sql.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Cumulative attempt archive in the shared H2 store. Never creates a session or authorizes a
 * write from an upsert. Group 4 supplies the mandatory root/instance/operation guard, which runs
 * INSIDE every short transaction. Corpus schema must be initialized first for source retention. */
public final class ReactRepository implements Journal {
    public enum Access { CREATE, LIVE_WRITE, COORDINATOR_TERMINAL, READ, DELETE }
    @FunctionalInterface public interface Guard { void check(Connection connection, Identity identity, Access access) throws Exception; }
    @FunctionalInterface public interface AfterSave { void saved(Connection connection, Snapshot snapshot) throws Exception; }
    private final H2Store store;
    private final Guard guard;
    private final AfterSave afterSave;
    private final JsonMapper json = JsonMapper.builder().build();
    public ReactRepository(H2Store store, Guard guard) { this(store, guard, (c, s) -> {}); }
    public ReactRepository(H2Store store, Guard guard, AfterSave afterSave) {
        this.store = Objects.requireNonNull(store); this.guard = Objects.requireNonNull(guard); this.afterSave = Objects.requireNonNull(afterSave);
    }
    public void initialize() {
        store.transaction(c -> {
            try (var s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS ca_react_meta(singleton INT PRIMARY KEY, schema_version INT NOT NULL)");
                try (var r = s.executeQuery("SELECT schema_version FROM ca_react_meta WHERE singleton=1")) {
                    if (r.next()) { if (r.getInt(1) != 1) throw new SQLException("Unsupported ReAct schema"); }
                    else { try (var insert = c.prepareStatement("INSERT INTO ca_react_meta VALUES(1,1)")) { insert.executeUpdate(); } }
                }
                s.execute("CREATE TABLE IF NOT EXISTS ca_react_attempt(id VARCHAR PRIMARY KEY, project_id VARCHAR NOT NULL, session_id VARCHAR NOT NULL, revision BIGINT NOT NULL, terminal BOOLEAN NOT NULL, payload CLOB NOT NULL)");
            }
            return null;
        }).join();
    }
    @Override public void create(Snapshot snapshot) {
        if (snapshot.revision() != 0 || snapshot.status() != AttemptStatus.RUNNING || !snapshot.branches().isEmpty()) throw new IllegalArgumentException("Expected new attempt");
        store.transaction(c -> {
            guard.check(c, snapshot.identity(), Access.CREATE);
            // Session submit may already have inserted this exact empty attempt atomically.
            var existing = read(c, snapshot.identity());
            if (existing.isPresent()) { if (!existing.get().equals(snapshot)) throw new SQLException("Attempt already started"); }
            else insert(c, snapshot);
            return null;
        }).join();
    }
    void insert(Connection c, Snapshot snapshot) throws SQLException {
        try (var s = c.prepareStatement("INSERT INTO ca_react_attempt VALUES(?,?,?,?,?,?)")) {
            s.setString(1, snapshot.identity().attemptId()); s.setString(2, snapshot.identity().projectId()); s.setString(3, snapshot.identity().sessionId());
            s.setLong(4, snapshot.revision()); s.setBoolean(5, snapshot.status()!=AttemptStatus.RUNNING); s.setString(6, json.writeValueAsString(snapshot)); s.executeUpdate();
        }
    }
    @Override public void save(Snapshot snapshot, boolean coordinator) {
        if (!snapshot.saved() || (coordinator && (snapshot.status() == AttemptStatus.RUNNING || snapshot.status() == AttemptStatus.SUCCEEDED)))
            throw new IllegalArgumentException("Invalid coordinator/durable snapshot");
        store.transaction(c -> {
            guard.check(c, snapshot.identity(), coordinator ? Access.COORDINATOR_TERMINAL : Access.LIVE_WRITE);
            var old = read(c, snapshot.identity()).orElseThrow(() -> new SQLException("Attempt absent; late write rejected"));
            if (!old.identity().equals(snapshot.identity()) || !old.input().equals(snapshot.input()) || old.status() != AttemptStatus.RUNNING || snapshot.revision() <= old.revision()
                    || snapshot.throughSeq() < old.throughSeq()) throw new SQLException("Attempt revision/terminal fence");
            retain(c, snapshot);
            try (var s = c.prepareStatement("UPDATE ca_react_attempt SET revision=?,terminal=?,payload=? WHERE id=? AND revision=? AND terminal=FALSE")) {
                s.setLong(1, snapshot.revision()); s.setBoolean(2, snapshot.status() != AttemptStatus.RUNNING); s.setString(3, json.writeValueAsString(snapshot));
                s.setString(4, snapshot.identity().attemptId()); s.setLong(5, old.revision());
                if (s.executeUpdate() != 1) throw new SQLException("Attempt commit lost ownership");
            }
            afterSave.saved(c, snapshot);
            return null;
        }).join();
    }
    public Optional<Snapshot> load(Identity identity) {
        return store.transaction(c -> { guard.check(c, identity, Access.READ); return read(c, identity); }).join();
    }
    /** Explicit lifecycle recovery only. No model or tool work is scheduled on load or recovery. */
    public Snapshot interrupt(Identity identity) {
        Snapshot old = load(identity).orElseThrow();
        if (old.status() != AttemptStatus.RUNNING) return old;
        var branches = old.branches().stream().map(b -> new Branch(b.id(), b.status(), b.retrieval(), b.explanation(), b.steps().stream().map(s ->
                new Step(s.id(), s.throughSeq(), s.status() == StepStatus.STREAMING || s.status() == StepStatus.TOOLS_RUNNING ? StepStatus.INCOMPLETE : s.status(),
                        s.body(), s.thinking(), s.tools(), s.finishReason(), s.usage(), s.suppliedEvidence())).toList(), b.protocol())).toList();
        Snapshot interrupted = new Snapshot(old.identity(), old.input(), old.revision()+1, old.throughSeq(), AttemptStatus.INTERRUPTED, true,
                "", "", branches, List.of(), "recovered-interrupted");
        save(interrupted, true); return interrupted;
    }
    /** Called under the session deletion fence; shared PDFs/indices remain untouched. */
    public void deleteSession(Identity identity) {
        store.transaction(c -> {
            guard.check(c, identity, Access.DELETE);
            try (var s = c.prepareStatement("DELETE FROM ca_source_ref WHERE owner_id IN (SELECT 'react:' || id FROM ca_react_attempt WHERE project_id=? AND session_id=?)")) {
                s.setString(1, identity.projectId()); s.setString(2, identity.sessionId()); s.executeUpdate();
            }
            try (var s = c.prepareStatement("DELETE FROM ca_react_attempt WHERE project_id=? AND session_id=?")) {
                s.setString(1, identity.projectId()); s.setString(2, identity.sessionId()); s.executeUpdate();
            }
            return null;
        }).join();
    }
    Optional<Snapshot> read(Connection c, Identity identity) throws SQLException {
        try (var s = c.prepareStatement("SELECT payload FROM ca_react_attempt WHERE id=? AND project_id=? AND session_id=?")) {
            s.setString(1, identity.attemptId()); s.setString(2, identity.projectId()); s.setString(3, identity.sessionId());
            try (var r = s.executeQuery()) {
                if (!r.next()) return Optional.empty();
                Snapshot snapshot = json.readValue(r.getString(1), Snapshot.class);
                if (!snapshot.identity().attemptId().equals(identity.attemptId()) || !snapshot.identity().projectId().equals(identity.projectId())
                        || !snapshot.identity().sessionId().equals(identity.sessionId())) throw new SQLException("Corrupt attempt identity");
                return Optional.of(snapshot);
            }
        }
    }
    private static void retain(Connection c, Snapshot snapshot) throws SQLException {
        Set<String> sources = new HashSet<>();
        for (Branch b : snapshot.branches()) for (Step step : b.steps()) {
            step.suppliedEvidence().forEach(e -> sources.add(e.source().versionId()));
            step.tools().forEach(t -> t.evidence().forEach(e -> sources.add(e.source().versionId())));
        }
        for (String source : sources) try (var s = c.prepareStatement("MERGE INTO ca_source_ref KEY(owner_id,source_id) SELECT ?,id FROM ca_source WHERE id=?")) {
            s.setString(1, "react:" + snapshot.identity().attemptId()); s.setString(2, source); s.executeUpdate();
        }
    }
}
