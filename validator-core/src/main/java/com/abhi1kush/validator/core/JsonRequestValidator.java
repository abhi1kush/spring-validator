package com.abhi1kush.validator.core;

import com.abhi1kush.validator.mapping.ConstraintDefaults;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 *   <li>Paths use API names, segment by segment: {@link com.abhi1kush.validator.annotation.ErrorField},
 *       else the Jackson JSON name, else the Java name (see {@link FieldNameResolver}).</li>
 *   <li>The generic code with the highest {@link GenericErrorCode#priority()} wins; all fields
 *       violating that code are listed, sorted and de-duplicated, as {@code {a, b}}.</li>
 * </ol>
 *
 * <h2>Isolation</h2>
 * An instance owns <em>private</em> resources: a {@linkplain ObjectMapper#copy() copy} of any mapper passed to the
 * builder (or an internally built one) and its own Hibernate {@link ValidatorFactory}. Neither is shared or exposed,
 * so creating a validator never changes an application's {@code ObjectMapper}, its default Jakarta
 * {@code Validator}, system properties or any other global state. {@link #close()} releases the factory; in Spring,
 * {@code close()} is called automatically when the context shuts down.
 *
 * <p>Instances are immutable and thread-safe. Create as many as needed (e.g. one per module with its own mapping):
 * <pre>{@code
 * JsonRequestValidator v = JsonRequestValidator.builder()
 *         .objectMapper(appMapper)                       // optional, copied
 *         .errorMapping(Map.of("USR_001", "E_MANDATORY_MISSING"))
 *         .failOnUnknownProperties(true)
 *         .build();
 * }</pre>
 */
public final class JsonRequestValidator implements AutoCloseable {

    /** A constraint message matching this pattern is treated as a VO-specific error code. */
    public static final Pattern VO_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+");

    private final ObjectMapper objectMapper;
    private final ValidatorFactory validatorFactory;
    private final Validator validator;
    private final ErrorCodeMapper errorCodeMapper;
    private final boolean failOnUnknownProperties;
    private final FieldNameResolver fieldNames;
    private volatile boolean closed;

    private JsonRequestValidator(Builder b) {
        this.objectMapper = b.objectMapper != null ? b.objectMapper.copy() : new ObjectMapper();
        this.errorCodeMapper = b.errorCodeMapper != null ? b.errorCodeMapper
                : new PropertiesErrorCodeMapper(b.errorMapping);
        this.failOnUnknownProperties = b.failOnUnknownProperties;
        this.validatorFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        this.validator = validatorFactory.getValidator();
        this.fieldNames = new FieldNameResolver(objectMapper);
    }

    /** @return a new builder; every setting is optional */
    public static Builder builder() {
        return new Builder();
    }

    /** Closes the privately owned {@link ValidatorFactory}. Idempotent; {@link #validate} fails afterwards. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            validatorFactory.close();
        }
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
        if (closed) {
            throw new IllegalStateException("JsonRequestValidator is closed");
        }
        if (requestBody == null || requestBody.isBlank()) {
            return ValidationResult.failure(GenericErrorCode.EMPTY_BODY, "");
        }
        T vo;
        try {
            vo = reader(voType).readValue(requestBody);
        } catch (JsonProcessingException e) {
            return fromJacksonException(e, voType);
        }
        if (vo == null) {
            return ValidationResult.failure(GenericErrorCode.EMPTY_BODY, "");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(vo);
        if (violations.isEmpty()) {
            return ValidationResult.success(vo);
        }
        return fromViolations(violations, voType);
    }

    private <T> ObjectReader reader(Class<T> voType) {
        ObjectReader reader = objectMapper.readerFor(voType);
        return failOnUnknownProperties
                ? reader.with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                : reader.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private <T> ValidationResult<T> fromJacksonException(JacksonException e, Class<T> voType) {
        if (e instanceof PropertyBindingException pbe) {
            // Unrecognized / ignored property: the path already ends with the offending name
            return ValidationResult.failure(GenericErrorCode.UNKNOWN_FIELD, braces(List.of(path(pbe, voType))));
        }
        if (e instanceof MismatchedInputException mie) {
            return ValidationResult.failure(GenericErrorCode.TYPE_MISMATCH, braces(List.of(path(mie, voType))));
        }
        if (e instanceof JsonMappingException jme && !jme.getPath().isEmpty()) {
            // e.g. a value that cannot be instantiated or converted (enum, date ...)
            return ValidationResult.failure(GenericErrorCode.TYPE_MISMATCH, braces(List.of(path(jme, voType))));
        }
        return ValidationResult.failure(GenericErrorCode.MALFORMED_JSON, "");
    }

    private String path(JsonMappingException e, Class<?> voType) {
        return fieldNames.resolve(voType, e.getPath());
    }

    private <T> ValidationResult<T> fromViolations(Set<ConstraintViolation<T>> violations, Class<T> voType) {
        List<Resolved> resolved = violations.stream()
                .map(v -> new Resolved(resolve(v), fieldNames.resolve(voType, v.getPropertyPath())))
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

    private static String braces(Iterable<String> fields) {
        return "{" + String.join(", ", fields) + "}";
    }

    private record Resolved(GenericErrorCode code, String path) {
    }

    /**
     * Builder for {@link JsonRequestValidator}. Not thread-safe; the built validator is.
     */
    public static final class Builder {

        private ObjectMapper objectMapper;
        private ErrorCodeMapper errorCodeMapper;
        private Map<String, String> errorMapping = Map.of();
        private boolean failOnUnknownProperties = true;

        private Builder() {
        }

        /**
         * @param objectMapper template mapper (e.g. the application's, to inherit its naming strategy and modules);
         *                     it is {@linkplain ObjectMapper#copy() copied} and never modified. Default: a plain mapper.
         * @return this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * @param errorMapping VO code to generic code (wire code or enum name); ignored if an
         *                     {@link #errorCodeMapper(ErrorCodeMapper)} is set
         * @return this builder
         */
        public Builder errorMapping(Map<String, String> errorMapping) {
            this.errorMapping = Map.copyOf(Objects.requireNonNull(errorMapping, "errorMapping"));
            return this;
        }

        /**
         * @param errorCodeMapper custom mapper; takes precedence over {@link #errorMapping(Map)}
         * @return this builder
         */
        public Builder errorCodeMapper(ErrorCodeMapper errorCodeMapper) {
            this.errorCodeMapper = errorCodeMapper;
            return this;
        }

        /**
         * @param failOnUnknownProperties reject unknown JSON properties (default {@code true})
         * @return this builder
         */
        public Builder failOnUnknownProperties(boolean failOnUnknownProperties) {
            this.failOnUnknownProperties = failOnUnknownProperties;
            return this;
        }

        /** @return a new validator owning its own mapper copy and validator factory */
        public JsonRequestValidator build() {
            return new JsonRequestValidator(this);
        }
    }
}
