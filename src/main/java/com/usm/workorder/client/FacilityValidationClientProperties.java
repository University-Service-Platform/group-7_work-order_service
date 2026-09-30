package com.usm.workorder.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from `services.group6-facility.*` in application.yml.
 * Client configuration for calling Group 6's facility-resource-service.
 */
@ConfigurationProperties(prefix = "services.group6-facility")
public class FacilityValidationClientProperties {

    private String baseUrl;
    private boolean enforceValidation = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnforceValidation() {
        return enforceValidation;
    }

    public void setEnforceValidation(boolean enforceValidation) {
        this.enforceValidation = enforceValidation;
    }
}
