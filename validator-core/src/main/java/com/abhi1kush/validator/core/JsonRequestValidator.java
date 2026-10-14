package com.abhi1kush.validator.core;

import com.abhi1kush.validator.mapping.ConstraintDefaults;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a raw JSON request body into a value object with Jackson and validates it with
 * Jakarta Bean Validation, reducing all problems to a single, prioritised, generic error.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Blank body or JSON {@code null} → {@link GenericErrorCode#EMPTY_BODY}.</li>
 *   <li>Jackson errors → {@link GenericErrorCode#MALFORMED_JSON}, {@link GenericErrorCode#UNKNOWN_FIELD}
 *       or {@link GenericErrorCode#TYPE_MISMATCH}.</li>
 *   <li>Each constraint violation is resolved to a generic code: if the constraint {@code message}
 *       is a VO code (e.g. {@code USR_001}) it is passed to the {@link ErrorCodeMapper}; otherwise,
 *       or when unmapped, the default from {@link ConstraintDefaults} is used (length constraints
 *       are split into max-exceeded / min-not-met by comparing the actual length with the bounds).</li>
 *   <li>The generic code with the highest {@link GenericErrorCode#priority()} wins; all fields
 *       violating that code are listed, sorted and de-duplicated, as {@code {a, b}}.</li>
 * </ol>
 *
 * <p>This class is immutable and thread-safe (Jackson {@link ObjectReader} and Jakarta
 * {@link Validator} are both thread-safe).
 */
public class JsonRequestValidator {

    /** A constraint message matching this pattern is treated as a VO-specific error code. */
    public static final Pattern VO_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");

    private static final String ROOT = "$";

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ErrorCodeMapper errorCodeMapper;
    private final boolean failOnUnknownProperties;

    /**
     * @param objectMapper            the (Spring-managed) Jackson mapper; it is not modified
     * @param validator               a thread-safe Jakarta validator
     * @param errorCodeMapper         maps VO codes to generic codes
     * @param failOnUnknownProperties whether unknown JSON properties are rejected
     */
    public JsonRequestValidator(ObjectMapper objectMapper, Validator validator,
                                ErrorCodeMapper errorCodeMapper, boolean failOnUnknownProperties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.errorCodeMapper = Objects.requireNonNull(errorCodeMapper, "errorCodeMapper");
        this.failOnUnknownProperties = failOnUnknownProperties;
    }

    /**
     * Parses and validates a JSON request body.
     *
     * @param requestBody raw JSON, may be {@code null}
     * @param voType      the value object class to bind to
     * @param <T>         the value object type
     * @return a success result holding the VO, or a failure with generic code, description and HTTP status
     */
    public <T> ValidationResult<T> validate(String requestBody, Class<T> voType) {
        Objects.requireNonNull(voType, "voType");
        if (requestBody == null || requestBody.isBlank()) {
            return ValidationResult.failure(GenericErrorCode.EMPTY_BODY, "");
        }
        T vo;
        try {
            vo = reader(voType).readValue(requestBody);
        } catch (JsonProcessingException e) {
            return fromJacksonException(e);
        }
        if (vo == null) {
            return ValidationResult.failure(GenericErrorCode.EMPTY_BODY, "");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(vo);
        if (violations.isEmpty()) {
            return ValidationResult.success(vo);
        }
        return fromViolations(violations);
    }

    private <T> ObjectReader reader(Class<T> voType) {
        ObjectReader reader = objectMapper.readerFor(voType);
        return failOnUnknownProperties
                ? reader.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                : reader.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static <T> ValidationResult<T> fromJacksonException(JacksonException e) {
        if (e instanceof PropertyBindingException pbe) {
            // Unrecognized / ignored property: the path already ends with the offending name
            return ValidationResult.failure(GenericErrorCode.UNKNOWN_FIELD, braces(List.of(path(pbe))));
        }
        if (e instanceof MismatchedInputException mie) {
            return ValidationResult.failure(GenericErrorCode.TYPE_MISMATCH, braces(List.of(path(mie))));
        }
        if (e instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            // e.g. a value that cannot be instantiated or converted (enum, date ...)
            return ValidationResult.failure(GenericErrorCode.TYPE_MISMATCH, braces(List.of(path(jme))));
        }
        return ValidationResult.failure(GenericErrorCode.MALFORMED_JSON, "");
    }

    private static String path(JsonMappingException e) {
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? ROOT : sb.toString();
    }

    private <T> ValidationResult<T> fromViolations(Set<ConstraintViolation<T>> violations) {
        List<Resolved> resolved = violations.stream()
                .map(v -> new Resolved(resolve(v), pathOf(v)))
                .toList();
        GenericErrorCode winner = resolved.stream()
                .map(Resolved::code)
                .max(Comparator.comparingInt(GenericErrorCode::priority))
                .orElseThrow();
        Set<String> fields = resolved.stream()
                .filter(r -> r.code() == winner)
                .map(Resolved::path)
                .collect(Collectors.toCollection(() -> new TreeSet<>(PropertyPathComparator.INSTANCE)));
        return ValidationResult.failure(winner, braces(fields));
    }

    /**
     * Resolves one violation to its generic code.
     *
     * @param violation the violation
     * @return generic code
     */
    GenericErrorCode resolve(ConstraintViolation<?> violation) {
        String template = violation.getMessageTemplate();
        if (template != null && VO_CODE_PATTERN.matcher(template).matches()) {
            var mapped = errorCodeMapper.map(template);
            if (mapped.isPresent()) {
                return mapped.get();
            }
        }
        return ConstraintDefaults.forViolation(violation);
    }

    private static String pathOf(ConstraintViolation<?> violation) {
        String p = violation.getPropertyPath().toString();
        return p.isEmpty() ? ROOT : p;
    }

    private static String braces(Iterable<String> fields) {
        return "{" + String.join(", ", fields) + "}";
    }

    private record Resolved(GenericErrorCode code, String path) {
    }
}
