package com.fieldops.inspection.application;

import com.fieldops.audit.application.AuditEventService;
import com.fieldops.auth.infrastructure.security.AuthenticatedUserResolver;
import com.fieldops.auth.infrastructure.security.AuthorizationPolicies;
import com.fieldops.inspection.domain.model.InspectionTemplate;
import com.fieldops.inspection.domain.model.InspectionTemplateVersion;
import com.fieldops.inspection.domain.model.TemplateStatus;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateRepository;
import com.fieldops.inspection.infrastructure.persistence.InspectionTemplateVersionRepository;
import com.fieldops.shared.domain.exception.BusinessRuleException;
import com.fieldops.shared.domain.exception.InvalidRequestException;
import com.fieldops.shared.domain.exception.ResourceNotFoundException;
import com.fieldops.shared.domain.exception.Violation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Publication and lifecycle of template versions (UC-05).
 *
 * <p>Publishing freezes a copy of the draft as the next numbered version
 * (ADR-001); the draft stays editable for the following version. The first
 * publication activates the template; a template retired on purpose stays
 * INACTIVE when new versions are published.</p>
 */
@Service
public class TemplateVersionService {

    static final String VERSION_ENTITY = "INSPECTION_TEMPLATE_VERSION";
    static final String TEMPLATE_ENTITY = "INSPECTION_TEMPLATE";

    private final InspectionTemplateRepository templateRepository;
    private final InspectionTemplateVersionRepository versionRepository;
    private final AuditEventService auditEventService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public TemplateVersionService(
            InspectionTemplateRepository templateRepository,
            InspectionTemplateVersionRepository versionRepository,
            AuditEventService auditEventService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.auditEventService = auditEventService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion publish(UUID templateId) {
        InspectionTemplate template = templateRepository.findByIdForUpdate(templateId)
                .orElseThrow(() -> templateNotFound(templateId));
        InspectionTemplateVersion draft = versionRepository
                .findByTemplateIdAndVersionNumber(templateId, InspectionTemplateService.DRAFT_VERSION_NUMBER)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_DRAFT_NOT_FOUND",
                        "Inspection template " + templateId + " has no draft version"
                ));

        List<Violation> violations = TemplatePublicationValidator.validate(draft);
        if (!violations.isEmpty()) {
            throw new BusinessRuleException(
                    "TEMPLATE_PUBLISH_INVALID",
                    "The draft has " + violations.size() + " pending issue(s) that block publication",
                    violations
            );
        }

        int versionNumber = versionRepository.findLatestVersionNumber(templateId) + 1;
        UUID publishedBy = authenticatedUserResolver.requireUserId(
                SecurityContextHolder.getContext().getAuthentication()
        );
        InspectionTemplateVersion published = versionRepository.saveAndFlush(
                InspectionTemplateVersion.publishCopyOf(draft, versionNumber, publishedBy)
        );

        template.advanceCurrentVersion(versionNumber);
        if (template.getStatus() == TemplateStatus.DRAFT) {
            template.updateStatus(TemplateStatus.ACTIVE);
        }
        templateRepository.saveAndFlush(template);

        auditEventService.recordEvent(
                "TEMPLATE_VERSION_PUBLISHED",
                VERSION_ENTITY,
                published.getId(),
                null,
                "{\"templateId\":\"" + templateId + "\",\"versionNumber\":" + versionNumber + "}"
        );
        return published;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public List<InspectionTemplateVersion> findVersions(UUID templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw templateNotFound(templateId);
        }
        return versionRepository.findByTemplateIdAndPublishedAtIsNotNullOrderByVersionNumberDesc(templateId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion findVersion(UUID versionId) {
        return requirePublished(versionId);
    }

    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplateVersion updateAvailability(UUID versionId, boolean activeForNewInspections) {
        InspectionTemplateVersion version = requirePublished(versionId);
        boolean before = version.isActiveForNewInspections();
        version.updateAvailability(activeForNewInspections);
        InspectionTemplateVersion saved = versionRepository.saveAndFlush(version);

        if (before != activeForNewInspections) {
            auditEventService.recordEvent(
                    "TEMPLATE_VERSION_AVAILABILITY_CHANGED",
                    VERSION_ENTITY,
                    versionId,
                    "{\"activeForNewInspections\":" + before + "}",
                    "{\"activeForNewInspections\":" + activeForNewInspections + "}"
            );
        }
        return saved;
    }

    /**
     * Activates or retires the whole template. Retiring hides every version
     * from new scheduling without affecting existing inspections (RN-022).
     */
    @Transactional
    @PreAuthorize(AuthorizationPolicies.TEMPLATES_MANAGE)
    public InspectionTemplate updateTemplateStatus(UUID templateId, TemplateStatus status) {
        if (status == TemplateStatus.DRAFT) {
            throw new InvalidRequestException(
                    "INVALID_TEMPLATE_STATUS",
                    "Template status can only be changed to ACTIVE or INACTIVE"
            );
        }
        InspectionTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> templateNotFound(templateId));
        if (template.getCurrentVersion() == null) {
            throw new BusinessRuleException(
                    "TEMPLATE_NOT_PUBLISHED",
                    "Publish a version before activating or retiring the template"
            );
        }

        TemplateStatus before = template.getStatus();
        template.updateStatus(status);
        InspectionTemplate saved = templateRepository.saveAndFlush(template);

        if (before != status) {
            auditEventService.recordEvent(
                    "TEMPLATE_STATUS_CHANGED",
                    TEMPLATE_ENTITY,
                    templateId,
                    "{\"status\":\"" + before + "\"}",
                    "{\"status\":\"" + status + "\"}"
            );
        }
        return saved;
    }

    /**
     * Scheduling guard for the inspection scheduling use case: only published,
     * available versions of active templates can back a new inspection.
     */
    @Transactional(readOnly = true)
    @PreAuthorize(AuthorizationPolicies.INSPECTIONS_SCHEDULE)
    public InspectionTemplateVersion findVersionForScheduling(UUID versionId) {
        InspectionTemplateVersion version = requirePublished(versionId);
        if (!version.isActiveForNewInspections() || version.getTemplate().getStatus() != TemplateStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "TEMPLATE_VERSION_UNAVAILABLE",
                    "This template version is not available for new inspections"
            );
        }
        return version;
    }

    private InspectionTemplateVersion requirePublished(UUID versionId) {
        return versionRepository.findById(versionId)
                .filter(version -> !version.isDraft())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "Published template version with id " + versionId + " not found"
                ));
    }

    private static ResourceNotFoundException templateNotFound(UUID templateId) {
        return new ResourceNotFoundException(
                "INSPECTION_TEMPLATE_NOT_FOUND",
                "Inspection template with id " + templateId + " not found"
        );
    }
}
