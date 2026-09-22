package com.usm.workorder.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Guide §7: publish the OpenAPI contract now, even in draft form. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI workOrderOpenApi() {
        final String bearerScheme = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("work-order-service API")
                        .description("USM-G7 Sprint 1 - create work orders against a triaged service "
                                + "request, technician start/progress/resolution. See the group's Sprint 1 "
                                + "Backend Developer Guide for the full spec (§3-§7).")
                        .version("0.1.0-SPRINT1"))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme))
                .components(new Components().addSecuritySchemes(bearerScheme,
                        new SecurityScheme()
                                .name(bearerScheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
