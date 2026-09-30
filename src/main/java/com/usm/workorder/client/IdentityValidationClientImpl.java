package com.usm.workorder.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.usm.workorder.exception.UpstreamServiceException;
import com.usm.workorder.security.IdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Live caller re-validation client calling Group 5's identity-access-service.
 * Calls GET /api/v1/validation/users/{user_id}?require_active=true&required_role={role}.
 * Enforces fail-closed semantics: forwards the caller's own bearer token and refuses
 * on inactive (403 ACCOUNT_INACTIVE), not found (404 USER_NOT_FOUND), unreachable, or timeout.
 */
@Component
public class IdentityValidationClientImpl implements IdentityValidationClient {

    private static final Logger log = LoggerFactory.getLogger(IdentityValidationClientImpl.class);

    private final RestClient restClient;

    public IdentityValidationClientImpl(IdentityProperties properties) {
        this(RestClient.builder(), properties);
    }

    public IdentityValidationClientImpl(RestClient.Builder restClientBuilder, IdentityProperties properties) {
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        if (properties != null && properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder = builder.baseUrl(properties.getBaseUrl());
        }
        this.restClient = builder.build();
    }

    public IdentityValidationClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public IdentityValidationSnapshot validateUser(String userId, String requiredRole, String bearerToken) {
        if (userId == null || userId.isBlank() || bearerToken == null || bearerToken.isBlank()) {
            log.warn("Identity validation refused: missing userId or bearer token");
            return IdentityValidationSnapshot.invalid("Missing user ID or bearer token");
        }

        try {
            RemoteValidationResponse response = restClient.get()
                    .uri("/api/v1/validation/users/{userId}?require_active=true&required_role={requiredRole}",
                            userId, requiredRole != null ? requiredRole : "")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(RemoteValidationResponse.class);

            if (response == null) {
                log.warn("Identity validation service returned empty response for user {}", userId);
                throw new UpstreamServiceException("Identity validation service returned an empty response");
            }

            boolean isValid = Boolean.TRUE.equals(response.isValid());
            boolean isAuthorized = Boolean.TRUE.equals(response.isAuthorized());

            if (!isValid) {
                return IdentityValidationSnapshot.invalid(
                        response.message() != null ? response.message() : "User is invalid or inactive");
            }

            if (!isAuthorized) {
                return IdentityValidationSnapshot.notAuthorized(
                        response.message() != null ? response.message() : "User not authorized for required role " + requiredRole);
            }

            return IdentityValidationSnapshot.valid(response.message());

        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("Identity validation returned 404 USER_NOT_FOUND for user {}: {}", userId, ex.getMessage());
                return IdentityValidationSnapshot.invalid("USER_NOT_FOUND: " + ex.getMessage());
            }
            if (statusCode == 403) {
                log.warn("Identity validation returned 403 ACCOUNT_INACTIVE for user {}: {}", userId, ex.getMessage());
                return IdentityValidationSnapshot.invalid("ACCOUNT_INACTIVE: " + ex.getMessage());
            }
            if (statusCode == 401) {
                log.warn("Identity validation returned 401 UNAUTHORIZED for user {}: {}", userId, ex.getMessage());
                return IdentityValidationSnapshot.invalid("UNAUTHORIZED: " + ex.getMessage());
            }
            log.warn("Identity validation upstream error {} for user {}: {}", statusCode, userId, ex.getMessage());
            throw new UpstreamServiceException(
                    "Identity validation service error (" + statusCode + "): " + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Identity validation service timeout or connection failure for user {}: {}", userId, ex.getMessage());
            throw new UpstreamServiceException(
                    "Identity validation service unreachable or timed out: " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            log.warn("Could not reach identity validation service for user {}: {}", userId, ex.getMessage());
            throw new UpstreamServiceException(
                    "Could not reach identity validation service: " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteValidationResponse(
            @JsonProperty("is_valid") Boolean isValid,
            @JsonProperty("is_authorized") Boolean isAuthorized,
            @JsonProperty("message") String message,
            @JsonProperty("error") String error
    ) {
    }
}
