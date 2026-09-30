package com.usm.workorder.client;

import com.usm.workorder.exception.UpstreamServiceException;
import com.usm.workorder.security.IdentityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

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
    void validate_whenActive_returnsActiveSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/auth/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andRespond(withSuccess("""
                        {
                            "success": true,
                            "exists": true,
                            "active": true,
                            "userId": "user-123",
                            "message": "User is active"
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityValidationSnapshot snapshot = client.validate("valid-token");

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.active()).isTrue();
        assertThat(snapshot.userId()).isEqualTo("user-123");
        mockServer.verify();
    }

    @Test
    void validate_whenActiveFalseInBody_returnsInactiveSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/auth/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer inactive-token"))
                .andRespond(withSuccess("""
                        {
                            "success": true,
                            "exists": true,
                            "active": false,
                            "userId": "user-456",
                            "message": "User account is suspended"
                        }
                        """, MediaType.APPLICATION_JSON));

        IdentityValidationSnapshot snapshot = client.validate("inactive-token");

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.userId()).isEqualTo("user-456");
        mockServer.verify();
    }

    @Test
    void validate_when404_returnsNotFoundSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/auth/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer unknown-token"))
                .andRespond(withResourceNotFound());

        IdentityValidationSnapshot snapshot = client.validate("unknown-token");

        assertThat(snapshot.exists()).isFalse();
        assertThat(snapshot.active()).isFalse();
        mockServer.verify();
    }

    @Test
    void validate_when401Or403_returnsInactiveSnapshot() {
        mockServer.expect(requestTo("http://localhost:8001/api/auth/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andRespond(withStatus(UNAUTHORIZED));

        IdentityValidationSnapshot snapshot = client.validate("expired-token");

        assertThat(snapshot.active()).isFalse();
        mockServer.verify();
    }

    @Test
    void validate_when500_throwsUpstreamServiceException() {
        mockServer.expect(requestTo("http://localhost:8001/api/auth/validate"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer error-token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.validate("error-token"))
                .isInstanceOf(UpstreamServiceException.class)
                .hasMessageContaining("Identity validation service error");
        mockServer.verify();
    }

    @Test
    void validate_whenNullOrBlankToken_returnsInactive() {
        IdentityValidationSnapshot nullSnapshot = client.validate(null);
        assertThat(nullSnapshot.active()).isFalse();

        IdentityValidationSnapshot blankSnapshot = client.validate("   ");
        assertThat(blankSnapshot.active()).isFalse();
    }
}
