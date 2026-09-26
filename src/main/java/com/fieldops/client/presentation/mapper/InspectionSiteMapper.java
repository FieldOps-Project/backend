package com.fieldops.client.presentation.mapper;

import com.fieldops.client.domain.model.InspectionSite;
import com.fieldops.client.presentation.dto.InspectionSiteResponse;
import org.springframework.stereotype.Component;

@Component
public class InspectionSiteMapper {

    public InspectionSiteResponse toResponse(InspectionSite site) {
        return new InspectionSiteResponse(
                site.getId(),
                site.getClient().getId(),
                site.getName(),
                site.getDescription(),
                site.getAddressLine(),
                site.getCity(),
                site.getState(),
                site.getPostalCode(),
                site.getLatitude(),
                site.getLongitude(),
                site.getContactName(),
                site.getContactPhone(),
                site.getStatus(),
                site.getCreatedAt(),
                site.getUpdatedAt(),
                site.getVersion()
        );
    }
}
