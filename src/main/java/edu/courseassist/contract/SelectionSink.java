package edu.courseassist.contract;

import edu.courseassist.contract.CaptureContracts.CaptureTicket;
import edu.courseassist.contract.CaptureContracts.SelectionStart;

/** Core-owned ingress. This quick memory path fixes the route and schedules initialization/capture. */
@FunctionalInterface
public interface SelectionSink {
    /** Must not wait for database/network/PDF/heavy PSI; the caller never selects a target in completion. */
    CaptureTicket beginCapture(SelectionStart start);
}
