package com.fieldops.sync.infrastructure.persistence;

import com.fieldops.client.domain.model.Client;
import com.fieldops.client.domain.model.ClientStatus;
import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.domain.model.InspectionSiteStatus;
import com.fieldops.client.infrastructure.persistence.ClientRepository;
import com.fieldops.client.infrastructure.persistence.InspectionSiteRepository;
import com.fieldops.inspection.application.InspectionResponseService;
import com.fieldops.inspection.domain.model.Inspection;
import com.fieldops.inspection.domain.model.InspectionItemSnapshot;
import com.fieldops.inspection.domain.model.InspectionPriority;
import com.fieldops.inspection.domain.model.InspectionResponse;
import com.fieldops.inspection.domain.model.InspectionResponseConformity;
import com.fieldops.inspection.domain.model.InspectionStatus;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.ResponseType;
import com.fieldops.inspection.domain.model.TemplateItem;
import com.fieldops.inspection.domain.model.TemplateSection;
import com.fieldops.inspection.infrastructure.persistence.InspectionRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionResponseRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.inspection.presentation.dto.SaveInspectionResponseRequest;
import com.fieldops.shared.infrastructure.filter.RequestIdFilter;
import com.fieldops.sync.application.SyncConflictResolver;
import com.fieldops.sync.application.SyncPushService;
import com.fieldops.sync.domain.model.SyncChange;
import com.fieldops.sync.domain.model.SyncOperationResultStatus;
import com.fieldops.sync.presentation.dto.SyncOperationRequest;
import com.fieldops.sync.presentation.dto.SyncOperationResult;
import com.fieldops.sync.presentation.dto.SyncPushRequest;
import com.fieldops.sync.presentation.dto.SyncPushResponse;
import com.fieldops.user.domain.model.User;
import com.fieldops.user.domain.model.UserRole;
import com.fieldops.user.domain.model.UserStatus;
import com.fieldops.user.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "JWT_SECRET=local-test-secret-012345678901234567890123456789",
        "app.security.jwt.secret=local-test-secret-012345678901234567890123456789"
})
@Testcontainers
class SyncConflictIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private SyncPushService syncPushService;

    @Autowired
    private SyncChangeReader syncChangeReader;

    @Autowired
    private InspectionResponseService responseService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private InspectionSiteRepository siteRepository;

    @Autowired
    private InspectionTemplateRepository templateRepository;

    @Autowired
    private InspectionTemplateVersionRepository versionRepository;

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private InspectionResponseRepository responseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User technician;
    private Inspection inspection;
    private InspectionItemSnapshot item;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        technician = userRepository.saveAndFlush(
                User.create("Tecnico " + suffix, "tech-" + suffix + "@fieldops.local", "hash", UserRole.TECHNICIAN, UserStatus.ACTIVE, null)
        );
        User supervisor = userRepository.saveAndFlush(
                User.create("Supervisor " + suffix, "sup-" + suffix + "@fieldops.local", "hash", UserRole.SUPERVISOR, UserStatus.ACTIVE, null)
        );
        Client client = clientRepository.saveAndFlush(
                Client.create("Cliente " + suffix, null, null, null, null, ClientStatus.ACTIVE)
        );
        InspectionSite site = siteRepository.saveAndFlush(
                InspectionSite.create(client, "Site " + suffix, null, "Rua 1", "SP", "SP", null, null, null, null, null, InspectionSiteStatus.ACTIVE)
        );

        InspectionTemplate template = templateRepository.saveAndFlush(
                InspectionTemplate.create("Template " + suffix, "Desc", "Gerador", supervisor.getId())
        );
        InspectionTemplateVersion version = InspectionTemplateVersion.createPublished(
                template, 1, "Template " + suffix, null, supervisor.getId()
        );
        TemplateSection section = TemplateSection.create(version, "Geral", null, 1);
        section.addItem(TemplateItem.create(
                section, "IT-01", "Estado geral", null, ResponseType.CONFORMITY, true, false, false, null, 1
        ));
        version.addSection(section);
        version = versionRepository.saveAndFlush(version);

        inspection = Inspection.create(
                version, client.getId(), site.getId(), null, technician.getId(), supervisor.getId(),
                supervisor.getId(), "Inspecao " + suffix, null, InspectionPriority.HIGH,
                Instant.now().plusSeconds(3600)
        );
        inspection.start(Instant.now(), Instant.now(), null);
        inspection = inspectionRepository.saveAndFlush(inspection);
        item = inspection.getItemSnapshots().getFirst();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        technician,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))
                )
        );
        MDC.put(RequestIdFilter.MDC_KEY, UUID.randomUUID().toString());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void detectsVersionConflictBetweenPullAndPushWithoutOverwritingServerData() {
        UUID responseId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-24T10:00:00Z");

        // 1. Initial response created (version 0) and pulled by device
        responseService.upsert(inspection.getId(), responseId, new SaveInspectionResponseRequest(
                responseId, item.getId(), null, null, null, null, null,
                InspectionResponseConformity.CONFORMING, "Inicial", t0, 0
        ));
        List<SyncChange> pulled = syncChangeReader.findChanges(technician.getId(), null, 50);
        assertThat(pulled).anySatisfy(change -> {
            assertThat(change.entityType()).isEqualTo("INSPECTION_RESPONSE");
            assertThat(change.entityId()).isEqualTo(responseId);
            assertThat(change.version()).isEqualTo(0);
        });

        // 2. Server updates the response (version becomes 1) before offline device pushes
        responseService.upsert(inspection.getId(), responseId, new SaveInspectionResponseRequest(
                responseId, item.getId(), null, null, null, null, null,
                InspectionResponseConformity.NON_CONFORMING, "Atualizado no servidor", t0.plusSeconds(60), 0
        ));

        // 3. Device pushes stale update with baseVersion = 0
        UUID operationId = UUID.randomUUID();
        var payload = objectMapper.createObjectNode()
                .put("inspectionId", inspection.getId().toString())
                .put("inspectionItemId", item.getId().toString())
                .put("conformity", "CONFORMING")
                .put("observation", "Alteracao offline atrasada")
                .put("answeredAtDevice", t0.plusSeconds(30).toString());
        SyncOperationRequest staleOp = new SyncOperationRequest(
                operationId, "INSPECTION_RESPONSE", responseId, "UPSERT", 0, List.of(), payload
        );

        SyncPushResponse pushResponse = syncPushService.push(new SyncPushRequest(UUID.randomUUID(), null, List.of(staleOp)));
        assertThat(pushResponse.results()).hasSize(1);
        SyncOperationResult result = pushResponse.results().getFirst();
        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(result.entityVersion()).isEqualTo(1);
        assertThat(result.errorCode()).isEqualTo(SyncConflictResolver.CODE_VERSION_CONFLICT);
        assertThat(result.serverState()).isNotNull();
        assertThat(result.serverState().get("conformity").asString()).isEqualTo("NON_CONFORMING");
        assertThat(result.serverState().get("observation").asString()).isEqualTo("Atualizado no servidor");

        // 4. Server data was not overwritten
        InspectionResponse persisted = responseRepository.findById(responseId).orElseThrow();
        assertThat(persisted.getVersion()).isEqualTo(1);
        assertThat(persisted.getObservation()).isEqualTo("Atualizado no servidor");

        // 5. Retrying the same operationId still preserves CONFLICT and serverState
        SyncPushResponse retryResponse = syncPushService.push(new SyncPushRequest(UUID.randomUUID(), null, List.of(staleOp)));
        SyncOperationResult retryResult = retryResponse.results().getFirst();
        assertThat(retryResult.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(retryResult.entityVersion()).isEqualTo(1);
        assertThat(retryResult.serverState().get("observation").asString()).isEqualTo("Atualizado no servidor");
    }

    @Test
    void rejectsUpdateOnApprovedInspectionAsExplicitConflict() {
        ReflectionTestUtils.setField(inspection, "status", InspectionStatus.APPROVED);
        ReflectionTestUtils.setField(inspection, "approvedAt", Instant.now());
        inspectionRepository.saveAndFlush(inspection);

        UUID operationId = UUID.randomUUID();
        var payload = objectMapper.createObjectNode()
                .put("inspectionId", inspection.getId().toString())
                .put("inspectionItemId", item.getId().toString())
                .put("conformity", "NON_CONFORMING")
                .put("observation", "Tentativa pos-aprovacao");
        SyncOperationRequest op = new SyncOperationRequest(
                operationId, "INSPECTION_RESPONSE", UUID.randomUUID(), "UPSERT", 0, List.of(), payload
        );

        SyncPushResponse response = syncPushService.push(new SyncPushRequest(UUID.randomUUID(), null, List.of(op)));
        SyncOperationResult result = response.results().getFirst();
        assertThat(result.status()).isEqualTo(SyncOperationResultStatus.CONFLICT);
        assertThat(result.errorCode()).isEqualTo(SyncConflictResolver.CODE_APPROVED_CONFLICT);
        assertThat(result.serverState()).isNotNull();
        assertThat(result.serverState().get("status").asString()).isEqualTo("APPROVED");
    }
}
