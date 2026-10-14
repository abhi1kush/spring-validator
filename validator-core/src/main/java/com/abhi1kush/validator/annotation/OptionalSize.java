package com.abhi1kush.validator.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Length check for an <em>optional</em> {@link CharSequence}.
 *
 * <p>{@code null} and {@code ""} are valid (the field is optional). Any other value must
 * have {@code min <= length <= max}. <b>Whitespace is not trimmed</b>: {@code "   "} is a
 * non-empty value of length 3 and is checked like any other string. Combine with
 * {@code @Pattern} if blank values must be rejected.
 */
@Documented
@Constraint(validatedBy = OptionalSizeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalSize {

    /** @return minimum length (inclusive) for non-empty values */
    int min() default 0;

    /** @return maximum length (inclusive) */
    int max() default Integer.MAX_VALUE;

    /** @return message or VO-specific error code */
    String message() default "length must be between {min} and {max} when present";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return payload */
    Class<? extends Payload>[] payload() default {};
}
