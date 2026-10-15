package com.abhi1kush.validator.mapping;

import com.abhi1kush.validator.core.GenericErrorCode;

import java.util.Optional;

/**
 * Maps a VO-specific error code (e.g. {@code USR_001}) to a generic service error code.
 *
 * <p>By default a {@link PropertiesErrorCodeMapper} built from the configured error mapping is used. Pass your own
 * via {@code JsonRequestValidator.builder().errorCodeMapper(..)}, or, with {@code @EnableJsonRequestValidator},
 * declare a single bean of this type; implementations must be thread-safe.
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
