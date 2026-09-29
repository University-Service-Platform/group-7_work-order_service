package com.usm.workorder.client;

public interface FacilityValidationClient {

    /**
     * Never throws — a Group 6 outage must not block Group 7's own work order flow.
     * Returns a safe "not validated" snapshot (exists=false, validForReservation=false,
     * message describing the failure) on any error.
     */
    FacilityValidationSnapshot validateByCode(String code);
}
