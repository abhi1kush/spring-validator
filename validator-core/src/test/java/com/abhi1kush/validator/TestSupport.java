package com.abhi1kush.validator;

import com.abhi1kush.validator.core.JsonRequestValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Map;

/** Shared, thread-safe test fixtures (no Spring context). */
public final class TestSupport {

    private static final ValidatorFactory FACTORY = Validation.byProvider(org.hibernate.validator.HibernateValidator.class).configure()
            .messageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator())
            .buildValidatorFactory();

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
        return JsonRequestValidator.builder().errorMapping(mapping)
                .failOnUnknownProperties(failOnUnknownProperties).build();
    }
}
