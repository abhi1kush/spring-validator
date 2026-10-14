package com.abhi1kush.validator.core;

import com.abhi1kush.validator.TestSupport;
import com.abhi1kush.validator.annotation.ErrorField;
import com.abhi1kush.validator.annotation.OptionalDate;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static com.abhi1kush.validator.core.JsonRequestValidatorTest.assertFailure;
import static org.assertj.core.api.Assertions.assertThat;

/** Error paths use API names: @ErrorField, then the Jackson JSON name, then the Java name. */
class FieldNameResolutionTest {

    static final String MANDATORY = "E_MANDATORY_MISSING";
    static final String MANDATORY_MSG = "Mandatory fields missing: ";

    // --- rename-proof pair: different Java names, same API names ---
    record CustomerV1(@JsonProperty("customer_name") @NotBlank String name,
                      @JsonProperty("mobile") @ErrorField("mobile_number") @NotBlank String phone,
                      @JsonProperty("joined_on") @OptionalDate String joined) {
    }

    record CustomerV2(@JsonProperty("customer_name") @NotBlank String fullName,
                      @JsonProperty("mobile") @ErrorField("mobile_number") @NotBlank String mobilePhoneNumber,
                      @JsonProperty("joined_on") @OptionalDate String registrationDate) {
    }

    // --- nested record + list of Lombok items + container elements ---
    record Address(@NotBlank String city, @JsonProperty("zip_code") @NotBlank String zip) {
    }

    @Value
    @Builder
    @Jacksonized
    static class Item {
        @JsonProperty("sku_code")
        @NotBlank
        String sku;

        @ErrorField("quantity")
        @NotNull
        @Min(1)
        Integer qty;
    }

    record Order(@JsonProperty("shipping_address") @NotNull @Valid Address shippingAddress,
                 @NotNull @Size(min = 1) List<@Valid Item> items,
                 @JsonProperty("tag_list") List<@NotBlank String> tags,
                 @JsonProperty("extra") Map<String, @NotBlank String> attributes) {
    }

    // --- precedence: ErrorField > JsonProperty > Java name ---
    record Precedence(@ErrorField("ef_only") @NotBlank String a,
                      @JsonProperty("json_b") @ErrorField("ef_b") @NotBlank String b,
                      @JsonProperty("json_c") @NotBlank String c,
                      @NotBlank String d) {
    }

    // --- getter-level @ErrorField on a plain class ---
    static class GetterBean {
        private String code;

        @ErrorField("product_code")
        @NotBlank
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    // --- global naming strategy ---
    record Camel(@NotBlank String firstName, @NotNull @Valid Address homeAddress,
                 @ErrorField("surname") @NotBlank String lastName, @OptionalMinSize(3) String nickName) {
    }

    private final JsonRequestValidator validator = TestSupport.jsonValidator(Map.of(), true);

    @Nested
    class RenameProof {

        @Test
        void differentJavaNamesGiveIdenticalErrors() {
            for (String body : List.of("{}", "{\"customer_name\":\"x\"}", "{\"mobile\":\"1\"}",
                    "{\"customer_name\":\"x\",\"mobile\":1,\"joined_on\":\"2024-02-30\"}",
                    "{\"customer_name\":\"x\",\"mobile\":[1]}", "{\"customer_name\":\"x\",\"fullName\":\"y\"}")) {
                ValidationResult<CustomerV1> v1 = validator.validate(body, CustomerV1.class);
                ValidationResult<CustomerV2> v2 = validator.validate(body, CustomerV2.class);
                assertThat(v2.errorCode()).as(body).isEqualTo(v1.errorCode());
                assertThat(v2.errorDescription()).as(body).isEqualTo(v1.errorDescription());
            }
            assertFailure(validator.validate("{}", CustomerV2.class), MANDATORY,
                    MANDATORY_MSG + "{customer_name, mobile_number}", 400);
        }

        @Test
        void javaNameNeverLeaks() {
            String d = validator.validate("{}", CustomerV2.class).errorDescription().orElseThrow();
            assertThat(d).doesNotContain("fullName").doesNotContain("mobilePhoneNumber");
        }
    }

    @Nested
    class Precedences {

        @Test
        void errorFieldThenJsonPropertyThenJavaName() {
            assertFailure(validator.validate("{}", Precedence.class), MANDATORY,
                    MANDATORY_MSG + "{d, ef_b, ef_only, json_c}", 400);
        }

        @Test
        void getterLevelErrorField() {
            assertFailure(validator.validate("{}", GetterBean.class), MANDATORY, MANDATORY_MSG + "{product_code}", 400);
        }
    }

    @Nested
    class NestedAndLists {

        @Test
        void nestedRecordUsesJsonNames() {
            assertFailure(validator.validate("{\"shipping_address\":{},\"items\":[{\"sku_code\":\"a\",\"qty\":1}]}",
                    Order.class), MANDATORY, MANDATORY_MSG + "{shipping_address.city, shipping_address.zip_code}", 400);
        }

        @Test
        void lombokListElementsUseJsonNamesAndErrorField() {
            assertFailure(validator.validate("{\"shipping_address\":{\"city\":\"c\",\"zip_code\":\"z\"},"
                    + "\"items\":[{\"sku_code\":\"a\",\"qty\":1},{\"qty\":1},{\"sku_code\":\"c\"}]}", Order.class),
                    MANDATORY, MANDATORY_MSG + "{items[1].sku_code, items[2].quantity}", 400);
        }

        @Test
        void containerElementsAndMapValues() {
            ValidationResult<Order> r = validator.validate("{\"shipping_address\":{\"city\":\"c\",\"zip_code\":\"z\"},"
                    + "\"items\":[{\"sku_code\":\"a\",\"qty\":1}],\"tag_list\":[\"ok\",\" \"],\"extra\":{\"k\":\"\"}}",
                    Order.class);
            assertFailure(r, MANDATORY, MANDATORY_MSG + "{extra[k], tag_list[1]}", 400);
        }

        @Test
        void bindingTypeMismatchUsesErrorFieldInLists() {
            assertFailure(validator.validate("{\"items\":[{\"sku_code\":\"a\",\"qty\":1},{\"sku_code\":\"b\",\"qty\":\"x\"}]}",
                    Order.class), "E_TYPE_MISMATCH", "Invalid type for fields: {items[1].quantity}", 400);
        }

        @Test
        void bindingUnknownFieldInNestedObjectUsesJsonParentName() {
            assertFailure(validator.validate("{\"shipping_address\":{\"city\":\"c\",\"zip\":\"z\"}}", Order.class),
                    "E_UNKNOWN_FIELD", "Unknown fields: {shipping_address.zip}", 400);
        }

        @Test
        void bindingTypeMismatchUsesErrorFieldTopLevel() {
            assertFailure(validator.validate("{\"customer_name\":\"x\",\"mobile\":{}}", CustomerV1.class),
                    "E_TYPE_MISMATCH", "Invalid type for fields: {mobile_number}", 400);
        }
    }

    @Nested
    class NamingStrategy {

        private final JsonRequestValidator snake = new JsonRequestValidator(
                new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE),
                TestSupport.VALIDATOR, new PropertiesErrorCodeMapper(Map.of()), true);

        @Test
        void globalSnakeCaseIsHonoured() {
            assertFailure(snake.validate("{\"home_address\":{\"city\":\"c\"}}", Camel.class), MANDATORY,
                    MANDATORY_MSG + "{first_name, home_address.zip_code, surname}", 400);
        }

        @Test
        void snakeCaseForBindingErrors() {
            assertFailure(snake.validate("{\"firstName\":\"x\"}", Camel.class),
                    "E_UNKNOWN_FIELD", "Unknown fields: {firstName}", 400);
            assertFailure(snake.validate("{\"first_name\":\"x\",\"nick_name\":\"ab\",\"last_name\":\"l\","
                    + "\"home_address\":{\"city\":\"c\",\"zip_code\":\"z\"}}", Camel.class),
                    "E_MIN_LENGTH_NOT_MET", "Length is less than allowed length for fields: {nick_name}", 400);
        }

        @Test
        void sameClassDifferentMappersDoNotShareCache() {
            assertFailure(validator.validate("{\"homeAddress\":{\"city\":\"c\",\"zip_code\":\"z\"},\"lastName\":\"l\"}",
                    Camel.class), MANDATORY, MANDATORY_MSG + "{firstName}", 400);
            assertFailure(snake.validate("{\"home_address\":{\"city\":\"c\",\"zip_code\":\"z\"},\"last_name\":\"l\"}",
                    Camel.class), MANDATORY, MANDATORY_MSG + "{first_name}", 400);
        }
    }

    @Test
    void sortingUsesResolvedNames() {
        // Java order a, b, c, d; resolved names sort as d < ef_b < ef_only < json_c
        String d = validator.validate("{}", Precedence.class).errorDescription().orElseThrow();
        assertThat(d).endsWith("{d, ef_b, ef_only, json_c}");
    }

    @Test
    void concurrentResolutionIsConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            JsonRequestValidator fresh = TestSupport.jsonValidator(Map.of(), true);
            List<Future<String>> results = pool.invokeAll(IntStream.range(0, 200)
                    .<java.util.concurrent.Callable<String>>mapToObj(i -> () -> fresh.validate(
                            "{\"shipping_address\":{},\"items\":[{}]}", Order.class).errorDescription().orElseThrow())
                    .toList());
            for (Future<String> f : results) {
                assertThat(f.get()).isEqualTo(MANDATORY_MSG
                        + "{items[0].quantity, items[0].sku_code, shipping_address.city, shipping_address.zip_code}");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
