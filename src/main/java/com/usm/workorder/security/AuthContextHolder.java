package com.usm.workorder.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContextHolder {

    private AuthContextHolder() {
    }

    public static AuthContext require() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (!(principal instanceof AuthContext authContext)) {
            throw new IllegalStateException("No authenticated AuthContext on the security context - "
                    + "is this endpoint missing from SecurityConfig's permitted list, or did the JWT filter not run?");
        }
        return authContext;
    }
}
