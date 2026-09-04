package com.fieldops.user.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializingUserDoesNotExposePasswordHash() throws Exception {
        User user = User.create(
                "Maria Silva",
                "maria@example.com",
                "$2a$10$secretHash",
                UserRole.TECHNICIAN,
                UserStatus.ACTIVE,
                null
        );

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("passwordHash");
        assertThat(json).doesNotContain("password_hash");
        assertThat(json).doesNotContain("$2a$10$secretHash");
    }
}
