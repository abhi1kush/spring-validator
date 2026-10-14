package com.abhi1kush.validator;

import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Map;

/** Shared, thread-safe test fixtures (no Spring context). */
public final class TestSupport {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    /** Shared validator. */
    public static final Validator VALIDATOR = FACTORY.getValidator();

    private TestSupport() {
    }

    /**
     * @param mapping                 VO code mapping
     * @param failOnUnknownProperties unknown fields policy
     * @return a validator without Spring
     */
    public static JsonRequestValidator jsonValidator(Map<String, String> mapping, boolean failOnUnknownProperties) {
        return new JsonRequestValidator(new ObjectMapper(), VALIDATOR,
                new PropertiesErrorCodeMapper(mapping), failOnUnknownProperties);
    }
}
