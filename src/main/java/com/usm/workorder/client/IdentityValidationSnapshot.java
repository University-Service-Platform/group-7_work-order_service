package com.usm.workorder.client;

/**
 * Snapshot of live caller identity verification returned by Group 5's
 * identity-access-service.
 */
public record IdentityValidationSnapshot(
        boolean exists,
        boolean active,
        String userId,
        String message
) {
    public static IdentityValidationSnapshot active(String userId) {
        return new IdentityValidationSnapshot(true, true, userId, "User is active");
    }

    public static IdentityValidationSnapshot inactive(String userId, String message) {
        return new IdentityValidationSnapshot(true, false, userId, message != null ? message : "User is inactive");
    }

    public static IdentityValidationSnapshot notFound(String message) {
        return new IdentityValidationSnapshot(false, false, null, message != null ? message : "User not found");
    }
}
