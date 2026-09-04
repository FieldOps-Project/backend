package com.fieldops.shared.presentation.dto;

import java.util.List;

/**
 * Generic envelope for paginated list responses.
 *
 * <p>Wraps the content together with pagination metadata so that every
 * list endpoint of the API returns a consistent structure.</p>
 *
 * @param content       items on the current page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total number of elements across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @param <T>           type of the listed resource
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /**
     * Convenience factory that converts a Spring {@code Page} into this envelope.
     */
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isFirst(),
                springPage.isLast()
        );
    }
}
