package com.abhi1kush.validator.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of {@link JsonRequestValidator#validate(String, Class)}.
 *
 * <p>Exactly one of two shapes is possible:
 * <ul>
 *   <li><b>success</b>: {@code vo} is present, all error fields are empty;</li>
 *   <li><b>failure</b>: {@code vo} is empty, {@code errorCode}, {@code errorDescription}
 *       and {@code httpStatusCode} are present.</li>
 * </ul>
 *
 * @param vo               the parsed and validated value object (success only)
 * @param errorCode        the generic service error code, e.g. {@code E_MANDATORY_MISSING}
 * @param errorDescription human readable description, e.g. {@code Mandatory fields missing: {name, email}}
 * @param httpStatusCode   HTTP status suggested for the error, e.g. {@code 400}
 * @param <T>              the value object type
 */
public record ValidationResult<T>(Optional<T> vo,
                                  Optional<String> errorCode,
                                  Optional<String> errorDescription,
                                  Optional<Integer> httpStatusCode) {

    /** Canonical constructor; enforces the success/failure invariant. */
    public ValidationResult {
        Objects.requireNonNull(vo, "vo");
        Objects.requireNonNull(errorCode, "errorCode");
        Objects.requireNonNull(errorDescription, "errorDescription");
        Objects.requireNonNull(httpStatusCode, "httpStatusCode");
        boolean failure = errorCode.isPresent();
        if (failure == vo.isPresent()
                || failure != errorDescription.isPresent()
                || failure != httpStatusCode.isPresent()) {
            throw new IllegalArgumentException("Either vo or all error fields must be present");
        }
    }

    /**
     * Creates a successful result.
     *
     * @param vo  the validated value object, never {@code null}
     * @param <T> the value object type
     * @return a success result
     */
    public static <T> ValidationResult<T> success(T vo) {
        return new ValidationResult<>(Optional.of(vo), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Creates a failed result.
     *
     * @param errorCode        generic error code
     * @param errorDescription description
     * @param httpStatusCode   HTTP status
     * @param <T>              the value object type
     * @return a failure result
     */
    public static <T> ValidationResult<T> failure(String errorCode, String errorDescription, int httpStatusCode) {
        return new ValidationResult<>(Optional.empty(), Optional.of(errorCode),
                Optional.of(errorDescription), Optional.of(httpStatusCode));
    }

    /**
     * Creates a failed result from a generic code and the affected fields.
     *
     * @param code   generic error code
     * @param fields formatted field list, e.g. {@code {name, email}}
     * @param <T>    the value object type
     * @return a failure result
     */
    public static <T> ValidationResult<T> failure(GenericErrorCode code, String fields) {
        return failure(code.code(), code.describe(fields), code.httpStatus());
    }

    /** @return {@code true} when validation succeeded and {@link #vo()} is present */
    public boolean isValid() {
        return vo.isPresent();
    }
}
