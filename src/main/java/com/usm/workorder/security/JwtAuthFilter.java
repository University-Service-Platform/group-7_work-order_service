package com.usm.workorder.security;

import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ExternalTokenValidator externalTokenValidator;
    private final IdentityProperties identityProperties;

    public JwtAuthFilter(JwtTokenService jwtTokenService,
                         ExternalTokenValidator externalTokenValidator,
                         IdentityProperties identityProperties) {
        this.jwtTokenService = jwtTokenService;
        this.externalTokenValidator = externalTokenValidator;
        this.identityProperties = identityProperties;
    }

    public JwtAuthFilter(JwtTokenService jwtTokenService) {
        this(jwtTokenService, null, null);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            String issuer = extractIssuerUnverified(token);
            String externalIssuer = (identityProperties != null && identityProperties.getIssuer() != null)
                    ? identityProperties.getIssuer()
                    : "university-identity-service";

            if (externalIssuer.equals(issuer) && externalTokenValidator != null) {
                // RS256 path from Group 5's identity-access-service
                try {
                    ExternalTokenValidationResult result = externalTokenValidator.validate(token);

                    // Group 5's identity token does not contain a department or service_unit claim.
                    // Step 1 analysis confirmed work-order-service has zero runtime or authorization
                    // dependencies on departmentOrServiceUnit; department is thus safely passed as null.
                    AuthContext authContext = new AuthContext(
                            result.userId(),
                            result.roles(),
                            null,
                            result.universityId(),
                            result.accountType(),
                            token
                    );

                    // Grant a ROLE_<X> authority for EVERY role in the returned set
                    List<SimpleGrantedAuthority> authorities = result.roles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList();

                    var authentication = new UsernamePasswordAuthenticationToken(authContext, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (JwtException | IllegalArgumentException ex) {
                    log.warn("Invalid external identity token: {}", ex.getMessage());
                }
            } else {
                // Fall through to the EXISTING HS256 path, unchanged (no iss or internal placeholder issuer)
                Optional<AuthContext> parsed = jwtTokenService.parseToken(token);

                parsed.ifPresent(authContext -> {
                    AuthContext withToken = new AuthContext(
                            authContext.getUserId(),
                            authContext.getRoles(),
                            authContext.getDepartmentOrServiceUnit(),
                            authContext.getUniversityId(),
                            authContext.getAccountType(),
                            token
                    );
                    List<SimpleGrantedAuthority> authorities = withToken.getRoles().stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                            .toList();
                    var authentication = new UsernamePasswordAuthenticationToken(withToken, token, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractIssuerUnverified(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            return signedJWT.getJWTClaimsSet().getIssuer();
        } catch (Exception ex) {
            return null;
        }
    }
}
