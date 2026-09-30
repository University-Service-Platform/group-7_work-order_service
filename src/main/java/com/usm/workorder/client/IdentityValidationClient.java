package com.usm.workorder.client;

public interface IdentityValidationClient {

    IdentityValidationSnapshot validateUser(String userId, String requiredRole, String bearerToken);
}
