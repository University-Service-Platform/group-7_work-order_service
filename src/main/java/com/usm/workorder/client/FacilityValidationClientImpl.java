package com.usm.workorder.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class FacilityValidationClientImpl implements FacilityValidationClient {

    private static final Logger log = LoggerFactory.getLogger(FacilityValidationClientImpl.class);

    private final RestClient restClient;

    public FacilityValidationClientImpl(@Qualifier("facilityValidationRestClient") RestClient facilityValidationRestClient) {
        this.restClient = facilityValidationRestClient;
    }

    @Override
    public FacilityValidationSnapshot validateByCode(String code) {
        try {
            RemoteFacilityValidationResponse response = restClient.get()
                    .uri("/api/resources/code/{code}/validate", code)
                    .retrieve()
                    .body(RemoteFacilityValidationResponse.class);

            if (response == null || response.data() == null) {
                log.warn("Facility validation service returned empty response for code: {}", code);
                return new FacilityValidationSnapshot(
                        false, false, false, false,
                        "Facility validation service unreachable or errored: empty response",
                        null, null, null
                );
            }

            FacilityResourceData data = response.data();
            return new FacilityValidationSnapshot(
                    Boolean.TRUE.equals(data.exists()),
                    Boolean.TRUE.equals(data.active()),
                    Boolean.TRUE.equals(data.available()),
                    Boolean.TRUE.equals(data.validForReservation()),
                    data.message() != null ? data.message() : response.message(),
                    data.capacity(),
                    data.operatingHoursStart(),
                    data.operatingHoursEnd()
            );

        } catch (RestClientResponseException ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex.getStatusCode());
            log.warn("Facility validation service returned error for code {}: {}", code, reason);
            return new FacilityValidationSnapshot(
                    false, false, false, false,
                    "Facility validation service unreachable or errored: " + reason,
                    null, null, null
            );
        } catch (RestClientException ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Could not reach facility validation service for code {}: {}", code, reason);
            return new FacilityValidationSnapshot(
                    false, false, false, false,
                    "Facility validation service unreachable or errored: " + reason,
                    null, null, null
            );
        } catch (Exception ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Unexpected error during facility validation for code {}: {}", code, reason, ex);
            return new FacilityValidationSnapshot(
                    false, false, false, false,
                    "Facility validation service unreachable or errored: " + reason,
                    null, null, null
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RemoteFacilityValidationResponse(
            Boolean success,
            String message,
            FacilityResourceData data,
            String timestamp
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FacilityResourceData(
            Long resourceId,
            String resourceCode,
            Long facilityId,
            Boolean exists,
            Boolean active,
            Boolean available,
            Integer capacity,
            Boolean approvalRequired,
            String operatingHoursStart,
            String operatingHoursEnd,
            Boolean validForReservation,
            String message
    ) {
    }
}
