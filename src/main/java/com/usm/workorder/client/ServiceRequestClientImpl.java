package com.usm.workorder.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.usm.workorder.exception.ResourceNotFoundException;
import com.usm.workorder.exception.UpstreamServiceException;
import com.usm.workorder.security.JwtTokenService;
import com.usm.workorder.security.Role;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Real HTTP implementation of {@link ServiceRequestClient} - this is guide
 * §10 step 3's "first real cross-service integration test": run both
 * services locally and exercise this class end to end before trusting it.
 *
 * Auth: mints its own short-lived SERVICE-role JWT per call, signed with the
 * SAME placeholder secret as service-request-service (Sprint 1
 * simplification - see README "Service-to-service auth"). Swap only this
 * class (and the equivalent JwtTokenService/JwtProperties files) once real
 * service-to-service auth exists.
 */
@Component
public class ServiceRequestClientImpl implements ServiceRequestClient {

    private static final String SERVICE_CALLER_ID = "work-order-service";
    private static final String SERVICE_DEPARTMENT = "SYSTEM";

    private final RestClient restClient;
    private final JwtTokenService jwtTokenService;

    public ServiceRequestClientImpl(RestClient serviceRequestRestClient, JwtTokenService jwtTokenService) {
        this.restClient = serviceRequestRestClient;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public ServiceRequestSnapshot fetchRequest(String requestId) {
        String serviceToken = jwtTokenService.generateToken(SERVICE_CALLER_ID, Role.SERVICE, SERVICE_DEPARTMENT);

        try {
            RemoteServiceRequestResponse response = restClient.get()
                    .uri("/api/service-requests/{id}", requestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .retrieve()
                    .body(RemoteServiceRequestResponse.class);

            if (response == null) {
                throw new UpstreamServiceException(
                        "service-request-service returned an empty response for " + requestId);
            }

            return new ServiceRequestSnapshot(response.requestId(), response.status(),
                    response.location(), response.category());

        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("No service request found with id " + requestId);
            }
            throw new UpstreamServiceException(
                    "service-request-service rejected the lookup for " + requestId + ": "
                            + ex.getStatusCode() + " " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException(
                    "Could not reach service-request-service to validate " + requestId
                            + " - is it running at the configured base URL? (" + ex.getMessage() + ")", ex);
        }
    }

    @Override
    public void pushStatusUpdate(String requestId, String status) {
        String serviceToken = jwtTokenService.generateToken(SERVICE_CALLER_ID, Role.SERVICE, SERVICE_DEPARTMENT);

        try {
            restClient.patch()
                    .uri("/api/service-requests/{id}/status", requestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // Deliberately non-fatal to the caller's own state change would be worse than losing
            // sync for a moment - but we DO surface it as an error so the technician/officer knows
            // the parent request may now be out of sync until someone retries or fixes it manually.
            throw new UpstreamServiceException(
                    "work-order-service updated its own record, but could not push the status change "
                            + "back to service-request-service for " + requestId + ": " + ex.getMessage(), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteServiceRequestResponse(String requestId, String status, String location, String category) {
    }
}
