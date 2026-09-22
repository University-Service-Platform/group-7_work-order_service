package com.usm.workorder.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from `services.service-request.*` in application.yml. See guide §13 row on cross-service calls. */
@ConfigurationProperties(prefix = "services.service-request")
public class ServiceRequestClientProperties {

    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
