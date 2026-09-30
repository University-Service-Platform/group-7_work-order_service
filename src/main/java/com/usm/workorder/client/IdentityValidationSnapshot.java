package com.usm.workorder.client;

/**
 * Snapshot of live caller identity verification returned by Group 5's
 * identity-access-service (GET /api/v1/validation/users/{user_id}).
 */
public record IdentityValidationSnapshot(
        boolean isValid,
        boolean isAuthorized,
        String message
) {
    public static IdentityValidationSnapshot valid() {
        return new IdentityValidationSnapshot(true, true, "User is valid and authorized");
    }

    public static IdentityValidationSnapshot valid(String message) {
        return new IdentityValidationSnapshot(true, true, message != null ? message : "User is valid and authorized");
    }

    public static IdentityValidationSnapshot invalid(String message) {
        return new IdentityValidationSnapshot(false, false, message != null ? message : "User validation failed");
    }

    public static IdentityValidationSnapshot notAuthorized(String message) {
        return new IdentityValidationSnapshot(true, false, message != null ? message : "User is not authorized for required role");
    }
}
