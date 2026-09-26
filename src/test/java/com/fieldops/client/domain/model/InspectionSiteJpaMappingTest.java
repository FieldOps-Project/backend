package com.fieldops.client.domain.model;

import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionSiteJpaMappingTest {

    @Test
    void inspectionSiteHasRequiredClientRelationshipAndAuditing() throws Exception {
        assertThat(InspectionSite.class.getDeclaredField("client").isAnnotationPresent(ManyToOne.class)).isTrue();
        JoinColumn joinColumn = InspectionSite.class.getDeclaredField("client").getAnnotation(JoinColumn.class);
        assertThat(joinColumn.name()).isEqualTo("client_id");
        assertThat(joinColumn.nullable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
        assertThat(InspectionSite.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class)).isTrue();
        assertThat(InspectionSite.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class)).isTrue();
        assertThat(InspectionSite.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
    }
}
