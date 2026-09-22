package com.usm.workorder.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * DEV-ONLY. Mints a placeholder JWT so you can call protected endpoints
 * locally before Group 5's real login exists. Only active when
 * `usm.dev-tools.enabled=true` (application-dev.yml). Never enable this
 * profile anywhere but your own machine.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(prefix = "usm.dev-tools", name = "enabled", havingValue = "true")
public class DevTokenController {

    private final JwtTokenService jwtTokenService;

    public DevTokenController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/token")
    public Map<String, String> issueDevToken(@RequestBody DevTokenRequest request) {
        Role role = Role.valueOf(request.role().toUpperCase());
        String token = jwtTokenService.generateToken(request.userId(), role, request.department());
        return Map.of("token", token);
    }

    public record DevTokenRequest(String userId, String role, String department) {
    }
}
