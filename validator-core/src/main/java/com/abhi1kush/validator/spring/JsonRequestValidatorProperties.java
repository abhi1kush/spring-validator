package com.abhi1kush.validator.spring;

import com.abhi1kush.validator.core.JsonRequestValidator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Settings of the opt-in Spring integration, under the namespaced prefix {@value #PREFIX}. Only bound when
 * {@link EnableJsonRequestValidator} is present, or explicitly via {@link #bind(Environment, String)}; it is never
 * registered as a bean ({@code @ConfigurationProperties} is kept only for IDE metadata).
 *
 * @param failOnUnknownProperties    reject unknown JSON properties (default {@code true})
 * @param useApplicationObjectMapper copy the application's unique {@code ObjectMapper} as a template so JSON names
 *                                   follow its naming strategy (default {@code true}); otherwise a plain mapper
 * @param errorMapping               VO code to generic code
 */
@ConfigurationProperties(prefix = JsonRequestValidatorProperties.PREFIX)
public record JsonRequestValidatorProperties(Boolean failOnUnknownProperties,
                                             Boolean useApplicationObjectMapper,
                                             Map<String, String> errorMapping) {

    /** Default property prefix. */
    public static final String PREFIX = "spring-validator";

    /** Applies defaults. */
    public JsonRequestValidatorProperties {
        failOnUnknownProperties = failOnUnknownProperties == null ? Boolean.TRUE : failOnUnknownProperties;
        useApplicationObjectMapper = useApplicationObjectMapper == null ? Boolean.TRUE : useApplicationObjectMapper;
        errorMapping = errorMapping == null ? Map.of() : Map.copyOf(errorMapping);
    }

    /**
     * Binds settings from any prefix, e.g. one per module ({@code orders.validator}).
     *
     * @param environment Spring environment
     * @param prefix      property prefix
     * @return bound settings, defaults if nothing is configured
     */
    public static JsonRequestValidatorProperties bind(Environment environment, String prefix) {
        return Binder.get(environment).bind(prefix, JsonRequestValidatorProperties.class)
                .orElseGet(() -> new JsonRequestValidatorProperties(null, null, null));
    }

    /** @return a builder pre-filled with these settings (object mapper and custom mapper not set) */
    public JsonRequestValidator.Builder toBuilder() {
        return JsonRequestValidator.builder()
                .failOnUnknownProperties(failOnUnknownProperties)
                .errorMapping(errorMapping);
    }
}
