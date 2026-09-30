package com.usm.workorder.client;

import com.usm.workorder.exception.UpstreamServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityValidationClientImplTest {

    private MockRestServiceServer mockServer;
    private IdentityValidationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8001");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        client = new IdentityValidationClientImpl(builder.build());
    }

    @Test
    void validateUser_whenValidAndAuthorized_returnsValidSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/v1/validation/users/user-123?require_active=true&required_role=SERVICE_DESK_OFFICER"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {
                            "is_valid": true,
                            "is_authorized": true,
                            "message": "User is active and authorized"
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityValidationSnapshot snapshot = client.validateUser("user-123", "SERVICE_DESK_OFFICER", "valid-token");

        assertThat(snapshot.isValid()).isTrue();
        assertThat(snapshot.isAuthorized()).isTrue();
        mockServer.verify();
    }

    @Test
    void validateUser_whenValidButNotAuthorized_returnsNotAuthorizedSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/v1/validation/users/user-123?require_active=true&required_role=TECHNICIAN"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {
                            "is_valid": true,
                            "is_authorized": false,
                            "message": "User does not have required role TECHNICIAN"
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityValidationSnapshot snapshot = client.validateUser("user-123", "TECHNICIAN", "valid-token");

        assertThat(snapshot.isValid()).isTrue();
        assertThat(snapshot.isAuthorized()).isFalse();
        mockServer.verify();
    }

    @Test
    void validateUser_when403AccountInactive_returnsInvalidSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/v1/validation/users/user-inactive?require_active=true&required_role=TECHNICIAN"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withStatus(FORBIDDEN));

        IdentityValidationSnapshot snapshot = client.validateUser("user-inactive", "TECHNICIAN", "token");

        assertThat(snapshot.isValid()).isFalse();
        assertThat(snapshot.isAuthorized()).isFalse();
        assertThat(snapshot.message()).contains("ACCOUNT_INACTIVE");
        mockServer.verify();
    }

    @Test
    void validateUser_when404UserNotFound_returnsInvalidSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/v1/validation/users/user-unknown?require_active=true&required_role=TECHNICIAN"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withStatus(NOT_FOUND));

        IdentityValidationSnapshot snapshot = client.validateUser("user-unknown", "TECHNICIAN", "token");

        assertThat(snapshot.isValid()).isFalse();
        assertThat(snapshot.isAuthorized()).isFalse();
        assertThat(snapshot.message()).contains("USER_NOT_FOUND");
        mockServer.verify();
    }

    @Test
    void validateUser_when500ServerError_throwsUpstreamServiceException() {
        mockServer.expect(requestTo("http://localhost:8001/api/v1/validation/users/user-err?require_active=true&required_role=TECHNICIAN"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.validateUser("user-err", "TECHNICIAN", "token"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Identity validation service error");
        mockServer.verify();
    }

    @Test
    void validateUser_whenMissingUserIdOrToken_returnsInvalid() {
        IdentityValidationSnapshot noUser = client.validateUser(null, "TECHNICIAN", "token");
        assertThat(noUser.isValid()).isFalse();

        IdentityValidationSnapshot noToken = client.validateUser("user-1", "TECHNICIAN", null);
        assertThat(noToken.isValid()).isFalse();
    }
}
