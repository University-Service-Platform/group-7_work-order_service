package com.usm.workorder.security;

/**
 * Same role set as service-request-service's Role enum (kept as a separate
 * copy in this service's own package - no shared library between
 * microservices, per the module's architecture rules). SERVICE is this
 * project's own placeholder for service-to-service calls, not one of
 * Group 5's real user roles - see README "Service-to-service auth".
 */
public enum Role {
    STUDENT,
    ACADEMIC_STAFF,
    ADMIN_STAFF,
    SERVICE_DESK_OFFICER,
    TECHNICIAN,
    SERVICE
}
