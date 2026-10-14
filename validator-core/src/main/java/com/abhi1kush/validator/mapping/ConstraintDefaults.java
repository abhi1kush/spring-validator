package com.abhi1kush.validator.mapping;

import com.abhi1kush.validator.annotation.OptionalDate;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import com.abhi1kush.validator.core.GenericErrorCode;
import jakarta.validation.ConstraintViolation;
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

import org.hibernate.validator.constraints.Length;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

/**
 * Default generic code per constraint violation, used when a constraint carries no
 * VO-specific code or the code is not mapped.
 *
 * <p>Two-sided length constraints ({@code @Size}, Hibernate {@code @Length}) are resolved per violation:
 * the actual length of the invalid value (characters of a {@link CharSequence}, elements of a
 * {@link Collection}, entries of a {@link Map}, or array length) is compared with the constraint's
 * {@code max} attribute: greater gives {@link GenericErrorCode#MAX_LENGTH_EXCEEDED}, otherwise
 * {@link GenericErrorCode#MIN_LENGTH_NOT_MET}.
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
            Map.entry(OptionalMinSize.class, GenericErrorCode.MIN_LENGTH_NOT_MET),
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
     * @param violation the constraint violation
     * @return its default generic code, {@link GenericErrorCode#INVALID_VALUE} if unknown
     */
    public static GenericErrorCode forViolation(ConstraintViolation<?> violation) {
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
        if (annotation instanceof Size size) {
            return lengthCategory(violation.getInvalidValue(), size.max());
        }
        if (annotation instanceof Length length) {
            return lengthCategory(violation.getInvalidValue(), length.max());
        }
        return DEFAULTS.getOrDefault(annotation.annotationType(), GenericErrorCode.INVALID_VALUE);
    }

    private static GenericErrorCode lengthCategory(Object value, int max) {
        long length;
        if (value instanceof CharSequence cs) {
            length = cs.length();
        } else if (value instanceof Collection<?> c) {
            length = c.size();
        } else if (value instanceof Map<?, ?> m) {
            length = m.size();
        } else if (value != null && value.getClass().isArray()) {
            length = Array.getLength(value);
        } else {
            return GenericErrorCode.MIN_LENGTH_NOT_MET;
        }
        return length > max ? GenericErrorCode.MAX_LENGTH_EXCEEDED : GenericErrorCode.MIN_LENGTH_NOT_MET;
    }
}
