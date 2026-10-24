package com.fieldops.inspection.application;

import com.fieldops.audit.infrastructure.persistence.AuditEvent;
import com.fieldops.audit.infrastructure.persistence.AuditEventRepository;
import com.fieldops.auth.infrastructure.persistence.RefreshTokenRepository;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.equipment.infrastructure.persistence.EquipmentRepository;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateItemRepository;
import com.fieldops.inspection.infrastructure.persistence.TemplateSectionRepository;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "JWT_SECRET=local-test-secret-012345678901234567890123456789",
        "app.security.jwt.secret=local-test-secret-012345678901234567890123456789"
})
@AutoConfigureMockMvc
class InspectionTemplateAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionTemplateRepository inspectionTemplateRepository;

    @MockitoBean
    private InspectionTemplateVersionRepository inspectionTemplateVersionRepository;

    @MockitoBean
    private TemplateSectionRepository templateSectionRepository;

    @MockitoBean
    private TemplateItemRepository templateItemRepository;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private InspectionSiteRepository inspectionSiteRepository;

    @MockitoBean
    private EquipmentRepository equipmentRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UUID templateId;
    private InspectionTemplate template;
    private InspectionTemplateVersion draft;
    private TemplateSection section;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        template = withId(InspectionTemplate.create("Extintores", null, "Incendio", UUID.randomUUID()), templateId);
        draft = withId(InspectionTemplateVersion.createDraft(template, "Extintores", null), UUID.randomUUID());
        section = withId(TemplateSection.create(draft, "Condicoes", null, 1), UUID.randomUUID());
        draft.addSection(section);

        when(inspectionTemplateRepository.existsById(templateId)).thenReturn(true);
        when(inspectionTemplateVersionRepository.findByTemplateIdAndVersionNumber(templateId, 0))
                .thenReturn(Optional.of(draft));
        when(templateSectionRepository.findById(section.getId())).thenReturn(Optional.of(section));
        when(templateItemRepository.saveAndFlush(any(TemplateItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void technicianGetsForbiddenFromTemplateBuilder() throws Exception {
        mockMvc.perform(get("/api/v1/inspection-templates/{id}", templateId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void clientViewerCannotCreateSections() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections", templateId)
                        .with(user("viewer").roles("CLIENT_VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nova secao\"}"))
                .andExpect(status().isForbidden());

        verify(templateSectionRepository, never()).saveAndFlush(any(TemplateSection.class));
    }

    @Test
    void supervisorReadsTemplateWithOrderedDraftStructure() throws Exception {
        mockMvc.perform(get("/api/v1/inspection-templates/{id}", templateId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.draftVersionId").value(draft.getId().toString()))
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].title").value("Condicoes"));
    }

    @Test
    void adminCreatesItemWithConfiguredFailureRules() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "A protecao do equipamento esta integra?",
                                  "responseType": "CONFORMITY",
                                  "required": true,
                                  "observationRequiredOnFailure": true,
                                  "evidenceRequiredOnFailure": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sectionId").value(section.getId().toString()))
                .andExpect(jsonPath("$.responseType").value("CONFORMITY"))
                .andExpect(jsonPath("$.evidenceRequiredOnFailure").value(true))
                .andExpect(jsonPath("$.displayOrder").value(1))
                .andExpect(jsonPath("$.options", hasSize(0)));
    }

    @Test
    void itemWithoutResponseTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sem tipo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("responseType"));
    }

    @Test
    void unsupportedResponseTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Assinatura\",\"responseType\":\"SIGNATURE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void singleChoiceWithOneOptionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Estado\",\"responseType\":\"SINGLE_CHOICE\",\"options\":[\"Bom\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ITEM_OPTIONS_REQUIRED"));
    }

    @Test
    void optionsForUnsupportedTypeAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Lacre\",\"responseType\":\"BOOLEAN\",\"options\":[\"Sim\",\"Nao\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ITEM_OPTIONS_NOT_ALLOWED"));
    }

    @Test
    void evidenceOnFailureForDateItemIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/sections/{sectionId}/items", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Validade\",\"responseType\":\"DATE\",\"evidenceRequiredOnFailure\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ITEM_FAILURE_RULES_NOT_ALLOWED"));
    }

    @Test
    void editingSectionOfPublishedVersionReturnsConflict() throws Exception {
        InspectionTemplateVersion published = InspectionTemplateVersion.createPublished(
                template, 1, "Extintores", null, UUID.randomUUID()
        );
        TemplateSection publishedSection = withId(TemplateSection.create(published, "Publicada", null, 1), UUID.randomUUID());
        when(templateSectionRepository.findById(publishedSection.getId())).thenReturn(Optional.of(publishedSection));

        mockMvc.perform(put("/api/v1/inspection-templates/{id}/sections/{sectionId}", templateId, publishedSection.getId())
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Alterada\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_PUBLISHED"));
    }

    @Test
    void reorderWithIncompleteListReturnsUnprocessableEntity() throws Exception {
        mockMvc.perform(put("/api/v1/inspection-templates/{id}/sections/order", templateId)
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_REORDER_MISMATCH"));
    }

    @Test
    void deletingDraftSectionReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/inspection-templates/{id}/sections/{sectionId}", templateId, section.getId())
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isNoContent());
    }

    // ── Publication ─────────────────────────────────────────────────────

    @Test
    void technicianCannotPublishTemplate() throws Exception {
        mockMvc.perform(post("/api/v1/inspection-templates/{id}/publish", templateId)
                        .with(user("technician").roles("TECHNICIAN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        verify(inspectionTemplateVersionRepository, never()).saveAndFlush(any(InspectionTemplateVersion.class));
    }

    @Test
    void supervisorPublishesValidDraftAsVersionOne() throws Exception {
        section.addItem(TemplateItem.create(
                section, "EXT-01", "Lacre integro?", null, ResponseType.CONFORMITY, true, true, true, null, 1
        ));
        when(inspectionTemplateRepository.findByIdForUpdate(templateId)).thenReturn(Optional.of(template));
        when(inspectionTemplateVersionRepository.findLatestVersionNumber(templateId)).thenReturn(0);
        when(inspectionTemplateVersionRepository.saveAndFlush(any(InspectionTemplateVersion.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), UUID.randomUUID()));
        when(inspectionTemplateRepository.saveAndFlush(any(InspectionTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/v1/inspection-templates/{id}/publish", templateId)
                        .with(authentication(authenticatedAs(UserRole.SUPERVISOR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateId").value(templateId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.activeForNewInspections").value(true))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        verify(auditEventRepository).save(any(AuditEvent.class));
    }

    @Test
    void publishingInvalidDraftListsEveryPendingIssue() throws Exception {
        TemplateSection second = withId(TemplateSection.create(draft, "Escolha", null, 2), UUID.randomUUID());
        second.addItem(TemplateItem.create(
                second, null, "Estado", null, ResponseType.SINGLE_CHOICE, true, false, false, null, 1
        ));
        draft.addSection(second);
        when(inspectionTemplateRepository.findByIdForUpdate(templateId)).thenReturn(Optional.of(template));

        mockMvc.perform(post("/api/v1/inspection-templates/{id}/publish", templateId)
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_PUBLISH_INVALID"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sections[0].items"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("sections[1].items[0].options"));

        verify(inspectionTemplateVersionRepository, never()).saveAndFlush(any(InspectionTemplateVersion.class));
    }

    @Test
    void draftIdIsNotExposedAsPublishedVersion() throws Exception {
        when(inspectionTemplateVersionRepository.findById(draft.getId())).thenReturn(Optional.of(draft));

        mockMvc.perform(get("/api/v1/inspection-template-versions/{versionId}", draft.getId())
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_NOT_FOUND"));
    }

    @Test
    void supervisorReadsPublishedVersionStructure() throws Exception {
        InspectionTemplateVersion published = withId(InspectionTemplateVersion.publishCopyOf(
                draft, 1, UUID.randomUUID()
        ), UUID.randomUUID());
        when(inspectionTemplateVersionRepository.findById(published.getId())).thenReturn(Optional.of(published));

        mockMvc.perform(get("/api/v1/inspection-template-versions/{versionId}", published.getId())
                        .with(user("supervisor").roles("SUPERVISOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.sections", hasSize(1)))
                .andExpect(jsonPath("$.sections[0].title").value("Condicoes"));
    }

    @Test
    void activatingNeverPublishedTemplateIsRejected() throws Exception {
        when(inspectionTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        mockMvc.perform(patch("/api/v1/inspection-templates/{id}/status", templateId)
                        .with(user("supervisor").roles("SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_PUBLISHED"));
    }

    private static UsernamePasswordAuthenticationToken authenticatedAs(UserRole role) {
        User actor = withId(User.create(
                "Supervisor", "supervisor@example.com", "$2a$10$secretHash", role, UserStatus.ACTIVE, null
        ), UUID.randomUUID());
        return UsernamePasswordAuthenticationToken.authenticated(
                actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
