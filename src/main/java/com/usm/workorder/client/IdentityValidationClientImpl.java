package com.usm.workorder.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Live re-validation client calling Group 5's identity-access-service.
 * Enforces fail-closed semantics: caller token is forwarded directly,
 * and any failure (inactive, not found, unreachable, timeout) aborts execution.
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

    // Constructor for testing with pre-built RestClient
    public IdentityValidationClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public IdentityValidationSnapshot validate(String rawBearerToken) {
        if (rawBearerToken == null || rawBearerToken.isBlank()) {
            log.warn("Identity validation failed: raw bearer token is null or blank");
            return IdentityValidationSnapshot.inactive(null, "Missing bearer token");
        }

        try {
            RemoteIdentityValidationResponse response = restClient.get()
                    .uri("/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + rawBearerToken)
                    .retrieve()
                    .body(RemoteIdentityValidationResponse.class);

            if (response == null) {
                log.warn("Identity validation service returned empty response");
                throw new UpstreamServiceException("Identity validation service returned an empty response");
            }

            boolean exists = response.exists() != null ? response.exists() : true;
            boolean active = response.active() != null ? response.active()
                    : !"INACTIVE".equalsIgnoreCase(response.status());

            if (!exists) {
                return IdentityValidationSnapshot.notFound(response.message());
            }

            if (!active) {
                return IdentityValidationSnapshot.inactive(response.userId(), response.message());
            }

            return IdentityValidationSnapshot.active(response.userId());

        } catch (RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 404) {
                log.warn("Identity validation returned 404: user not found");
                return IdentityValidationSnapshot.notFound("User not found: " + ex.getMessage());
            }
            if (statusCode == 401 || statusCode == 403) {
                log.warn("Identity validation returned {}: token invalid or account inactive", statusCode);
                return IdentityValidationSnapshot.inactive(null, "Token invalid or account inactive: " + ex.getMessage());
            }
            log.warn("Identity validation upstream error {}: {}", statusCode, ex.getMessage());
            throw new UpstreamServiceException(
                    "Identity validation service error (" + statusCode + "): " + ex.getMessage(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Identity validation service timeout or connection failure: {}", ex.getMessage());
            throw new UpstreamServiceException(
                    "Identity validation service unreachable or timed out: " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            log.warn("Could not reach identity validation service: {}", ex.getMessage());
            throw new UpstreamServiceException(
                    "Could not reach identity validation service: " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteIdentityValidationResponse(
            Boolean success,
            Boolean exists,
            Boolean active,
            String status,
            String userId,
            String message
    ) {
    }
}
