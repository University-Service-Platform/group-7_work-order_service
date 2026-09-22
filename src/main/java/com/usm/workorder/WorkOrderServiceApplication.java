package com.usm.workorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * USM-G7 Sprint 1 - work-order-service.
 *
 * Owns the WorkOrder entity end to end: creation against a triaged
 * ServiceRequest, technician start/progress/resolution, and the callback
 * that keeps the parent ServiceRequest's status in sync.
 *
 * See the project README and the "Group 7 Sprint 1 - Backend Developer
 * Guide" (sections 3, 4, 5, 6, 7) for the full spec this was built from.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkOrderServiceApplication.class, args);
    }
}
