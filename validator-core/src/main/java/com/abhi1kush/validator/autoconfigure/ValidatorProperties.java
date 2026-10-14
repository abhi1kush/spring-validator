package com.abhi1kush.validator.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration of the JSON request validator ({@code validator.*}).
 *
 * @param failOnUnknownProperties reject JSON properties not declared on the VO with {@code E_UNKNOWN_FIELD}
 *                                (default {@code true}); when {@code false} they are ignored
 * @param errorMapping            VO-specific code to generic code, e.g. {@code USR_001: E_MANDATORY_MISSING}
 */
@ConfigurationProperties(prefix = "validator")
public record ValidatorProperties(Boolean failOnUnknownProperties, Map<String, String> errorMapping) {

    /** Applies defaults and makes the mapping immutable. */
    public ValidatorProperties {
        failOnUnknownProperties = failOnUnknownProperties == null ? Boolean.TRUE : failOnUnknownProperties;
        errorMapping = errorMapping == null ? Map.of() : Map.copyOf(errorMapping);
    }
}
