package com.usm.workorder.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-only-secret-key-at-least-32-bytes-long-000000");
        properties.setIssuer("usm-g7-test");
        properties.setExpirationMinutes(60);
        jwtTokenService = new JwtTokenService(properties);
    }

    @Test
    void generateThenParse_roundTripsTheSameClaims() {
        String token = jwtTokenService.generateToken("tech-1", Role.TECHNICIAN, "Facilities");

        Optional<AuthContext> parsed = jwtTokenService.parseToken(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().getUserId()).isEqualTo("tech-1");
        assertThat(parsed.get().getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(parsed.get().getDepartmentOrServiceUnit()).isEqualTo("Facilities");
    }

    @Test
    void parseToken_garbageInput_returnsEmptyRatherThanThrowing() {
        assertThat(jwtTokenService.parseToken("not-a-real-jwt")).isEmpty();
    }

    @Test
    void generateToken_serviceRole_roundTripsForInternalCalls() {
        String token = jwtTokenService.generateToken("work-order-service", Role.SERVICE, "SYSTEM");

        Optional<AuthContext> parsed = jwtTokenService.parseToken(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().isServiceCall()).isTrue();
    }

    @Test
    void parseToken_tokenSignedWithADifferentKey_isRejected() {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret("a-completely-different-secret-key-of-at-least-32-bytes");
        otherProperties.setIssuer("usm-g7-test");
        otherProperties.setExpirationMinutes(60);
        JwtTokenService otherService = new JwtTokenService(otherProperties);

        String tokenFromOtherService = otherService.generateToken("u-999", Role.STUDENT, "Faculty of Science");

        assertThat(jwtTokenService.parseToken(tokenFromOtherService)).isEmpty();
    }
}
