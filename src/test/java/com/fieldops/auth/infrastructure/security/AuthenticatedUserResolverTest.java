package com.fieldops.auth.infrastructure.security;

import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserResolverTest {

    private final AuthenticatedUserResolver resolver = new AuthenticatedUserResolver();

    @Test
    void resolvesIdentityFromAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        User user = User.create(
                "Maria Silva",
                "maria@example.com",
                "$2a$10$secretHash",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );
        setId(user, userId);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
        );

        assertThat(resolver.requireUserId(authentication)).isEqualTo(userId);
    }

    @Test
    void rejectsMissingOrNonUserPrincipal() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("client-supplied-id", null);

        assertThatThrownBy(() -> resolver.requireUserId(authentication))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Authenticated user is required");
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
