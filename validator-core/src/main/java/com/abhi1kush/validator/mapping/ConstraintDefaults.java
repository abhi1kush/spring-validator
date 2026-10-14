package com.abhi1kush.validator.mapping;

import com.abhi1kush.validator.annotation.OptionalDate;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import com.abhi1kush.validator.annotation.OptionalSize;
import com.abhi1kush.validator.core.GenericErrorCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Negative;
import jakarta.validation.constraints.NegativeOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Default generic code per constraint annotation, used when a constraint carries no
 * VO-specific code or the code is not mapped.
 */
public final class ConstraintDefaults {

    private static final Map<Class<? extends Annotation>, GenericErrorCode> DEFAULTS = Map.ofEntries(
            Map.entry(NotNull.class, GenericErrorCode.MANDATORY_MISSING),
            Map.entry(NotBlank.class, GenericErrorCode.MANDATORY_MISSING),
            Map.entry(NotEmpty.class, GenericErrorCode.MANDATORY_MISSING),
            Map.entry(Pattern.class, GenericErrorCode.INVALID_FORMAT),
            Map.entry(Email.class, GenericErrorCode.INVALID_FORMAT),
            Map.entry(Digits.class, GenericErrorCode.INVALID_FORMAT),
            Map.entry(OptionalDate.class, GenericErrorCode.INVALID_DATE),
            Map.entry(Past.class, GenericErrorCode.INVALID_DATE),
            Map.entry(PastOrPresent.class, GenericErrorCode.INVALID_DATE),
            Map.entry(Future.class, GenericErrorCode.INVALID_DATE),
            Map.entry(FutureOrPresent.class, GenericErrorCode.INVALID_DATE),
            Map.entry(Size.class, GenericErrorCode.INVALID_LENGTH),
            Map.entry(OptionalSize.class, GenericErrorCode.INVALID_LENGTH),
            Map.entry(OptionalMinSize.class, GenericErrorCode.INVALID_LENGTH),
            Map.entry(Min.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(Max.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(DecimalMin.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(DecimalMax.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(Positive.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(PositiveOrZero.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(Negative.class, GenericErrorCode.OUT_OF_RANGE),
            Map.entry(NegativeOrZero.class, GenericErrorCode.OUT_OF_RANGE));

    private ConstraintDefaults() {
    }

    /**
     * @param constraintType the constraint annotation type
     * @return its default generic code, {@link GenericErrorCode#INVALID_VALUE} if unknown
     */
    public static GenericErrorCode forConstraint(Class<? extends Annotation> constraintType) {
        return DEFAULTS.getOrDefault(constraintType, GenericErrorCode.INVALID_VALUE);
    }
}
