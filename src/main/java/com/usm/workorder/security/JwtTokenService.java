package com.usm.workorder.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Generates and parses JWTs against the placeholder dev secret in
 * `usm.jwt.*` (application.yml). Also used by
 * {@link com.usm.workorder.client.ServiceRequestClient} to mint the outgoing
 * SERVICE-role token for calls into service-request-service.
 *
 * Claim names (`sub`, `role`, `department`) are the guesses from guide §7 -
 * confirm the real names with Group 5 and update ROLE_CLAIM/DEPARTMENT_CLAIM
 * if they differ (guide §9 question 2).
 */
@Service
public class JwtTokenService {

    static final String ROLE_CLAIM = "role";
    static final String DEPARTMENT_CLAIM = "department";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, Role role, String departmentOrServiceUnit) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(userId)
                .claim(ROLE_CLAIM, role.name())
                .claim(DEPARTMENT_CLAIM, departmentOrServiceUnit)
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Optional<AuthContext> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String roleValue = claims.get(ROLE_CLAIM, String.class);
            String department = claims.get(DEPARTMENT_CLAIM, String.class);

            if (userId == null || roleValue == null) {
                return Optional.empty();
            }

            Role role;
            try {
                role = Role.valueOf(roleValue);
            } catch (IllegalArgumentException unknownRole) {
                return Optional.empty();
            }

            return Optional.of(new AuthContext(userId, role, department));
        } catch (JwtException | IllegalArgumentException invalidToken) {
            return Optional.empty();
        }
    }
}
