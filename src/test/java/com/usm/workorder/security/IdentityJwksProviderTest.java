package com.usm.workorder.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityJwksProviderTest {

    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private RestClient.RequestHeadersSpec requestHeadersSpec;
    private RestClient.ResponseSpec responseSpec;
    private IdentityProperties properties;
    private IdentityJwksProvider provider;
    private RSAPublicKey publicKey;
    private String jwksJson;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        this.publicKey = (RSAPublicKey) kp.getPublic();

        RSAKey rsaJwk = new RSAKey.Builder(publicKey).keyID("key-1").build();
        this.jwksJson = new JWKSet(rsaJwk).toString();

        this.restClient = mock(RestClient.class);
        this.requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        this.requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        this.responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(jwksJson);

        this.properties = new IdentityProperties();
        this.properties.setBaseUrl("http://localhost:8001");

        this.provider = new IdentityJwksProvider(restClient, properties);
    }

    @Test
    void getPublicKey_knownKid_returnsPublicKeyAndCaches() {
        RSAPublicKey resolved = provider.getPublicKey("key-1");

        assertThat(resolved).isNotNull();
        assertThat(resolved.getModulus()).isEqualTo(publicKey.getModulus());
        assertThat(resolved.getPublicExponent()).isEqualTo(publicKey.getPublicExponent());

        // Second call should hit cache and NOT fetch again
        RSAPublicKey cached = provider.getPublicKey("key-1");
        assertThat(cached).isNotNull();
        verify(restClient, times(1)).get();
    }

    @Test
    void getPublicKey_unknownKid_refetchesAndReturnsNullIfNotPresent() {
        RSAPublicKey resolved = provider.getPublicKey("unknown-key");

        assertThat(resolved).isNull();
        // Initial fetch + 1 refetch attempt on unknown kid
        verify(restClient, times(1)).get();
    }

    @Test
    void getPublicKey_nullOrBlankKid_returnsNullImmediately() {
        assertThat(provider.getPublicKey(null)).isNull();
        assertThat(provider.getPublicKey("")).isNull();
        assertThat(provider.getPublicKey("   ")).isNull();
        verify(restClient, times(0)).get();
    }
}
