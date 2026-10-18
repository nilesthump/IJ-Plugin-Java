package edu.courseassist.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import static edu.courseassist.contract.ApplicationContracts.WorkState.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeGateTest {
    @Test void stopRevokesCallbacksAndCannotBeCompletedByOldStart() {
        var gate = new RuntimeGate();
        assertEquals(OFF, gate.snapshot().state());
        long start = gate.beginStart();
        assertFalse(gate.isCurrent(start));
        long stop = gate.beginStop();
        assertTrue(stop > start);
        assertEquals(STOPPING, gate.snapshot().state());
        assertThrows(CancellationException.class, () -> gate.finishStart(start));
        assertThrows(IllegalStateException.class, gate::beginStart);
        gate.finishStop(stop);
        long next = gate.beginStart();
        gate.finishStart(next);
        assertTrue(gate.isCurrent(next));
        assertFalse(gate.isCurrent(start));
        gate.close();
        assertFalse(gate.isCurrent(next));
        assertThrows(IllegalStateException.class, gate::beginStart);
    }

    @Test void stoppingWaitsForExplicitLocalCoordination() {
        var gate = new RuntimeGate();
        long start = gate.beginStart();
        gate.finishStart(start);
        long stop = gate.beginStop();
        assertFalse(gate.isCurrent(start));
        assertEquals(STOPPING, gate.snapshot().state());
        assertThrows(CancellationException.class, () -> gate.finishStop(start));
        gate.finishStop(stop);
        assertEquals(OFF, gate.snapshot().state());
    }
}
