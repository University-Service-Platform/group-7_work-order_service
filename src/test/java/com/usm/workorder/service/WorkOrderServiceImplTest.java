package com.usm.workorder.service;

import com.usm.workorder.client.FacilityValidationClient;
import com.usm.workorder.client.FacilityValidationClientProperties;
import com.usm.workorder.client.FacilityValidationSnapshot;
import com.usm.workorder.client.ServiceRequestClient;
import com.usm.workorder.client.ServiceRequestSnapshot;
import com.usm.workorder.domain.WorkOrder;
import com.usm.workorder.domain.WorkOrderStatus;
import com.usm.workorder.dto.CreateWorkOrderRequest;
import com.usm.workorder.dto.ProgressUpdateRequest;
import com.usm.workorder.dto.ResolutionRequest;
import com.usm.workorder.dto.SummaryResponse;
import com.usm.workorder.dto.WorkOrderResponse;
import com.usm.workorder.client.IdentityValidationClient;
import com.usm.workorder.client.IdentityValidationSnapshot;
import com.usm.workorder.exception.ForbiddenOperationException;
import com.usm.workorder.exception.InvalidRequestException;
import com.usm.workorder.exception.UpstreamServiceException;
import com.usm.workorder.repository.WorkOrderRepository;
import com.usm.workorder.security.AuthContext;
import com.usm.workorder.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * First-draft unit tests for the business rules in guide §5, written the way
 * guide §12 describes using AI: a fast first pass you then run, read, and
 * extend yourself with the acceptance-criteria cases your BA report calls out.
 */
@ExtendWith(MockitoExtension.class)
class WorkOrderServiceImplTest {

    @Mock
    private WorkOrderRepository repository;

    @Mock
    private WorkOrderIdGenerator idGenerator;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private FacilityValidationClient facilityValidationClient;

    @Mock
    private IdentityValidationClient identityValidationClient;

    private FacilityValidationClientProperties properties;

    private WorkOrderServiceImpl service;

    private static final AuthContext OFFICER = new AuthContext("officer-1", Role.SERVICE_DESK_OFFICER, "IT Services");
    private static final AuthContext TECH = new AuthContext("tech-1", Role.TECHNICIAN, "Facilities");
    private static final AuthContext OTHER_TECH = new AuthContext("tech-2", Role.TECHNICIAN, "Facilities");
    private static final AuthContext STUDENT = new AuthContext("student-1", Role.STUDENT, "Faculty of Science");
    private static final AuthContext SERVICE_CALL = new AuthContext("service-request-service", Role.SERVICE, "SYSTEM");

    @BeforeEach
    void setUp() {
        properties = new FacilityValidationClientProperties();
        service = new WorkOrderServiceImpl(repository, idGenerator, serviceRequestClient,
                facilityValidationClient, properties, identityValidationClient);
        lenient().when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.active("user-1"));
    }

    private WorkOrder newWorkOrder(WorkOrderStatus status) {
        WorkOrder entity = new WorkOrder("WO-2026-0001", "SR-2026-0001", TECH.getUserId(), "Facilities",
                "Mon 9am-12pm", Instant.now());
        if (status == WorkOrderStatus.IN_PROGRESS || status == WorkOrderStatus.RESOLVED) {
            entity.start(Instant.now());
        }
        if (status == WorkOrderStatus.RESOLVED) {
            entity.resolve("Replaced the bulb", Instant.now());
        }
        return entity;
    }

    @Test
    void create_onUntriagedRequest_isRejected_BR06() {
        when(serviceRequestClient.fetchRequest("SR-2026-0001"))
                .thenReturn(new ServiceRequestSnapshot("SR-2026-0001", "NEW", "Lab 3", "IT"));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("hasn't been triaged");
    }

    @Test
    void create_onTriagedRequest_succeedsAndPushesAssignedBack() {
        when(serviceRequestClient.fetchRequest("SR-2026-0001"))
                .thenReturn(new ServiceRequestSnapshot("SR-2026-0001", "ACKNOWLEDGED", "Lab 3", "IT"));
        when(repository.findByRequestId("SR-2026-0001")).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn("WO-2026-0007");

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        WorkOrderResponse response = service.create(request, OFFICER);

        assertThat(response.workOrderId()).isEqualTo("WO-2026-0007");
        assertThat(response.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        verify(serviceRequestClient).pushStatusUpdate("SR-2026-0001", "ASSIGNED");
    }

    @Test
    void create_whenAnActiveWorkOrderAlreadyExists_isRejected() {
        when(serviceRequestClient.fetchRequest("SR-2026-0001"))
                .thenReturn(new ServiceRequestSnapshot("SR-2026-0001", "ESCALATED", "Lab 3", "IT"));
        when(repository.findByRequestId("SR-2026-0001")).thenReturn(List.of(newWorkOrder(WorkOrderStatus.ASSIGNED)));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already has an active work order");
    }

    @Test
    void create_whenFacilityCheckFailsAndEnforceValidationIsFalse_stillSucceeds() {
        when(serviceRequestClient.fetchRequest("SR-2026-0001"))
                .thenReturn(new ServiceRequestSnapshot("SR-2026-0001", "ACKNOWLEDGED", "LAB-101", "FACILITY"));
        when(facilityValidationClient.validateByCode("LAB-101"))
                .thenReturn(new FacilityValidationSnapshot(false, false, false, false, "Resource not available", null, null, null));
        when(repository.findByRequestId("SR-2026-0001")).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn("WO-2026-0008");

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        WorkOrderResponse response = service.create(request, OFFICER);

        assertThat(response.workOrderId()).isEqualTo("WO-2026-0008");
        assertThat(response.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        verify(facilityValidationClient).validateByCode("LAB-101");
        verify(serviceRequestClient).pushStatusUpdate("SR-2026-0001", "ASSIGNED");
    }

    @Test
    void create_whenFacilityCheckFailsAndEnforceValidationIsTrue_throwsInvalidRequestException() {
        properties.setEnforceValidation(true);
        when(serviceRequestClient.fetchRequest("SR-2026-0001"))
                .thenReturn(new ServiceRequestSnapshot("SR-2026-0001", "ACKNOWLEDGED", "LAB-101", "FACILITY"));
        when(facilityValidationClient.validateByCode("LAB-101"))
                .thenReturn(new FacilityValidationSnapshot(false, false, false, false, "Resource with ID 999 does not exist", null, null, null));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot dispatch a technician to 'LAB-101'")
                .hasMessageContaining("Resource with ID 999 does not exist");

        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void start_byNonAssignedTechnician_isForbidden() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.start("WO-2026-0001", OTHER_TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only the assigned technician can update this work order");
    }

    @Test
    void start_whenAlreadyInProgress_isRejected() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.start("WO-2026-0001", TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot start a work order in status IN_PROGRESS");
    }

    @Test
    void start_setsInProgressAndPushesBackToParentRequest() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        WorkOrderResponse response = service.start("WO-2026-0001", TECH);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(response.startTime()).isNotNull();
        verify(serviceRequestClient).pushStatusUpdate("SR-2026-0001", "IN_PROGRESS");
    }

    @Test
    void addProgress_byNonAssignedTechnician_isForbidden() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", new ProgressUpdateRequest("Checked wiring"), OTHER_TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only the assigned technician can update this work order");
    }

    @Test
    void addProgress_beforeStart_isRejected_BR07() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", new ProgressUpdateRequest("checked wiring"), TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("call /start first");
    }

    @Test
    void addProgress_whenResolved_isRejected() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.RESOLVED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", new ProgressUpdateRequest("checked wiring"), TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot log progress on a work order in status RESOLVED");
    }

    @Test
    void addProgress_whileInProgress_appendsToActionLog() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        WorkOrderResponse response = service.addProgress("WO-2026-0001",
                new ProgressUpdateRequest("Replaced the fuse"), TECH);

        assertThat(response.actionNotes()).contains("Replaced the fuse");
    }

    @Test
    void resolve_byNonAssignedTechnician_isForbidden() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", new ResolutionRequest("Fixed"), OTHER_TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Only the assigned technician can update this work order");
    }

    @Test
    void resolve_withBlankResolution_isRejected_BR08() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", new ResolutionRequest("   "), TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("resolution is required");

        verify(serviceRequestClient, never()).pushStatusUpdate(any(), eq("RESOLVED"));
    }

    @Test
    void resolve_withNullResolution_isRejected_BR08() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", new ResolutionRequest(null), TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("resolution is required");

        verify(serviceRequestClient, never()).pushStatusUpdate(any(), eq("RESOLVED"));
    }

    @Test
    void resolve_whenNotActiveStatus_isRejected() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.RESOLVED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", new ResolutionRequest("Fixed again"), TECH))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Cannot resolve a work order in status RESOLVED");

        verify(serviceRequestClient, never()).pushStatusUpdate(any(), eq("RESOLVED"));
    }

    @Test
    void resolve_withResolutionText_setsResolvedAndPushesBackToParentRequest() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        WorkOrderResponse response = service.resolve("WO-2026-0001",
                new ResolutionRequest("Replaced the projector bulb"), TECH);

        assertThat(response.status()).isEqualTo(WorkOrderStatus.RESOLVED);
        assertThat(response.resolution()).isEqualTo("Replaced the projector bulb");
        verify(serviceRequestClient).pushStatusUpdate("SR-2026-0001", "RESOLVED");
    }

    @Test
    void getById_byNonOwnerNonPrivilegedCaller_isForbidden() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getById("WO-2026-0001", OTHER_TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You can only view work orders assigned to you");

        assertThatThrownBy(() -> service.getById("WO-2026-0001", STUDENT))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("You can only view work orders assigned to you");
    }

    @Test
    void getById_byAssignedTechnicianOrOfficer_succeeds() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findById("WO-2026-0001")).thenReturn(Optional.of(entity));

        WorkOrderResponse respTech = service.getById("WO-2026-0001", TECH);
        assertThat(respTech.workOrderId()).isEqualTo("WO-2026-0001");

        WorkOrderResponse respOfficer = service.getById("WO-2026-0001", OFFICER);
        assertThat(respOfficer.workOrderId()).isEqualTo("WO-2026-0001");
    }

    @Test
    void getByRequestId_byNonServiceCaller_throwsForbiddenOperationException() {
        assertThatThrownBy(() -> service.getByRequestId("SR-2026-0001", TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("service-to-service calls only");

        assertThatThrownBy(() -> service.getByRequestId("SR-2026-0001", OFFICER))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("service-to-service calls only");
    }

    @Test
    void getByRequestId_byServiceCaller_succeeds() {
        WorkOrder entity = newWorkOrder(WorkOrderStatus.ASSIGNED);
        when(repository.findFirstByRequestIdOrderByCreatedTimeDesc("SR-2026-0001"))
                .thenReturn(Optional.of(entity));

        WorkOrderResponse response = service.getByRequestId("SR-2026-0001", SERVICE_CALL);

        assertThat(response.workOrderId()).isEqualTo("WO-2026-0001");
        assertThat(response.requestId()).isEqualTo("SR-2026-0001");
    }

    @Test
    void summary_withUnsupportedGroupBy_throwsInvalidRequestException() {
        assertThatThrownBy(() -> service.summary("invalidField", OFFICER))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported groupBy 'invalidField'");
    }

    @Test
    void summary_withSupportedGroupBy_returnsGroupedCounts() {
        WorkOrder wo1 = newWorkOrder(WorkOrderStatus.ASSIGNED);
        WorkOrder wo2 = newWorkOrder(WorkOrderStatus.IN_PROGRESS);
        when(repository.findAll()).thenReturn(List.of(wo1, wo2));

        SummaryResponse response = service.summary("status", OFFICER);

        assertThat(response.groupBy()).isEqualTo("status");
        assertThat(response.counts()).containsEntry("ASSIGNED", 1L);
        assertThat(response.counts()).containsEntry("IN_PROGRESS", 1L);
    }

    @Test
    void list_forTechnician_isRestrictedToOwnAssignmentsRegardlessOfFilter() {
        when(repository.findByAssignedTechnicianId("tech-1")).thenReturn(List.of(newWorkOrder(WorkOrderStatus.ASSIGNED)));

        List<WorkOrderResponse> result = service.list("someone-else", null, TECH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assignedTechnicianId()).isEqualTo("tech-1");
    }

    // =========================================================================
    // Live Identity Re-Validation Tests (Sensitive Actions: create, start, progress, resolution)
    // =========================================================================

    @Test
    void create_whenIdentityValidationInactive_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.inactive("officer-1", "Account inactive"));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("inactive");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void create_whenIdentityValidationNotFound_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.notFound("User not found"));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not found");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void create_whenIdentityValidationUnreachable_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Connection refused to identity-access-service"));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Connection refused");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void create_whenIdentityValidationTimeout_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Read timed out from identity-access-service"));

        CreateWorkOrderRequest request = new CreateWorkOrderRequest("SR-2026-0001", "tech-1", "Facilities", null);

        assertThatThrownBy(() -> service.create(request, OFFICER))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("timed out");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void start_whenIdentityValidationInactive_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.inactive("tech-1", "Technician account deactivated"));

        assertThatThrownBy(() -> service.start("WO-2026-0001", TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any());
    }

    @Test
    void start_whenIdentityValidationNotFound_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.notFound("Technician not found"));

        assertThatThrownBy(() -> service.start("WO-2026-0001", TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void start_whenIdentityValidationUnreachable_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Identity service unreachable"));

        assertThatThrownBy(() -> service.start("WO-2026-0001", TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("unreachable");
        verify(repository, never()).save(any());
    }

    @Test
    void start_whenIdentityValidationTimeout_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Identity service timed out"));

        assertThatThrownBy(() -> service.start("WO-2026-0001", TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("timed out");
        verify(repository, never()).save(any());
    }

    @Test
    void addProgress_whenIdentityValidationInactive_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.inactive("tech-1", "Account revoked"));

        ProgressUpdateRequest request = new ProgressUpdateRequest("Diagnosed issue");

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", request, TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void addProgress_whenIdentityValidationNotFound_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.notFound("User not found"));

        ProgressUpdateRequest request = new ProgressUpdateRequest("Diagnosed issue");

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", request, TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void addProgress_whenIdentityValidationUnreachable_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Network error reaching identity service"));

        ProgressUpdateRequest request = new ProgressUpdateRequest("Diagnosed issue");

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", request, TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Network error");
    }

    @Test
    void addProgress_whenIdentityValidationTimeout_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Socket timeout from identity service"));

        ProgressUpdateRequest request = new ProgressUpdateRequest("Diagnosed issue");

        assertThatThrownBy(() -> service.addProgress("WO-2026-0001", request, TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Socket timeout");
    }

    @Test
    void resolve_whenIdentityValidationInactive_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.inactive("tech-1", "Technician suspended"));

        ResolutionRequest request = new ResolutionRequest("Completed repair");

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", request, TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("inactive");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void resolve_whenIdentityValidationNotFound_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenReturn(IdentityValidationSnapshot.notFound("Technician not found"));

        ResolutionRequest request = new ResolutionRequest("Completed repair");

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", request, TECH))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("not found");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void resolve_whenIdentityValidationUnreachable_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Identity service offline"));

        ResolutionRequest request = new ResolutionRequest("Completed repair");

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", request, TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("offline");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }

    @Test
    void resolve_whenIdentityValidationTimeout_failsClosed() {
        when(identityValidationClient.validate(any()))
                .thenThrow(new UpstreamServiceException("Gateway timeout (504) from identity service"));

        ResolutionRequest request = new ResolutionRequest("Completed repair");

        assertThatThrownBy(() -> service.resolve("WO-2026-0001", request, TECH))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Gateway timeout");
        verify(serviceRequestClient, never()).pushStatusUpdate(any(), any());
    }
}
