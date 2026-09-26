package com.fieldops.user.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionVersionTest {

    @Test
    void statusAndPasswordChangesInvalidatePreviouslyIssuedSessions() {
        User user = User.create(
                "Maria Silva",
                "maria@example.com",
                "hashed-password",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );

        assertThat(user.getSessionVersion()).isZero();

        user.updateStatus(UserStatus.BLOCKED);
        assertThat(user.getSessionVersion()).isOne();

        user.updateStatus(UserStatus.ACTIVE);
        user.changePassword("new-hash");

        assertThat(user.getSessionVersion()).isEqualTo(3);
    }
}
