package com.fieldops.shared.presentation.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Central pagination constants and utility methods.
 *
 * <p>Every list endpoint should delegate to {@link #of(int, int, Sort)} to
 * build a {@link Pageable} that respects the maximum page size constraint.</p>
 */
public final class PaginationDefaults {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PaginationDefaults() {
        // utility class
    }

    /**
     * Creates a {@link Pageable} clamping {@code size} to {@link #MAX_SIZE}.
     *
     * @param page zero-based page index
     * @param size requested page size (will be clamped, never rejected)
     * @param sort sort specification
     * @return a safe {@link Pageable}
     */
    public static Pageable of(int page, int size, Sort sort) {
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, sort);
    }

    /**
     * Overload without explicit sort — defaults to {@link Sort#unsorted()}.
     */
    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }
}
