package com.usm.workorder.security;

import java.util.Set;

/**
 * Result of validating an RS256 token issued by Group 5's identity-access-service.
 */
public record ExternalTokenValidationResult(
        String userId,
        String universityId,
        String accountType,
        Set<Role> roles
) {
}
