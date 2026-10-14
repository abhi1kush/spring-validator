package com.abhi1kush.validator.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Minimum length for an <em>optional</em> {@link CharSequence}: {@code null} and {@code ""}
 * are valid, otherwise {@code length >= value}. Whitespace is not trimmed (see {@link OptionalSize}).
 */
@Documented
@Constraint(validatedBy = OptionalMinSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalMinSize {

    /** @return minimum length (inclusive) for non-empty values */
    int value();

    /** @return message or VO-specific error code */
    String message() default "length must be at least {value} when present";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return payload */
    Class<? extends Payload>[] payload() default {};
}
