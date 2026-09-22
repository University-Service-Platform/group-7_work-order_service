package com.usm.workorder.service;

import com.usm.workorder.domain.WorkOrderStatus;
import com.usm.workorder.dto.CreateWorkOrderRequest;
import com.usm.workorder.dto.ProgressUpdateRequest;
import com.usm.workorder.dto.ResolutionRequest;
import com.usm.workorder.dto.SummaryResponse;
import com.usm.workorder.dto.WorkOrderResponse;
import com.usm.workorder.security.AuthContext;

import java.util.List;

public interface WorkOrderService {

    WorkOrderResponse create(CreateWorkOrderRequest request, AuthContext caller);

    List<WorkOrderResponse> list(String technicianIdFilter, WorkOrderStatus statusFilter, AuthContext caller);

    WorkOrderResponse getById(String workOrderId, AuthContext caller);

    WorkOrderResponse start(String workOrderId, AuthContext caller);

    WorkOrderResponse addProgress(String workOrderId, ProgressUpdateRequest request, AuthContext caller);

    WorkOrderResponse resolve(String workOrderId, ResolutionRequest request, AuthContext caller);

    SummaryResponse summary(String groupBy, AuthContext caller);

    /** Internal, service-to-service only (guide §4.2 GET .../by-request/{requestId}). */
    WorkOrderResponse getByRequestId(String requestId, AuthContext caller);
}
