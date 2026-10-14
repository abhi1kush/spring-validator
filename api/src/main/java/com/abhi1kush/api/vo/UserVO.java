package com.abhi1kush.api.vo;

import com.abhi1kush.validator.annotation.OptionalDate;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sample user VO. Constraint messages of the form {@code USR_xxx} are VO-specific error codes,
 * mapped to generic codes in {@code validator.error-mapping}. {@code age} uses default messages,
 * so it falls back to the constraint-type default generic code.
 *
 * @param name      mandatory, at most 50 characters
 * @param email     mandatory, valid e-mail
 * @param phone     optional, 10 to 15 digits with optional leading +
 * @param age       mandatory, 18 to 120
 * @param nickname  optional, 3 to 20 characters when present
 * @param birthDate optional, {@code yyyy-MM-dd}, in the past
 */
public record UserVO(
        @NotBlank(message = "USR_001") @Size(max = 50, message = "USR_002") String name,
        @NotBlank(message = "USR_003") @Email(message = "USR_004") String email,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "USR_005") String phone,
        @NotNull @Min(18) @Max(120) Integer age,
        @OptionalMinSize(value = 3, message = "USR_006") @Size(max = 20, message = "USR_008") String nickname,
        @OptionalDate(pattern = "yyyy-MM-dd", when = OptionalDate.Bound.PAST, message = "USR_007") String birthDate) {
}
