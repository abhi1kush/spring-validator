package com.abhi1kush.api.vo;

import com.abhi1kush.validator.annotation.ErrorField;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * mapped to generic codes in {@code validator.error-mapping}. Errors name properties by their
 * JSON names ({@code @JsonProperty}) or an {@code @ErrorField} override, never by Java variable names. {@code age} uses default messages,
 * so it falls back to the constraint-type default generic code.
 *
 * @param name      mandatory, at most 50 characters
 * @param email     mandatory, valid e-mail
 * @param phone     optional, 10 to 15 digits with optional leading +; reported as {@code mobile_number}
 *                  in errors via {@code @ErrorField} (JSON property stays {@code phone})
 * @param age       mandatory, 18 to 120
 * @param nickname  JSON {@code nick_name}; optional, 3 to 20 characters when present
 * @param birthDate JSON {@code birth_date}; optional, {@code yyyy-MM-dd}, in the past
 */
public record UserVO(
        @NotBlank(message = "USR_001") @Size(max = 50, message = "USR_002") String name,
        @NotBlank(message = "USR_003") @Email(message = "USR_004") String email,
        @ErrorField("mobile_number") @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "USR_005") String phone,
        @NotNull @Min(18) @Max(120) Integer age,
        @JsonProperty("nick_name") @OptionalMinSize(value = 3, message = "USR_006") @Size(max = 20, message = "USR_008") String nickname,
        @JsonProperty("birth_date") @OptionalDate(pattern = "yyyy-MM-dd", when = OptionalDate.Bound.PAST, message = "USR_007") String birthDate) {
}
