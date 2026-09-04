package com.fieldops.user.domain.model;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserJpaMappingTest {

    @Test
    void userHasAuditingAndOptimisticLockingAnnotations() throws Exception {
        assertThat(User.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
        assertThat(User.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
        assertThat(User.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }
}
