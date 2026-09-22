package com.usm.workorder.domain;

/**
 * §3.2 of the guide: "Assigned, In Progress, Resolved, Closed."
 *
 * Sprint 1 only wires ASSIGNED -> IN_PROGRESS -> RESOLVED through real
 * endpoints (start/progress/resolution). CLOSED and its closure_time column
 * are modelled here because the guide's data table names them, but no
 * Sprint 1 endpoint drives that transition yet - raise it with your Tech
 * Lead alongside the RequestStatus naming question (guide §9/§14) before
 * building whatever closes a work order (most likely mirroring the parent
 * ServiceRequest's own confirm/close step).
 */
public enum WorkOrderStatus {
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
