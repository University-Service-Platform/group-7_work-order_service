package com.usm.workorder.client;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FacilityValidationClientProperties.class)
public class FacilityValidationClientConfig {

    @Bean
    public RestClient facilityValidationRestClient(FacilityValidationClientProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }
}
