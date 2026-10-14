package com.abhi1kush.api.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;

/**
 * Order line. Demonstrates an immutable Lombok VO ({@code @Value @Builder @Jacksonized})
 * alongside records; default constraint messages map via constraint-type defaults.
 */
@Value
@Builder
@Jacksonized
public class OrderItemVO {

    /** Stock keeping unit, mandatory; JSON {@code sku_code}. */
    @JsonProperty("sku_code")
    @NotBlank
    String sku;

    /** Quantity, 1 to 100. */
    @NotNull
    @Min(1)
    @Max(100)
    Integer qty;

    /** Unit price, at least 0.01. */
    @NotNull
    @DecimalMin("0.01")
    BigDecimal price;
}
