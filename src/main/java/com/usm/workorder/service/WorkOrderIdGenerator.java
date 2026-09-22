package com.usm.workorder.service;

/**
 * Strategy interface for generating Work Order IDs.
 */
public interface WorkOrderIdGenerator {

    String nextId();
}
