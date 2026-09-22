package com.usm.workorder.service;

import com.usm.workorder.client.ServiceRequestClient;
import com.usm.workorder.client.ServiceRequestSnapshot;
import com.usm.workorder.domain.WorkOrder;
import com.usm.workorder.domain.WorkOrderStatus;
import com.usm.workorder.dto.CreateWorkOrderRequest;
import com.usm.workorder.dto.ProgressUpdateRequest;
import com.usm.workorder.dto.ResolutionRequest;
import com.usm.workorder.dto.SummaryResponse;
import com.usm.workorder.dto.WorkOrderResponse;
import com.usm.workorder.exception.ForbiddenOperationException;
import com.usm.workorder.exception.InvalidRequestException;
import com.usm.workorder.exception.ResourceNotFoundException;
import com.usm.workorder.repository.WorkOrderRepository;
import com.usm.workorder.security.AuthContext;
import com.usm.workorder.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class WorkOrderServiceImpl implements WorkOrderService {

    /**
     * BR-06: mirrors service-request-service's RequestStatus.ACKNOWLEDGED/ESCALATED (the statuses
     * a request reaches once triaged). Kept as plain strings, not a shared enum - see
     * ServiceRequestSnapshot's javadoc for why. If service-request-service's status names change
     * (guide §6/§9), this is the one set that needs to change here.
     */
    private static final Set<String> TRIAGEABLE_REQUEST_STATUSES = Set.of("ACKNOWLEDGED", "ESCALATED");

    /** Roles that may see every work order, not just their own assignment. */
    private static final Set<Role> CAN_VIEW_ALL = EnumSet.of(Role.SERVICE_DESK_OFFICER, Role.SERVICE);

    private static final Set<WorkOrderStatus> ACTIVE_STATUSES = EnumSet.of(
            WorkOrderStatus.ASSIGNED, WorkOrderStatus.IN_PROGRESS);

    private final WorkOrderRepository repository;
    private final WorkOrderIdGenerator idGenerator;
    private final ServiceRequestClient serviceRequestClient;

    public WorkOrderServiceImpl(WorkOrderRepository repository, WorkOrderIdGenerator idGenerator,
                                 ServiceRequestClient serviceRequestClient) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.serviceRequestClient = serviceRequestClient;
    }

    @Override
    public WorkOrderResponse create(CreateWorkOrderRequest request, AuthContext caller) {
        // BR-06: a work order cannot exist without a valid, triaged, non-rejected request.
        ServiceRequestSnapshot snapshot = serviceRequestClient.fetchRequest(request.requestId());
        if (!TRIAGEABLE_REQUEST_STATUSES.contains(snapshot.status())) {
            throw new InvalidRequestException(
                    "Cannot create a work order for request " + request.requestId()
                            + " - it is in status " + snapshot.status()
                            + ", which means it hasn't been triaged yet (or was rejected/cancelled/closed).");
        }

        // Extra guard beyond the guide's explicit BR list: refuse a second active work order for
        // the same request rather than silently allowing duplicates. Flag/adjust with your Tech
        // Lead if the team wants to explicitly allow multiple concurrent work orders per request.
        boolean alreadyActive = repository.findByRequestId(request.requestId()).stream()
                .anyMatch(existing -> ACTIVE_STATUSES.contains(existing.getStatus()));
        if (alreadyActive) {
            throw new InvalidRequestException(
                    "Request " + request.requestId() + " already has an active work order.");
        }

        String workOrderId = idGenerator.nextId();
        WorkOrder entity = new WorkOrder(workOrderId, request.requestId(), request.assignedTechnicianId(),
                request.serviceTeam(), request.schedule(), Instant.now());
        repository.save(entity);

        // Guide §4.2: "calls back to set request status = Assigned".
        serviceRequestClient.pushStatusUpdate(request.requestId(), "ASSIGNED");

        return WorkOrderResponse.from(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkOrderResponse> list(String technicianIdFilter, WorkOrderStatus statusFilter, AuthContext caller) {
        List<WorkOrder> base;

        if (CAN_VIEW_ALL.contains(caller.getRole())) {
            base = statusFilter != null ? repository.findByStatus(statusFilter) : repository.findAll();
            if (technicianIdFilter != null) {
                base = base.stream().filter(w -> w.getAssignedTechnicianId().equals(technicianIdFilter)).toList();
            }
        } else {
            // Technician (or anyone else without view-all rights): always own assignments only,
            // regardless of any technicianId filter they pass.
            base = repository.findByAssignedTechnicianId(caller.getUserId());
            if (statusFilter != null) {
                base = base.stream().filter(w -> w.getStatus() == statusFilter).toList();
            }
        }

        return base.stream().map(WorkOrderResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderResponse getById(String workOrderId, AuthContext caller) {
        WorkOrder entity = findOrThrow(workOrderId);
        assertCanView(entity, caller);
        return WorkOrderResponse.from(entity);
    }

    @Override
    public WorkOrderResponse start(String workOrderId, AuthContext caller) {
        WorkOrder entity = findOrThrow(workOrderId);
        assertIsAssignedTechnician(entity, caller);

        if (entity.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw new InvalidRequestException(
                    "Cannot start a work order in status " + entity.getStatus() + ".");
        }

        entity.start(Instant.now());
        serviceRequestClient.pushStatusUpdate(entity.getRequestId(), "IN_PROGRESS");
        return WorkOrderResponse.from(entity);
    }

    @Override
    public WorkOrderResponse addProgress(String workOrderId, ProgressUpdateRequest request, AuthContext caller) {
        WorkOrder entity = findOrThrow(workOrderId);
        assertIsAssignedTechnician(entity, caller);

        // BR-07: progress always writes against a specific work order, never a bare status change.
        if (entity.getStatus() != WorkOrderStatus.IN_PROGRESS) {
            throw new InvalidRequestException(
                    "Cannot log progress on a work order in status " + entity.getStatus()
                            + " - call /start first.");
        }

        entity.appendActionNote(request.note(), Instant.now());
        return WorkOrderResponse.from(entity);
    }

    @Override
    public WorkOrderResponse resolve(String workOrderId, ResolutionRequest request, AuthContext caller) {
        WorkOrder entity = findOrThrow(workOrderId);
        assertIsAssignedTechnician(entity, caller);

        // BR-08: resolution text is mandatory before Resolved - enforced again here, not just via
        // the DTO's @NotBlank, so this stays true even if this method is ever called from
        // somewhere other than the HTTP layer.
        if (request.resolution() == null || request.resolution().isBlank()) {
            throw new InvalidRequestException("resolution is required to resolve a work order.");
        }

        if (!ACTIVE_STATUSES.contains(entity.getStatus())) {
            throw new InvalidRequestException(
                    "Cannot resolve a work order in status " + entity.getStatus() + ".");
        }

        entity.resolve(request.resolution(), Instant.now());
        serviceRequestClient.pushStatusUpdate(entity.getRequestId(), "RESOLVED");
        return WorkOrderResponse.from(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse summary(String groupBy, AuthContext caller) {
        List<WorkOrder> all = repository.findAll();

        Function<WorkOrder, String> keyExtractor = switch (groupBy == null ? "status" : groupBy.toLowerCase()) {
            case "status" -> w -> w.getStatus().name();
            case "technician", "assignedtechnicianid" -> WorkOrder::getAssignedTechnicianId;
            case "serviceteam" -> w -> w.getServiceTeam() == null ? "UNASSIGNED" : w.getServiceTeam();
            default -> throw new InvalidRequestException(
                    "Unsupported groupBy '" + groupBy + "'. Use one of: status, technician, serviceTeam.");
        };

        Map<String, Long> counts = all.stream()
                .collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));

        return new SummaryResponse(groupBy == null ? "status" : groupBy, counts);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderResponse getByRequestId(String requestId, AuthContext caller) {
        if (!caller.isServiceCall()) {
            throw new ForbiddenOperationException("This endpoint is for service-to-service calls only.");
        }
        WorkOrder entity = repository.findFirstByRequestIdOrderByCreatedTimeDesc(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No work order found for service request " + requestId));
        return WorkOrderResponse.from(entity);
    }

    private WorkOrder findOrThrow(String workOrderId) {
        return repository.findById(workOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("No work order found with id " + workOrderId));
    }

    private void assertCanView(WorkOrder entity, AuthContext caller) {
        boolean isAssignedTechnician = entity.getAssignedTechnicianId().equals(caller.getUserId());
        if (!isAssignedTechnician && !CAN_VIEW_ALL.contains(caller.getRole())) {
            throw new ForbiddenOperationException("You can only view work orders assigned to you.");
        }
    }

    private void assertIsAssignedTechnician(WorkOrder entity, AuthContext caller) {
        if (!entity.getAssignedTechnicianId().equals(caller.getUserId())) {
            throw new ForbiddenOperationException("Only the assigned technician can update this work order.");
        }
    }
}
