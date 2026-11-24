package com.fieldops.inspection.presentation.mapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import com.fieldops.inspection.application.InspectionMobileChecklistItem;
import com.fieldops.inspection.application.InspectionMobileDetail;
import com.fieldops.inspection.application.InspectionMobileAnswer;
import com.fieldops.inspection.application.InspectionMobileReview;
import com.fieldops.inspection.application.InspectionMobileSummary;
import com.fieldops.inspection.presentation.dto.MobileClientResponse;
import com.fieldops.inspection.presentation.dto.MobileEquipmentResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionAnswerResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionItemResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionReviewResponse;
import com.fieldops.inspection.presentation.dto.MobileInspectionSummaryResponse;
import com.fieldops.inspection.presentation.dto.MobileSiteResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InspectionMobileMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MobileInspectionSummaryResponse toSummary(InspectionMobileSummary summary) {
        return new MobileInspectionSummaryResponse(
                summary.id(), summary.title(), summary.status(), summary.priority(),
                summary.scheduledFor(), summary.updatedAt(), summary.version(),
                summary.clientId(), summary.clientName(), summary.siteId(), summary.siteName(),
                summary.equipmentId(), summary.equipmentName()
        );
    }

    public MobileInspectionResponse toResponse(InspectionMobileDetail detail) {
        return new MobileInspectionResponse(
                detail.id(),
                detail.title(),
                detail.status(),
                detail.priority(),
                detail.scheduledFor(),
                detail.updatedAt(),
                detail.instructions(),
                detail.version(),
                new MobileClientResponse(detail.clientId(), detail.clientName()),
                new MobileSiteResponse(
                        detail.siteId(), detail.siteName(), detail.addressLine(),
                        detail.latitude(), detail.longitude()
                ),
                detail.equipmentId() == null ? null : new MobileEquipmentResponse(
                        detail.equipmentId(), detail.equipmentName(), detail.equipmentQrCode(),
                        detail.equipmentAssetNumber(), detail.equipmentSerialNumber()
                ),
                detail.items().stream().map(this::toItem).toList(),
                detail.responses().stream().map(this::toAnswer).toList(),
                toReview(detail.review())
        );
    }

    private MobileInspectionAnswerResponse toAnswer(InspectionMobileAnswer answer) {
        return new MobileInspectionAnswerResponse(
                answer.itemId(), parseNullable(answer.valueJson()), answer.observation(), answer.evidenceIds()
        );
    }

    private MobileInspectionItemResponse toItem(InspectionMobileChecklistItem item) {
        return new MobileInspectionItemResponse(
                item.id(), item.sectionTitle(), item.sectionDescription(), item.sectionOrder(),
                item.itemCode(), item.itemTitle(), item.itemDescription(), item.responseType(),
                item.required(), parse(item.rulesJson()), parseNullable(item.optionsJson()), item.itemOrder()
        );
    }

    private MobileInspectionReviewResponse toReview(InspectionMobileReview review) {
        return review == null ? null : new MobileInspectionReviewResponse(
                review.decision(), review.reason(), review.itemsToCorrect()
        );
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return NullNode.instance;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Stored inspection checklist JSON is invalid", ex);
        }
    }

    private JsonNode parseNullable(String json) {
        return json == null ? null : parse(json);
    }
}
