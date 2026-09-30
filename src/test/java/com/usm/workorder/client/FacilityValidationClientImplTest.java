package com.usm.workorder.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacilityValidationClientImplTest {

    private MockRestServiceServer mockServer;
    private FacilityValidationClient client;

    private static final String VALID_AVAILABLE_JSON = """
            {
              "success": true, "message": "Group 8 resource validation completed",
              "data": {
                "resourceId": 1, "resourceCode": "LAB-101", "facilityId": 1,
                "exists": true, "active": true, "available": true,
                "capacity": 30, "approvalRequired": false,
                "operatingHoursStart": "08:00:00", "operatingHoursEnd": "20:00:00",
                "validForReservation": true, "message": "Resource is valid and available for reservation"
              },
              "timestamp": "2026-09-25T19:22:00"
            }
            """;

    private static final String NOT_FOUND_JSON = """
            { "success": true, "message": "Group 8 resource validation completed",
              "data": { "resourceId": 999, "resourceCode": null, "facilityId": null,
                "exists": false, "active": false, "available": false, "capacity": null,
                "approvalRequired": false, "operatingHoursStart": null, "operatingHoursEnd": null,
                "validForReservation": false, "message": "Resource with ID 999 does not exist" },
              "timestamp": "2026-09-25T19:22:00" }
            """;

    private static final String INACTIVE_UNAVAILABLE_JSON = """
            { "success": true, "message": "Group 8 resource validation completed",
              "data": { "resourceId": 1, "resourceCode": "LAB-101", "facilityId": 1,
                "exists": true, "active": true, "available": false, "capacity": 30,
                "approvalRequired": false, "operatingHoursStart": "08:00:00", "operatingHoursEnd": "20:00:00",
                "validForReservation": false, "message": "Resource is currently marked unavailable" },
              "timestamp": "2026-09-25T19:22:00" }
            """;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8081");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new FacilityValidationClientImpl(restClient);
    }

    @Test
    void validateByCode_validAndAvailable_returnsValidForReservationTrue() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(VALID_AVAILABLE_JSON, MediaType.APPLICATION_JSON));

        FacilityValidationSnapshot snapshot = client.validateByCode("LAB-101");

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.validForReservation()).isTrue();
        assertThat(snapshot.message()).isEqualTo("Resource is valid and available for reservation");
        assertThat(snapshot.capacity()).isEqualTo(30);
        assertThat(snapshot.operatingHoursStart()).isEqualTo("08:00:00");
        assertThat(snapshot.operatingHoursEnd()).isEqualTo("20:00:00");
        mockServer.verify();
    }

    @Test
    void validateByCode_notFound_returnsExistsFalseAndValidForReservationFalse() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/UNKNOWN-999/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(NOT_FOUND_JSON, MediaType.APPLICATION_JSON));

        FacilityValidationSnapshot snapshot = client.validateByCode("UNKNOWN-999");

        assertThat(snapshot.exists()).isFalse();
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.validForReservation()).isFalse();
        assertThat(snapshot.message()).isEqualTo("Resource with ID 999 does not exist");
        assertThat(snapshot.capacity()).isNull();
        assertThat(snapshot.operatingHoursStart()).isNull();
        assertThat(snapshot.operatingHoursEnd()).isNull();
        mockServer.verify();
    }

    @Test
    void validateByCode_inactiveOrUnavailable_returnsExistsTrueAvailableFalseValidForReservationFalse() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(INACTIVE_UNAVAILABLE_JSON, MediaType.APPLICATION_JSON));

        FacilityValidationSnapshot snapshot = client.validateByCode("LAB-101");

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.validForReservation()).isFalse();
        assertThat(snapshot.message()).isEqualTo("Resource is currently marked unavailable");
        assertThat(snapshot.capacity()).isEqualTo(30);
        assertThat(snapshot.operatingHoursStart()).isEqualTo("08:00:00");
        assertThat(snapshot.operatingHoursEnd()).isEqualTo("20:00:00");
        mockServer.verify();
    }

    @Test
    void validateByCode_serverError500_returnsSafeFallbackSnapshotInsteadOfThrowing() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        FacilityValidationSnapshot snapshot = client.validateByCode("LAB-101");

        assertThat(snapshot.exists()).isFalse();
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.validForReservation()).isFalse();
        assertThat(snapshot.message()).contains("Facility validation service unreachable or errored");
        mockServer.verify();
    }

    @Test
    void validateByCode_connectionFailure_returnsSafeFallbackSnapshotInsteadOfThrowing() {
        mockServer.expect(requestTo("http://localhost:8081/api/resources/code/LAB-101/validate"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection refused: connect");
                });

        FacilityValidationSnapshot snapshot = client.validateByCode("LAB-101");

        assertThat(snapshot.exists()).isFalse();
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.validForReservation()).isFalse();
        assertThat(snapshot.message()).contains("Facility validation service unreachable or errored");
        mockServer.verify();
    }
}
