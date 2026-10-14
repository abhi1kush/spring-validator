package com.abhi1kush.validator.mapping;

import com.abhi1kush.validator.core.GenericErrorCode;

import java.util.Optional;

/**
 * Maps a VO-specific error code (e.g. {@code USR_001}) to a generic service error code.
 *
 * <p>The auto-configuration registers a {@link PropertiesErrorCodeMapper} backed by
 * {@code validator.error-mapping.*}. Declare your own bean of this type to replace it
 * (e.g. to read mappings from a database); implementations must be thread-safe.
 */
@FunctionalInterface
public interface ErrorCodeMapper {

    /**
     * @param voErrorCode the code declared on the VO constraint
     * @return the generic code, or empty when this mapper does not know the code
     *         (the validator then falls back to the constraint-type default)
     */
    Optional<GenericErrorCode> map(String voErrorCode);
}
