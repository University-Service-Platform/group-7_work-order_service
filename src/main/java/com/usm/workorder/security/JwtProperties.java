package com.usm.workorder.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from `usm.jwt.*` in application.yml - the ONE place the placeholder
 * secret/issuer live. See guide §7/§13 row 1.
 */
@ConfigurationProperties(prefix = "usm.jwt")
public class JwtProperties {

    private String secret;
    private String issuer;
    private long expirationMinutes = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
