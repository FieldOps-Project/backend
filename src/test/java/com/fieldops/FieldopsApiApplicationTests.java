package com.fieldops;

import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionItemSnapshotRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionReviewRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionEvidenceRepository;
import com.fieldops.inspection.infrastructure.persistence.NonConformityRepository;
import com.fieldops.sync.infrastructure.persistence.SyncProcessedOperationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
		"JWT_SECRET=local-test-secret-012345678901234567890123456789",
		"app.security.jwt.secret=local-test-secret-012345678901234567890123456789"
})
class FieldopsApiApplicationTests {

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private RefreshTokenRepository refreshTokenRepository;

	@MockitoBean
	private AuditEventRepository auditEventRepository;

	@MockitoBean
	private ClientRepository clientRepository;

	@MockitoBean
	private InspectionSiteRepository inspectionSiteRepository;

	@MockitoBean
	private EquipmentRepository equipmentRepository;

	@MockitoBean
	private InspectionTemplateRepository inspectionTemplateRepository;

	@MockitoBean
	private InspectionTemplateVersionRepository inspectionTemplateVersionRepository;

	@MockitoBean
	private TemplateSectionRepository templateSectionRepository;

	@MockitoBean
	private TemplateItemRepository templateItemRepository;

	@MockitoBean
	private InspectionRepository inspectionRepository;

	@MockitoBean
	private InspectionReviewRepository inspectionReviewRepository;

	@MockitoBean
	private InspectionItemSnapshotRepository inspectionItemSnapshotRepository;

	@MockitoBean
	private InspectionResponseRepository inspectionResponseRepository;

	@MockitoBean
	private InspectionEvidenceRepository inspectionEvidenceRepository;

	@MockitoBean
	private NonConformityRepository nonConformityRepository;

	@MockitoBean
	private SyncProcessedOperationRepository syncProcessedOperationRepository;

	@MockitoBean
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void contextLoads() {
	}

}
