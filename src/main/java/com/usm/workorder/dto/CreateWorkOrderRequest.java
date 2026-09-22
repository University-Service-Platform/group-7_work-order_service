package com.usm.workorder.dto;

import jakarta.validation.constraints.NotBlank;

/** US-06/07, FR-05/06, API-04, BR-06 (guide §4.2) - Service Desk Officer only. */
public record CreateWorkOrderRequest(

        @NotBlank(message = "requestId is required")
        String requestId,

        @NotBlank(message = "assignedTechnicianId is required")
        String assignedTechnicianId,

        String serviceTeam,

        String schedule
) {
}
