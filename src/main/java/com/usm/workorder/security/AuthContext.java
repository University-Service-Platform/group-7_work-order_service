package com.usm.workorder.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parsed, validated JWT claims for the current request. Controllers and
 * services depend on THIS object, never on raw JWT fields - so when Group 5's
 * real claim names arrive, only JwtAuthFilter/JwtTokenService change
 * (guide §13, row 1).
 */
public class AuthContext {

    private final String userId;
    private final Role role;
    private final Set<Role> roles;
    private final String departmentOrServiceUnit;
    private final String universityId;
    private final String accountType;

    public AuthContext(String userId, Role role, String departmentOrServiceUnit) {
        this(userId, role != null ? Collections.singleton(role) : Collections.emptySet(),
                departmentOrServiceUnit, null, null);
    }

    public AuthContext(String userId, Set<Role> roles, String departmentOrServiceUnit) {
        this(userId, roles, departmentOrServiceUnit, null, null);
    }

    public AuthContext(String userId, Set<Role> roles, String departmentOrServiceUnit,
                       String universityId, String accountType) {
        this.userId = userId;
        this.roles = (roles != null && !roles.isEmpty())
                ? Collections.unmodifiableSet(new LinkedHashSet<>(roles))
                : Collections.emptySet();
        this.role = this.roles.isEmpty() ? null : this.roles.iterator().next();
        this.departmentOrServiceUnit = departmentOrServiceUnit;
        this.universityId = universityId;
        this.accountType = accountType;
    }

    public String getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean hasRole(Role checkRole) {
        return roles.contains(checkRole);
    }

    public boolean hasAnyRole(Collection<Role> checkRoles) {
        if (checkRoles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(checkRoles::contains);
    }

    public String getDepartmentOrServiceUnit() {
        return departmentOrServiceUnit;
    }

    public String getUniversityId() {
        return universityId;
    }

    public String getAccountType() {
        return accountType;
    }

    public boolean isServiceCall() {
        return hasRole(Role.SERVICE);
    }

    @Override
    public String toString() {
        return "AuthContext{" +
                "userId='" + userId + '\'' +
                ", roles=" + roles +
                ", dept='" + departmentOrServiceUnit + '\'' +
                ", universityId='" + universityId + '\'' +
                ", accountType='" + accountType + '\'' +
                '}';
    }
}
