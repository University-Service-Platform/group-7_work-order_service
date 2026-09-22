package com.usm.workorder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * §3.2 of the guide. `requestId` is a plain String reference to a row this
 * service does NOT own (service-request-service does) - fetched/validated
 * over REST via {@link com.usm.workorder.client.ServiceRequestClient}, never
 * a JPA relationship or cross-schema join.
 */
@Entity
@Table(name = "work_order")
public class WorkOrder {

    @Id
    @Column(name = "work_order_id", length = 20, nullable = false, updatable = false)
    private String workOrderId;

    @Column(name = "request_id", length = 20, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "assigned_technician_id", length = 64, nullable = false)
    private String assignedTechnicianId;

    @Column(name = "service_team", length = 100)
    private String serviceTeam;

    @Column(name = "schedule", length = 255)
    private String schedule;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private WorkOrderStatus status;

    @Column(name = "action_notes", columnDefinition = "TEXT")
    private String actionNotes;

    @Column(name = "resolution", length = 1000)
    private String resolution;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "resolution_time")
    private Instant resolutionTime;

    @Column(name = "closure_time")
    private Instant closureTime;

    @Column(name = "created_time", nullable = false)
    private Instant createdTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected WorkOrder() {
        // JPA
    }

    public WorkOrder(String workOrderId, String requestId, String assignedTechnicianId, String serviceTeam,
                      String schedule, Instant createdTime) {
        this.workOrderId = workOrderId;
        this.requestId = requestId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.serviceTeam = serviceTeam;
        this.schedule = schedule;
        this.status = WorkOrderStatus.ASSIGNED;
        this.createdTime = createdTime;
    }

    // --- getters ---

    public String getWorkOrderId() {
        return workOrderId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public String getServiceTeam() {
        return serviceTeam;
    }

    public String getSchedule() {
        return schedule;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public String getActionNotes() {
        return actionNotes;
    }

    public String getResolution() {
        return resolution;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getResolutionTime() {
        return resolutionTime;
    }

    public Instant getClosureTime() {
        return closureTime;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Long getVersion() {
        return version;
    }

    // --- mutators (BR-09: status + timestamp always change together) ---

    /** US-09, FR-08: technician begins work. */
    public void start(Instant when) {
        this.status = WorkOrderStatus.IN_PROGRESS;
        this.startTime = when;
    }

    /**
     * US-09/16, FR-08, BR-07: append-only action log - guide §3.2 allows a plain text log for a
     * prototype instead of a separate child table. Each entry is timestamped so the log still
     * reads as a real history even without a dedicated `work_order_action` table.
     */
    public void appendActionNote(String note, Instant when) {
        String entry = "[" + when + "] " + note;
        this.actionNotes = (this.actionNotes == null || this.actionNotes.isBlank())
                ? entry
                : this.actionNotes + System.lineSeparator() + entry;
    }

    /** US-10/17, FR-09, BR-08: resolution text is mandatory before this can be called. */
    public void resolve(String resolutionText, Instant when) {
        this.resolution = resolutionText;
        this.status = WorkOrderStatus.RESOLVED;
        this.resolutionTime = when;
    }
}
