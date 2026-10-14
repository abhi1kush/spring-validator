package com.abhi1kush.validator.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validator for {@link OptionalMinSize}. Thread-safe. */
public class OptionalMinSizeValidator implements ConstraintValidator<OptionalMinSize, CharSequence> {

    private int min;

    @Override
    public void initialize(OptionalMinSize annotation) {
        if (annotation.value() < 0) {
            throw new IllegalArgumentException("@OptionalMinSize requires value >= 0");
        }
        this.min = annotation.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.isEmpty() || value.length() >= min;
    }
}
