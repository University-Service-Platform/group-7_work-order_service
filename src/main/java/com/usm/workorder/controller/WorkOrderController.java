package com.usm.workorder.controller;

import com.usm.workorder.domain.WorkOrderStatus;
import com.usm.workorder.dto.CreateWorkOrderRequest;
import com.usm.workorder.dto.ProgressUpdateRequest;
import com.usm.workorder.dto.ResolutionRequest;
import com.usm.workorder.dto.SummaryResponse;
import com.usm.workorder.dto.WorkOrderResponse;
import com.usm.workorder.security.AuthContextHolder;
import com.usm.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Backend Dev 2 surface (guide §2/§4.2). The internal by-request lookup lives
 * at the bottom of this same controller, same base path, matching how the
 * guide documents it - keep that one path out of the API Gateway's public
 * route table (see README "Service-to-service auth").
 */
@RestController
@RequestMapping("/api/work-orders")
@Tag(name = "Work Orders", description = "Create work orders against a triaged request; technician start/progress/resolution.")
public class WorkOrderController {

    private final WorkOrderService service;

    public WorkOrderController(WorkOrderService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('SERVICE_DESK_OFFICER')")
    @Operation(summary = "Create a work order for a triaged request (US-06/07, FR-05/06, API-04, BR-06)")
    public ResponseEntity<WorkOrderResponse> create(@Valid @RequestBody CreateWorkOrderRequest request) {
        WorkOrderResponse created = service.create(request, AuthContextHolder.require());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "Technician workspace / queue - own for technicians, all for Service Desk (US-08, FR-07)")
    public List<WorkOrderResponse> list(
            @RequestParam(required = false) String technicianId,
            @RequestParam(required = false) WorkOrderStatus status) {
        return service.list(technicianId, status, AuthContextHolder.require());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SERVICE_DESK_OFFICER','ADMIN_STAFF')")
    @Operation(summary = "Workload summaries (shares FR-12)")
    public SummaryResponse summary(@RequestParam(required = false) String groupBy) {
        return service.summary(groupBy, AuthContextHolder.require());
    }

    @GetMapping("/by-request/{requestId}")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "INTERNAL ONLY - lets service-request-service or Group 8 check completion status")
    public WorkOrderResponse getByRequestId(@PathVariable("requestId") String requestId) {
        return service.getByRequestId(requestId, AuthContextHolder.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail - assigned technician or Service Desk (US-08)")
    public WorkOrderResponse getById(@PathVariable("id") String id) {
        return service.getById(id, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Sets In Progress + start time (US-09, FR-08) - assigned technician only")
    public WorkOrderResponse start(@PathVariable("id") String id) {
        return service.start(id, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Append an action note (US-09/16, FR-08, BR-07) - assigned technician only")
    public WorkOrderResponse addProgress(@PathVariable("id") String id,
                                          @Valid @RequestBody ProgressUpdateRequest request) {
        return service.addProgress(id, request, AuthContextHolder.require());
    }

    @PatchMapping("/{id}/resolution")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "Record resolution, sets Resolved, calls back to service-request-service "
            + "(US-10/17, FR-09, API-05/06, BR-08) - assigned technician only")
    public WorkOrderResponse resolve(@PathVariable("id") String id,
                                      @Valid @RequestBody ResolutionRequest request) {
        return service.resolve(id, request, AuthContextHolder.require());
    }
}
