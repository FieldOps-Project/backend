package com.fieldops.auth.infrastructure.security;

/**
 * Centralized method-security expressions for the FieldOps permission matrix.
 *
 * <p>Endpoint and application-service annotations use these constants so a
 * permission change cannot silently leave one layer with a different policy.</p>
 */
public final class AuthorizationPolicies {

    public static final String AUTHENTICATED = "isAuthenticated()";
    public static final String USERS_MANAGE = "hasRole('ADMIN')";
    public static final String USERS_READ = "hasAnyRole('ADMIN', 'SUPERVISOR')";
    public static final String CATALOG_MANAGE = "hasAnyRole('ADMIN', 'SUPERVISOR')";
    public static final String INSPECTIONS_SCHEDULE = "hasAnyRole('ADMIN', 'SUPERVISOR')";
    public static final String INSPECTIONS_EXECUTE = "hasRole('TECHNICIAN')";
    public static final String INSPECTIONS_REVIEW = "hasRole('SUPERVISOR')";
    public static final String AUDIT_READ = "hasAnyRole('ADMIN', 'SUPERVISOR')";

    private AuthorizationPolicies() {
    }
}
