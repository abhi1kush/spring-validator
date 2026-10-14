package com.abhi1kush.api.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Nested address VO (validated through {@code @Valid} cascading).
 *
 * @param street mandatory (VO code {@code ADR_003})
 * @param city   mandatory (VO code {@code ADR_001})
 * @param zip    JSON {@code zip_code}; mandatory (VO code {@code ADR_004}), six digits (VO code {@code ADR_002})
 */
public record AddressVO(
        @NotBlank(message = "ADR_003") String street,
        @NotBlank(message = "ADR_001") String city,
        @JsonProperty("zip_code") @NotBlank(message = "ADR_004") @Pattern(regexp = "^[0-9]{6}$", message = "ADR_002") String zip) {
}
