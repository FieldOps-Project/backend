package com.fieldops.inspection.presentation.mapper;

import com.fieldops.inspection.domain.model.InspectionReview;
import com.fieldops.inspection.presentation.dto.InspectionReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionReviewMapper {

    public InspectionReviewResponse toResponse(InspectionReview review) {
        return new InspectionReviewResponse(
                review.getId(), review.getInspectionId(), review.getReviewCycle(), review.getReviewerId(),
                review.getDecision(), review.getReason(), review.getComments(), review.getItemsToCorrect(),
                review.getReviewedAt(), review.getCreatedAt()
        );
    }
}
