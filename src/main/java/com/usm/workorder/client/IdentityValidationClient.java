package com.usm.workorder.client;

/**
 * Client for performing live caller re-validation against Group 5's
 * identity-access-service before executing sensitive work-order operations.
 */
public interface IdentityValidationClient {

    /**
     * Validates the caller's live identity using their raw bearer token.
     *
     * @param rawBearerToken the bearer token from the caller's Authorization header
     * @return snapshot of identity verification status
     */
    IdentityValidationSnapshot validate(String rawBearerToken);
}
