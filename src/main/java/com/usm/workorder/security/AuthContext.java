package com.usm.workorder.security;

/**
 * Parsed, validated JWT claims for the current request. Controllers and
 * services depend on THIS object, never on raw JWT fields - so when Group 5's
 * real claim names arrive, only JwtAuthFilter/JwtTokenService change
 * (guide §13, row 1).
 */
public class AuthContext {

    private final String userId;
    private final Role role;
    private final String departmentOrServiceUnit;

    public AuthContext(String userId, Role role, String departmentOrServiceUnit) {
        this.userId = userId;
        this.role = role;
        this.departmentOrServiceUnit = departmentOrServiceUnit;
    }

    public String getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public String getDepartmentOrServiceUnit() {
        return departmentOrServiceUnit;
    }

    public boolean isServiceCall() {
        return role == Role.SERVICE;
    }

    @Override
    public String toString() {
        return "AuthContext{userId='" + userId + "', role=" + role + ", dept='" + departmentOrServiceUnit + "'}";
    }
}
