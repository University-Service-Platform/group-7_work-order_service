package com.usm.workorder.dto;

import com.usm.workorder.domain.WorkOrder;
import com.usm.workorder.domain.WorkOrderStatus;

import java.time.Instant;

public record WorkOrderResponse(
        String workOrderId,
        String requestId,
        String assignedTechnicianId,
        String serviceTeam,
        String schedule,
        WorkOrderStatus status,
        String actionNotes,
        String resolution,
        Instant startTime,
        Instant resolutionTime,
        Instant closureTime,
        Instant createdTime
) {
    public static WorkOrderResponse from(WorkOrder entity) {
        return new WorkOrderResponse(
                entity.getWorkOrderId(),
                entity.getRequestId(),
                entity.getAssignedTechnicianId(),
                entity.getServiceTeam(),
                entity.getSchedule(),
                entity.getStatus(),
                entity.getActionNotes(),
                entity.getResolution(),
                entity.getStartTime(),
                entity.getResolutionTime(),
                entity.getClosureTime(),
                entity.getCreatedTime()
        );
    }
}
