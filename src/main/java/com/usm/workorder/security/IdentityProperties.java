package com.usm.workorder.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Group 5's identity-access-service.
 * Bound from `services.identity.*` in application.yml.
 */
@ConfigurationProperties(prefix = "services.identity")
public class IdentityProperties {

    private String baseUrl = "http://localhost:8001";
    private String jwksUri;
    private String issuer = "university-identity-service";
    private String audience = "university-services-platform";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getJwksUri() {
        if (jwksUri != null && !jwksUri.isBlank()) {
            return jwksUri;
        }
        return baseUrl + "/.well-known/jwks.json";
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
