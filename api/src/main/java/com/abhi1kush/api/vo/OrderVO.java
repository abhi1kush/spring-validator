package com.abhi1kush.api.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.abhi1kush.validator.annotation.OptionalDate;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Sample order VO (snake_case API names via {@code @JsonProperty}) with a nested address and a list of items (both cascaded with {@code @Valid}).
 *
 * @param orderId         mandatory, {@code ORD-} followed by six digits
 * @param customerEmail   mandatory e-mail
 * @param shippingAddress mandatory nested address
 * @param items           1 to 50 items
 * @param deliveryDate    optional, today or later
 * @param notes           optional, at least 5 characters when present
 */
public record OrderVO(
        @JsonProperty("order_id") @NotBlank(message = "ORD_001") @Pattern(regexp = "^ORD-[0-9]{6}$", message = "ORD_002") String orderId,
        @JsonProperty("customer_email") @NotBlank(message = "ORD_004") @Email(message = "ORD_005") String customerEmail,
        @JsonProperty("shipping_address") @NotNull(message = "ORD_006") @Valid AddressVO shippingAddress,
        @NotEmpty(message = "ORD_003") @Size(max = 50) List<@Valid OrderItemVO> items,
        @JsonProperty("delivery_date") @OptionalDate(when = OptionalDate.Bound.FUTURE_OR_PRESENT, message = "ORD_007") String deliveryDate,
        @OptionalMinSize(5) String notes) {
}
