package com.usm.workorder.repository;

import com.usm.workorder.domain.WorkOrder;
import com.usm.workorder.domain.WorkOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, String> {

    List<WorkOrder> findByAssignedTechnicianId(String technicianId);

    List<WorkOrder> findByStatus(WorkOrderStatus status);

    /** Used by the internal by-request lookup (guide §4.2) - a request should have at most one active work order. */
    Optional<WorkOrder> findFirstByRequestIdOrderByCreatedTimeDesc(String requestId);

    List<WorkOrder> findByRequestId(String requestId);
}
