package com.usm.workorder.client;

/**
 * Snapshot of Group 6's facility-resource-service validation response.
 * Deliberately captures only what work-order-service needs to assess resource availability
 * and validity for dispatching technicians.
 */
public record FacilityValidationSnapshot(
        boolean exists,
        boolean active,
        boolean available,
        boolean validForReservation,
        String message,
        Integer capacity,
        String operatingHoursStart,
        String operatingHoursEnd
) {
}
