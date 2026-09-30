package com.usm.workorder.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalTokenValidatorTest {

    private static final String TEST_KID = "test-key-id-001";
    private static final String ISSUER = "university-identity-service";
    private static final String AUDIENCE = "university-services-platform";

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private IdentityJwksProvider jwksProvider;
    private IdentityProperties properties;
    private ExternalTokenValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        this.publicKey = (RSAPublicKey) kp.getPublic();
        this.privateKey = (RSAPrivateKey) kp.getPrivate();

        this.jwksProvider = mock(IdentityJwksProvider.class);
        when(jwksProvider.getPublicKey(TEST_KID)).thenReturn(publicKey);

        this.properties = new IdentityProperties();
        this.properties.setIssuer(ISSUER);
        this.properties.setAudience(AUDIENCE);

        this.validator = new ExternalTokenValidator(jwksProvider, properties);
    }

    private String createSignedJwt(String kid, String issuer, String audience, Date expiration,
                                   String subject, List<String> roles) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .build();

        JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .claim("university_id", "SDO001")
                .claim("account_type", "STAFF")
                .issueTime(new Date())
                .expirationTime(expiration);

        if (roles != null) {
            claimsBuilder.claim("roles", roles);
        }

        SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    @Test
    void validate_validToken_successWithParsedClaimsAndRoles() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, ISSUER, AUDIENCE, exp, "usr-tech-01",
                List.of("TECHNICIAN", "SERVICE_DESK_OFFICER"));

        ExternalTokenValidationResult result = validator.validate(token);

        assertThat(result.userId()).isEqualTo("usr-tech-01");
        assertThat(result.universityId()).isEqualTo("SDO001");
        assertThat(result.accountType()).isEqualTo("STAFF");
        assertThat(result.roles()).containsExactlyInAnyOrder(Role.TECHNICIAN, Role.SERVICE_DESK_OFFICER);
    }

    @Test
    void validate_expiredToken_rejectedWithJwtException() throws Exception {
        Date pastExp = Date.from(Instant.now().minus(10, ChronoUnit.MINUTES));
        String token = createSignedJwt(TEST_KID, ISSUER, AUDIENCE, pastExp, "usr-tech-01",
                List.of("TECHNICIAN"));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validate_wrongIssuer_rejectedWithJwtException() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, "wrong-issuer", AUDIENCE, exp, "usr-tech-01",
                List.of("TECHNICIAN"));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void validate_wrongAudience_rejectedWithJwtException() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, ISSUER, "wrong-audience", exp, "usr-tech-01",
                List.of("TECHNICIAN"));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void validate_unrecognizedRole_isSkippedAndNotFatal() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, ISSUER, AUDIENCE, exp, "usr-tech-01",
                List.of("TECHNICIAN", "UNKNOWN_FUTURE_ROLE_XYZ", "STAFF"));

        ExternalTokenValidationResult result = validator.validate(token);

        assertThat(result.roles()).containsExactlyInAnyOrder(Role.TECHNICIAN, Role.STAFF);
    }

    @Test
    void validate_unknownKid_rejectedWithJwtException() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt("unknown-kid", ISSUER, AUDIENCE, exp, "usr-tech-01",
                List.of("TECHNICIAN"));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Public key not found");
    }

    @Test
    void validate_differentPrivateKey_rejectedSignature() throws Exception {
        KeyPairGenerator otherKpg = KeyPairGenerator.getInstance("RSA");
        otherKpg.initialize(2048);
        KeyPair otherKp = otherKpg.generateKeyPair();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("usr-tech-01")
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .claim("roles", List.of("TECHNICIAN"))
                .build();

        SignedJWT otherSignedJwt = new SignedJWT(header, claims);
        otherSignedJwt.sign(new RSASSASigner((RSAPrivateKey) otherKp.getPrivate()));
        String token = otherSignedJwt.serialize();

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void validate_missingSubject_rejectedWithJwtException() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, ISSUER, AUDIENCE, exp, null,
                List.of("TECHNICIAN"));

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void validate_rolesWithRolePrefix_stripsPrefixAndMapsCorrectly() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        String token = createSignedJwt(TEST_KID, ISSUER, AUDIENCE, exp, "usr-tech-01",
                List.of("ROLE_TECHNICIAN", "ROLE_SERVICE_DESK_OFFICER"));

        ExternalTokenValidationResult result = validator.validate(token);

        assertThat(result.roles()).containsExactlyInAnyOrder(Role.TECHNICIAN, Role.SERVICE_DESK_OFFICER);
    }

    @Test
    void validate_singularRoleClaim_mapsCorrectly() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("usr-tech-01")
                .expirationTime(exp)
                .claim("role", "TECHNICIAN")
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(privateKey));

        ExternalTokenValidationResult result = validator.validate(signedJWT.serialize());

        assertThat(result.roles()).containsExactly(Role.TECHNICIAN);
    }

    @Test
    void validate_authoritiesClaim_mapsCorrectly() throws Exception {
        Date exp = Date.from(Instant.now().plus(1, ChronoUnit.HOURS));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("usr-tech-01")
                .expirationTime(exp)
                .claim("authorities", List.of("ROLE_ADMINISTRATIVE_STAFF"))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(privateKey));

        ExternalTokenValidationResult result = validator.validate(signedJWT.serialize());

        assertThat(result.roles()).containsExactly(Role.ADMINISTRATIVE_STAFF);
    }
}
