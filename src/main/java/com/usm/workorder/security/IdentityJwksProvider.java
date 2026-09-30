package com.usm.workorder.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches Group 5's JWK Set from identity-access-service.
 * Resolves a key ID (kid) to an RSA public key, refetching once if a key is not found in cache.
 */
@Component
public class IdentityJwksProvider {

    private static final Logger log = LoggerFactory.getLogger(IdentityJwksProvider.class);

    private final RestClient restClient;
    private final IdentityProperties properties;
    private final AtomicReference<JWKSet> cachedJwkSet = new AtomicReference<>();

    public IdentityJwksProvider(IdentityProperties properties) {
        this(RestClient.builder().build(), properties);
    }

    public IdentityJwksProvider(RestClient restClient, IdentityProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Resolves an RSA public key by kid. If not found in cache, attempts a single refetch.
     *
     * @param kid Key ID to look up
     * @return RSAPublicKey if found, or null if not found/error
     */
    public RSAPublicKey getPublicKey(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }

        JWKSet jwkSet = cachedJwkSet.get();
        if (jwkSet != null) {
            RSAPublicKey key = findKey(jwkSet, kid);
            if (key != null) {
                return key;
            }
        }

        // Unknown kid: refetch once
        jwkSet = fetchJwkSet();
        if (jwkSet != null) {
            cachedJwkSet.set(jwkSet);
            return findKey(jwkSet, kid);
        }

        return null;
    }

    private RSAPublicKey findKey(JWKSet jwkSet, String kid) {
        JWK jwk = jwkSet.getKeyByKeyId(kid);
        if (jwk instanceof RSAKey rsaKey) {
            try {
                return rsaKey.toRSAPublicKey();
            } catch (JOSEException ex) {
                log.error("Failed to convert RSAKey with kid '{}' to RSAPublicKey: {}", kid, ex.getMessage());
                return null;
            }
        }
        return null;
    }

    private synchronized JWKSet fetchJwkSet() {
        String uri = properties.getJwksUri();
        log.debug("Fetching JWKS from {}", uri);
        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                log.warn("Empty JWKS response from {}", uri);
                return null;
            }

            return JWKSet.parse(body);
        } catch (RestClientException ex) {
            log.warn("Could not reach identity-access-service at {} to fetch JWKS: {}", uri, ex.getMessage());
            return null;
        } catch (ParseException ex) {
            log.error("Failed to parse JWKS from {}: {}", uri, ex.getMessage());
            return null;
        }
    }
}
