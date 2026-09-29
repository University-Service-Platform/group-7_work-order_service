package com.usm.workorder.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private static final String TEST_KID = "filter-test-kid";
    private static final String ISSUER = "university-identity-service";
    private static final String AUDIENCE = "university-services-platform";

    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;
    private IdentityJwksProvider jwksProvider;
    private IdentityProperties identityProperties;
    private ExternalTokenValidator externalTokenValidator;
    private JwtProperties jwtProperties;
    private JwtTokenService jwtTokenService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();

        // RSA setup for external RS256 tokens
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        this.rsaPublicKey = (RSAPublicKey) kp.getPublic();
        this.rsaPrivateKey = (RSAPrivateKey) kp.getPrivate();

        this.jwksProvider = mock(IdentityJwksProvider.class);
        when(jwksProvider.getPublicKey(TEST_KID)).thenReturn(rsaPublicKey);

        this.identityProperties = new IdentityProperties();
        this.identityProperties.setIssuer(ISSUER);
        this.identityProperties.setAudience(AUDIENCE);

        this.externalTokenValidator = new ExternalTokenValidator(jwksProvider, identityProperties);

        // HS256 setup for existing internal tokens
        this.jwtProperties = new JwtProperties();
        this.jwtProperties.setSecret("test-secret-must-be-at-least-32-bytes-long-12345");
        this.jwtProperties.setIssuer("usm-g7-dev");
        this.jwtProperties.setExpirationMinutes(60);
        this.jwtTokenService = new JwtTokenService(jwtProperties);

        this.filter = new JwtAuthFilter(jwtTokenService, externalTokenValidator, identityProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String createRs256Jwt(List<String> roles) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("usr-tech-01")
                .claim("university_id", "T001")
                .claim("account_type", "STAFF")
                .claim("roles", roles)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaPrivateKey));
        return signedJWT.serialize();
    }

    @Test
    void doFilter_externalRs256WithMultipleRoles_grantsMultipleAuthoritiesIncludingTechnician() throws Exception {
        String token = createRs256Jwt(List.of("SERVICE_DESK_OFFICER", "TECHNICIAN"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthContext.class);

        AuthContext context = (AuthContext) auth.getPrincipal();
        assertThat(context.getUserId()).isEqualTo("usr-tech-01");
        assertThat(context.getUniversityId()).isEqualTo("T001");
        assertThat(context.getAccountType()).isEqualTo("STAFF");
        assertThat(context.getRoles()).containsExactlyInAnyOrder(Role.SERVICE_DESK_OFFICER, Role.TECHNICIAN);
        assertThat(context.hasRole(Role.TECHNICIAN)).isTrue();
        assertThat(context.hasRole(Role.SERVICE_DESK_OFFICER)).isTrue();
        // Department is null for external tokens
        assertThat(context.getDepartmentOrServiceUnit()).isNull();

        // Authorities granted must include ROLE_TECHNICIAN and ROLE_SERVICE_DESK_OFFICER
        Set<String> authorityNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(authorityNames).containsExactlyInAnyOrder("ROLE_SERVICE_DESK_OFFICER", "ROLE_TECHNICIAN");

        // Verify hasRole('TECHNICIAN') check passes
        boolean hasTechnicianRole = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TECHNICIAN".equals(a.getAuthority()));
        assertThat(hasTechnicianRole).isTrue();
    }

    @Test
    void doFilter_internalHs256Token_fallsThroughToExistingHandlerUnchanged() throws Exception {
        String hs256Token = jwtTokenService.generateToken("tech-100", Role.TECHNICIAN, "Facilities");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + hs256Token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        AuthContext context = (AuthContext) auth.getPrincipal();
        assertThat(context.getUserId()).isEqualTo("tech-100");
        assertThat(context.getRole()).isEqualTo(Role.TECHNICIAN);
        assertThat(context.getDepartmentOrServiceUnit()).isEqualTo("Facilities");
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .containsExactly("ROLE_TECHNICIAN");
    }

    @Test
    void doFilter_internalHs256ServiceToken_authenticatesSuccessfully() throws Exception {
        String serviceToken = jwtTokenService.generateToken("work-order-service", Role.SERVICE, "SYSTEM");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + serviceToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        AuthContext context = (AuthContext) auth.getPrincipal();
        assertThat(context.isServiceCall()).isTrue();
        assertThat(auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .containsExactly("ROLE_SERVICE");
    }

    @Test
    void doFilter_invalidExternalToken_doesNotSetAuthentication() throws Exception {
        // Expired token
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(TEST_KID).build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("usr-tech-01")
                .expirationTime(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)))
                .claim("roles", List.of("TECHNICIAN"))
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new RSASSASigner(rsaPrivateKey));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + signedJWT.serialize());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_noAuthorizationHeader_passesThroughWithoutAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
