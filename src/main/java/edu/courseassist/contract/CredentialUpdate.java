package edu.courseassist.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Short-lived secret carrier, deliberately not a record or a view/persistence DTO.
 * The caller closes it when staging completes; no key belongs in events, state or logs.
 */
public final class CredentialUpdate implements AutoCloseable {
    public enum Slot { GENERATION, EMBEDDING }
    public enum Action { REPLACE, REMOVE }

    public static final class Change implements AutoCloseable {
        private final Slot slot;
        private final Action action;
        private final char[] value;
        private boolean closed;

        public Change(Slot slot, Action action, char[] value) {
            this.slot = Objects.requireNonNull(slot, "slot");
            this.action = Objects.requireNonNull(action, "action");
            Objects.requireNonNull(value, "value");
            if ((action == Action.REPLACE) != (value.length > 0)) {
                throw new IllegalArgumentException("Replace requires a key; remove requires an empty array");
            }
            this.value = value.clone();
        }
        public Slot slot() { return slot; }
        public Action action() { return action; }
        /** Receiver owns this copy and must zero it after use. */
        public synchronized char[] copyValue() {
            if (closed) throw new IllegalStateException("Credential input has been cleared");
            return value.clone();
        }
        @Override public synchronized void close() { Arrays.fill(value, '\0'); closed = true; }
        @Override public String toString() { return "CredentialChange[slot=" + slot + ", action=" + action + ", value=<redacted>]"; }
    }

    private final List<Change> changes;
    public CredentialUpdate(List<Change> changes) {
        this.changes = List.copyOf(changes);
        if (this.changes.stream().map(Change::slot).distinct().count() != this.changes.size()) {
            throw new IllegalArgumentException("Duplicate credential slot");
        }
    }
    public List<Change> changes() { return changes; }
    @Override public void close() { changes.forEach(Change::close); }
    @Override public String toString() { return "CredentialUpdate[changes=<redacted>, count=" + changes.size() + "]"; }
}
