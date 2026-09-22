package com.usm.workorder.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ServiceRequestClientConfig {

    /**
     * Base URL comes from `services.service-request.base-url` (application.yml) - swap it for
     * a Docker Compose service name once QA/DevOps wires the real network (guide §13).
     * RestClient (Spring 6.1+/Boot 3.2+) is used instead of the older RestTemplate because it
     * supports PATCH out of the box via the JDK HttpClient, which the internal /status callback
     * needs.
     */
    @Bean
    public RestClient serviceRequestRestClient(ServiceRequestClientProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
