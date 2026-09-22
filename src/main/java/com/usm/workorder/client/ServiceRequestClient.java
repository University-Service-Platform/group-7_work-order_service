package com.usm.workorder.client;

/**
 * The one real cross-service integration this sprint (guide §10 step 3):
 * work-order-service validates against, and calls back into,
 * service-request-service. Isolated behind this interface so the HTTP
 * details (and, later, real service-to-service auth) live in exactly one
 * implementation class - see guide §13's placeholder-and-swap pattern.
 */
public interface ServiceRequestClient {

    /** @throws com.usm.workorder.exception.UpstreamServiceException if the request doesn't exist or the call fails. */
    ServiceRequestSnapshot fetchRequest(String requestId);

    /** Pushes Assigned/InProgress/Resolved back onto the parent request (guide §4.1 internal /status endpoint). */
    void pushStatusUpdate(String requestId, String status);
}
