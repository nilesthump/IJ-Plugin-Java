package edu.courseassist.core;

import edu.courseassist.contract.ApplicationContracts.WorkState;
import java.util.concurrent.CancellationException;

/** Manager-only runtime boundary. The application controller must validate configuration and
 * finish local persistence/cancellation before completing these transitions.
 */
public final class RuntimeGate implements AutoCloseable {
    public record Snapshot(WorkState state, long epoch) {}
    private WorkState state = WorkState.OFF;
    private long epoch;
    private boolean closed;

    public synchronized Snapshot snapshot() { return new Snapshot(state, epoch); }

    public synchronized long beginStart() {
        if (closed || state != WorkState.OFF) throw new IllegalStateException("Runtime cannot start");
        state = WorkState.STARTING;
        return ++epoch;
    }

    public synchronized void finishStart(long expectedEpoch) {
        requireTransition(WorkState.STARTING, expectedEpoch);
        state = WorkState.ON;
    }

    /** Revoke the old epoch before requesting cancellation. No remote acknowledgement is required here. */
    public synchronized long beginStop() {
        if (closed || (state != WorkState.ON && state != WorkState.STARTING)) {
            throw new IllegalStateException("Runtime cannot stop");
        }
        state = WorkState.STOPPING;
        return ++epoch;
    }

    public synchronized void finishStop(long expectedEpoch) {
        requireTransition(WorkState.STOPPING, expectedEpoch);
        state = WorkState.OFF;
    }

    /** Eligibility check for callbacks, not a substitute for transactional commit guards. */
    public synchronized boolean isCurrent(long expectedEpoch) {
        return !closed && state == WorkState.ON && epoch == expectedEpoch;
    }

    private void requireTransition(WorkState expectedState, long expectedEpoch) {
        if (closed || state != expectedState || epoch != expectedEpoch) {
            throw new CancellationException("Runtime transition invalidated");
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        ++epoch;
        state = WorkState.OFF;
    }
}
