package com.usm.workorder.client;

/**
 * Just enough of service-request-service's ServiceRequest to validate BR-06 -
 * deliberately not the full entity, and status is a plain String (not a
 * shared enum) because these are two independent services/repos with no
 * shared library between them. If service-request-service's status names
 * change (guide §6/§9), only the TRIAGEABLE_STATUSES set in
 * WorkOrderServiceImpl needs to change, not this class.
 */
public record ServiceRequestSnapshot(String requestId, String status, String location, String category) {
}
