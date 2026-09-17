package com.fieldops.auth.infrastructure.security;

import com.fieldops.user.domain.model.User;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the authenticated identity created by {@link JwtAuthenticationFilter}.
 *
 * <p>Services that implement user-scoped resources must use this resolver
 * instead of accepting an owner or technician id from the request payload.</p>
 */
@Component
public class AuthenticatedUserResolver {

    public User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)
                || user.getId() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user is required");
        }
        return user;
    }

    public UUID requireUserId(Authentication authentication) {
        return requireUser(authentication).getId();
    }
}
