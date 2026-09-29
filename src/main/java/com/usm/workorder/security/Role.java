package com.usm.workorder.security;

/**
 * Role enum representing Group 5's published roles plus this service's internal SERVICE role.
 *
 * Real roles from Group 5's contract:
 * ADMIN, STAFF, STUDENT, ACADEMIC_STAFF, ADMINISTRATIVE_STAFF,
 * SERVICE_DESK_OFFICER, TECHNICIAN, RESOURCE_MANAGER, EVENT_ORGANIZER.
 *
 * SERVICE is this project's own placeholder for internal service-to-service calls,
 * not one of Group 5's real user roles.
 */
public enum Role {
    ADMIN,
    STAFF,
    STUDENT,
    ACADEMIC_STAFF,
    ADMINISTRATIVE_STAFF,
    SERVICE_DESK_OFFICER,
    TECHNICIAN,
    RESOURCE_MANAGER,
    EVENT_ORGANIZER,
    SERVICE
}
