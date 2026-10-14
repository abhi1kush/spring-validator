package com.abhi1kush.validator.annotation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional date held in a {@link CharSequence}.
 *
 * <p>{@code null}, {@code ""} and an absent JSON property are valid. Otherwise the value must
 * parse with {@link #pattern()} using {@link java.time.format.ResolverStyle#STRICT}, so
 * {@code 2024-02-30} is rejected. Both {@code yyyy} (year-of-era) and {@code uuuu} are accepted.
 * Whitespace is not trimmed ({@code " "} is invalid). Optionally the date may be bounded with {@link #when()}.
 */
@Documented
@Constraint(validatedBy = OptionalDateValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalDate {

    /** @return {@link java.time.format.DateTimeFormatter} pattern of a date */
    String pattern() default "yyyy-MM-dd";

    /** @return temporal bound relative to today (system default zone) */
    Bound when() default Bound.ANY;

    /** @return message or VO-specific error code */
    String message() default "must be a valid date in format {pattern}";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return payload */
    Class<? extends Payload>[] payload() default {};

    /** Temporal bound relative to the current date. */
    enum Bound {
        /** No bound. */
        ANY,
        /** Strictly before today. */
        PAST,
        /** Today or before. */
        PAST_OR_PRESENT,
        /** Strictly after today. */
        FUTURE,
        /** Today or after. */
        FUTURE_OR_PRESENT
    }
}
