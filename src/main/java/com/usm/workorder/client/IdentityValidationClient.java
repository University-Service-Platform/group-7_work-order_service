package com.usm.workorder.client;

/**
 * Client for performing live caller re-validation against Group 5's
 * identity-access-service before executing sensitive work-order operations.
 */
public interface IdentityValidationClient {

    /**
     * Re-validates caller identity and role directly against Group 5's
     * GET /api/v1/validation/users/{user_id}?require_active=true&required_role={role}.
     *
     * @param userId       the ID of the user to validate
     * @param requiredRole the role required for the requested operation
     * @param bearerToken  the caller's raw bearer token to forward
     * @return snapshot with isValid and isAuthorized booleans
     */
    IdentityValidationSnapshot validateUser(String userId, String requiredRole, String bearerToken);
}
