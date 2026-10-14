package com.abhi1kush.validator.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validator for {@link OptionalSize}. Stateless after initialisation, therefore thread-safe. */
public class OptionalSizeValidator implements ConstraintValidator<OptionalSize, CharSequence> {

    private int min;
    private int max;

    @Override
    public void initialize(OptionalSize annotation) {
        if (annotation.min() < 0 || annotation.max() < annotation.min()) {
            throw new IllegalArgumentException("@OptionalSize requires 0 <= min <= max");
        }
        this.min = annotation.min();
        this.max = annotation.max();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        int length = value.length();
        return length >= min && length <= max;
    }
}
