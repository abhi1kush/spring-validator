package com.abhi1kush.validator.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;

/**
 * Validator for {@link OptionalDate}. The formatter is immutable, so the validator is thread-safe.
 * {@code ERA} defaults to CE so that {@code yyyy} works under {@link ResolverStyle#STRICT}.
 */
public class OptionalDateValidator implements ConstraintValidator<OptionalDate, CharSequence> {

    private final Clock clock;
    private DateTimeFormatter formatter;
    private OptionalDate.Bound bound;

    /** Uses the system default clock. */
    public OptionalDateValidator() {
        this(Clock.systemDefaultZone());
    }

    /** @param clock clock used for past/future bounds (useful for tests) */
    public OptionalDateValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void initialize(OptionalDate annotation) {
        this.formatter = new DateTimeFormatterBuilder()
                .appendPattern(annotation.pattern())
                .parseDefaulting(ChronoField.ERA, 1)
                .toFormatter()
                .withResolverStyle(ResolverStyle.STRICT);
        this.bound = annotation.when();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value, formatter);
        } catch (DateTimeParseException e) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        return switch (bound) {
            case ANY -> true;
            case PAST -> date.isBefore(today);
            case PAST_OR_PRESENT -> !date.isAfter(today);
            case FUTURE -> date.isAfter(today);
            case FUTURE_OR_PRESENT -> !date.isBefore(today);
        };
    }
}
