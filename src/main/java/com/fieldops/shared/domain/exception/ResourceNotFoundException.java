package com.fieldops.shared.domain.exception;

/**
 * Thrown when a requested resource does not exist.
 *
 * <p>Mapped to HTTP <strong>404 Not Found</strong> by the global handler.</p>
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String code, String message) {
        super(code, message);
    }

    /**
     * Convenience factory for the common pattern
     * {@code "Entity with id X not found"}.
     */
    public static ResourceNotFoundException of(String entity, Object id) {
        String code = entity.toUpperCase() + "_NOT_FOUND";
        String message = entity + " with id " + id + " not found";
        return new ResourceNotFoundException(code, message);
    }
}
