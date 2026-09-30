package com.usm.workorder.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates RS256-signed JWTs issued by Group 5's identity-access-service
 * against their published JWKS endpoint.
 */
@Component
public class ExternalTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(ExternalTokenValidator.class);

    private final IdentityJwksProvider jwksProvider;
    private final IdentityProperties properties;

    public ExternalTokenValidator(IdentityJwksProvider jwksProvider, IdentityProperties properties) {
        this.jwksProvider = jwksProvider;
        this.properties = properties;
    }

    /**
     * Validates a raw RS256 JWT from Group 5's identity service.
     *
     * @param rawJwt compact serialized JWT
     * @return validated external claims and mapped roles
     * @throws JwtException if validation fails at any step
     */
    public ExternalTokenValidationResult validate(String rawJwt) {
        if (rawJwt == null || rawJwt.isBlank()) {
            throw new JwtException("Token must not be null or blank");
        }

        SignedJWT signedJwt;
        try {
            signedJwt = SignedJWT.parse(rawJwt);
        } catch (ParseException ex) {
            throw new JwtException("Malformed JWT string: " + ex.getMessage(), ex);
        }

        // 1. Read kid from unverified header, confirm alg is RS256
        JWSHeader header = signedJwt.getHeader();
        if (header.getAlgorithm() == null || !JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            throw new JwtException("Expected RS256 algorithm but received: " + header.getAlgorithm());
        }

        String kid = header.getKeyID();
        if (kid == null || kid.isBlank()) {
            throw new JwtException("Missing 'kid' in JWT header");
        }

        // 2. Get public key from IdentityJwksProvider
        RSAPublicKey publicKey = jwksProvider.getPublicKey(kid);
        if (publicKey == null) {
            throw new JwtException("Public key not found for kid: " + kid);
        }

        // 3. Verify RS256 signature
        try {
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJwt.verify(verifier)) {
                throw new JwtException("RS256 signature verification failed for kid: " + kid);
            }
        } catch (JOSEException ex) {
            throw new JwtException("Signature verification error: " + ex.getMessage(), ex);
        }

        // 4. Validate exp, iss, aud, sub
        JWTClaimsSet claims;
        try {
            claims = signedJwt.getJWTClaimsSet();
        } catch (ParseException ex) {
            throw new JwtException("Failed to parse JWT claims set: " + ex.getMessage(), ex);
        }

        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.before(new Date())) {
            throw new JwtException("Token is expired: " + expirationTime);
        }

        String expectedIssuer = properties.getIssuer();
        String actualIssuer = claims.getIssuer();
        if (actualIssuer == null || !expectedIssuer.equals(actualIssuer)) {
            throw new JwtException("Invalid token issuer: expected '" + expectedIssuer
                    + "' but was '" + actualIssuer + "'");
        }

        String expectedAudience = properties.getAudience();
        List<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(expectedAudience)) {
            throw new JwtException("Invalid token audience: expected '" + expectedAudience
                    + "' but was '" + audiences + "'");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new JwtException("Missing subject ('sub') claim in token");
        }

        // 5. Extract claims and map roles
        String universityId = null;
        try {
            universityId = claims.getStringClaim("university_id");
        } catch (ParseException ignored) {
        }

        String accountType = null;
        try {
            accountType = claims.getStringClaim("account_type");
        } catch (ParseException ignored) {
        }

        Set<Role> roles = extractRoles(claims, sub);

        return new ExternalTokenValidationResult(sub, universityId, accountType, Collections.unmodifiableSet(roles));
    }

    private Set<Role> extractRoles(JWTClaimsSet claims, String sub) {
        Set<String> rawRoleStrings = new LinkedHashSet<>();

        // Check "roles", "role", and "authorities" claims for flexible inter-service compatibility
        extractRawRoleStrings(claims.getClaim("roles"), rawRoleStrings);
        extractRawRoleStrings(claims.getClaim("role"), rawRoleStrings);
        extractRawRoleStrings(claims.getClaim("authorities"), rawRoleStrings);

        Set<Role> roles = new LinkedHashSet<>();
        for (String roleStr : rawRoleStrings) {
            if (roleStr == null || roleStr.isBlank()) {
                continue;
            }
            String normalized = roleStr.trim().toUpperCase();
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring("ROLE_".length()).trim();
            }
            if ("ADMIN_STAFF".equals(normalized)) {
                normalized = "ADMINISTRATIVE_STAFF";
            }
            try {
                roles.add(Role.valueOf(normalized));
            } catch (IllegalArgumentException unknownRole) {
                log.warn("Unrecognized role '{}' in token for user '{}', skipping", roleStr, sub);
            }
        }
        return roles;
    }

    private void extractRawRoleStrings(Object claimValue, Set<String> target) {
        if (claimValue == null) {
            return;
        }
        if (claimValue instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    String str = String.valueOf(item).trim();
                    if (!str.isBlank()) {
                        target.add(str);
                    }
                }
            }
        } else if (claimValue instanceof String str) {
            if (str.contains(",")) {
                for (String part : str.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isBlank()) {
                        target.add(trimmed);
                    }
                }
            } else if (!str.isBlank()) {
                target.add(str.trim());
            }
        }
    }
}
