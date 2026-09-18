package com.fieldops.client.domain.model;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import static org.assertj.core.api.Assertions.assertThat;

class ClientJpaMappingTest {

    @Test
    void clientHasAuditingAndOptimisticLockingAnnotations() throws Exception {
        assertThat(Client.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
        assertThat(Client.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
        assertThat(Client.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }
}
