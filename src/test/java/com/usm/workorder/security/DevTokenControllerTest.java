package com.usm.workorder.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DevTokenControllerTest {

    private JwtTokenService jwtTokenService;
    private DevTokenController controller;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-dev-secret-key-that-is-at-least-32-bytes-long");
        properties.setIssuer("usm-g7-dev");
        properties.setExpirationMinutes(60);

        jwtTokenService = new JwtTokenService(properties);
        controller = new DevTokenController(jwtTokenService);
    }

    @Test
    void issueDevToken_administrativeStaffRole_generatesValidToken() {
        var request = new DevTokenController.DevTokenRequest("admin-1", "ADMINISTRATIVE_STAFF", "Operations");
        Map<String, String> response = controller.issueDevToken(request);

        assertThat(response).containsKey("token");
        Optional<AuthContext> parsed = jwtTokenService.parseToken(response.get("token"));
        assertThat(parsed).isPresent();
        assertThat(parsed.get().getRole()).isEqualTo(Role.ADMINISTRATIVE_STAFF);
        assertThat(parsed.get().getUserId()).isEqualTo("admin-1");
    }

    @Test
    void issueDevToken_adminStaffAlias_mappedToAdministrativeStaff() {
        var request = new DevTokenController.DevTokenRequest("admin-2", "ADMIN_STAFF", "Operations");
        Map<String, String> response = controller.issueDevToken(request);

        assertThat(response).containsKey("token");
        Optional<AuthContext> parsed = jwtTokenService.parseToken(response.get("token"));
        assertThat(parsed).isPresent();
        assertThat(parsed.get().getRole()).isEqualTo(Role.ADMINISTRATIVE_STAFF);
    }

    @Test
    void issueDevToken_technicianRole_generatesValidToken() {
        var request = new DevTokenController.DevTokenRequest("tech-1", "TECHNICIAN", "Facilities");
        Map<String, String> response = controller.issueDevToken(request);

        assertThat(response).containsKey("token");
        Optional<AuthContext> parsed = jwtTokenService.parseToken(response.get("token"));
        assertThat(parsed).isPresent();
        assertThat(parsed.get().getRole()).isEqualTo(Role.TECHNICIAN);
    }
}
