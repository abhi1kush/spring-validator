package com.abhi1kush.validator.core;

import java.util.Arrays;
import java.util.Optional;

/**
 * Registry of generic, service-level error codes.
 *
 * <p>Each code has a {@link #priority()} (higher wins when several violations occur),
 * a default message template in which {@code {fields}} is replaced by the affected
 * property paths (e.g. {@code {name, email}}) and an HTTP status.
 */
public enum GenericErrorCode {

    /** The request body is missing, blank or the JSON literal {@code null}. */
    EMPTY_BODY("E_EMPTY_BODY", 1000, "Request body is empty", 400),
    /** The request body is not syntactically valid JSON. */
    MALFORMED_JSON("E_MALFORMED_JSON", 900, "Malformed JSON request body", 400),
    /** The body contains a property the value object does not declare (when unknown fields are rejected). */
    UNKNOWN_FIELD("E_UNKNOWN_FIELD", 800, "Unknown fields: {fields}", 400),
    /** A JSON value cannot be converted to the declared Java type. */
    TYPE_MISMATCH("E_TYPE_MISMATCH", 700, "Invalid type for fields: {fields}", 400),
    /** A mandatory value is null, empty or blank. */
    MANDATORY_MISSING("E_MANDATORY_MISSING", 600, "Mandatory fields missing: {fields}", 400),
    /** A value does not match the expected format (pattern, e-mail ...). */
    INVALID_FORMAT("E_INVALID_FORMAT", 500, "Invalid format for fields: {fields}", 400),
    /** A date string cannot be parsed or violates past/future bounds. */
    INVALID_DATE("E_INVALID_DATE", 450, "Invalid date for fields: {fields}", 400),
    /**
     * A string, collection, map or array is longer than allowed ({@code @Size(max)}, {@code @Length(max)}).
     * Ranked just above {@link #MIN_LENGTH_NOT_MET}: an over-long value is the stronger signal
     * (truncation or abuse), and a fixed order keeps the selection deterministic.
     */
    MAX_LENGTH_EXCEEDED("E_MAX_LENGTH_EXCEEDED", 410, "Max length exceeded for fields: {fields}", 400),
    /** A string, collection, map or array is shorter than allowed ({@code @Size(min)}, {@code @OptionalMinSize}). */
    MIN_LENGTH_NOT_MET("E_MIN_LENGTH_NOT_MET", 400, "Length is less than allowed length for fields: {fields}", 400),
    /** A numeric value is outside the allowed range. */
    OUT_OF_RANGE("E_OUT_OF_RANGE", 300, "Value out of range for fields: {fields}", 400),
    /** Fallback for any other constraint violation. */
    INVALID_VALUE("E_INVALID_VALUE", 100, "Invalid value for fields: {fields}", 400);

    private static final String FIELDS_PLACEHOLDER = "{fields}";

    private final String code;
    private final int priority;
    private final String messageTemplate;
    private final int httpStatus;

    GenericErrorCode(String code, int priority, String messageTemplate, int httpStatus) {
        this.code = code;
        this.priority = priority;
        this.messageTemplate = messageTemplate;
        this.httpStatus = httpStatus;
    }

    /** @return the wire code, e.g. {@code E_MANDATORY_MISSING} */
    public String code() {
        return code;
    }

    /** @return priority; the highest priority violation is reported */
    public int priority() {
        return priority;
    }

    /** @return the default message template, possibly containing {@code {fields}} */
    public String messageTemplate() {
        return messageTemplate;
    }

    /** @return the HTTP status code associated with this error */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Renders the message template.
     *
     * @param fields formatted field list such as {@code {name, email}}
     * @return the description
     */
    public String describe(String fields) {
        return messageTemplate.replace(FIELDS_PLACEHOLDER, fields);
    }

    /**
     * Resolves a code either by wire code ({@code E_MANDATORY_MISSING}) or by enum
     * name ({@code MANDATORY_MISSING}), case-insensitively.
     *
     * @param value wire code or enum name
     * @return the matching code, or empty
     */
    public static Optional<GenericErrorCode> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String v = value.trim();
        return Arrays.stream(values())
                .filter(c -> c.code.equalsIgnoreCase(v) || c.name().equalsIgnoreCase(v))
                .findFirst();
    }
}
