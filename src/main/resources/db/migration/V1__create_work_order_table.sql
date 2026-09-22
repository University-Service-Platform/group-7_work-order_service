-- USM-G7 Sprint 1 - work_order_db schema
-- Owned entirely by work-order-service. request_id is a plain reference to a
-- row in service-request-service's OWN schema - never a foreign key, never a
-- cross-schema join. Validated/fetched over REST (see client.ServiceRequestClient).

CREATE TABLE work_order (
    work_order_id            VARCHAR(20)   NOT NULL PRIMARY KEY,
    request_id                VARCHAR(20)   NOT NULL,
    assigned_technician_id  VARCHAR(64)   NOT NULL,
    service_team              VARCHAR(100)  NULL,
    schedule                   VARCHAR(255)  NULL,
    status                     VARCHAR(20)   NOT NULL,
    action_notes               TEXT          NULL,
    resolution                 VARCHAR(1000) NULL,
    start_time                  DATETIME      NULL,
    resolution_time             DATETIME      NULL,
    closure_time                DATETIME      NULL,
    created_time                 DATETIME      NOT NULL,
    version                     BIGINT        NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_work_order_request_id ON work_order (request_id);
CREATE INDEX idx_work_order_technician ON work_order (assigned_technician_id);
CREATE INDEX idx_work_order_status ON work_order (status);
