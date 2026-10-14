package com.abhi1kush.validator.core;

import com.abhi1kush.validator.TestSupport;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRequestValidatorTest {

    record User(@NotBlank(message = "USR_001") String name,
                @NotBlank(message = "USR_003") @Email(message = "USR_004") String email,
                @Pattern(regexp = "^[0-9]{10}$", message = "USR_005") String phone,
                @NotNull @Min(18) @Max(120) Integer age,
                @Size(max = 5, message = "USR_002") String code) {
    }

    record Address(@NotBlank String city, @NotBlank String zip) {
    }

    record Item(@NotBlank String sku, @NotNull @Min(1) Integer qty) {
    }

    record Order(@NotNull @Valid Address address, @NotEmpty List<@Valid Item> items) {
    }

    record Unmapped(@NotBlank(message = "XYZ_999") String a, @Email(message = "not a code") String b) {
    }

    private static final Map<String, String> MAPPING = Map.of(
            "USR_001", "E_MANDATORY_MISSING",
            "USR_003", "MANDATORY_MISSING",
            "USR_004", "E_INVALID_FORMAT",
            "USR_005", "E_INVALID_FORMAT",
            "USR_002", "E_INVALID_LENGTH");

    private final JsonRequestValidator validator = TestSupport.jsonValidator(MAPPING, true);

    private static final String VALID_USER =
            "{\"name\":\"Abhi\",\"email\":\"a@b.com\",\"phone\":\"9876543210\",\"age\":30,\"code\":\"X1\"}";

    @Test
    void validBodyReturnsVo() {
        ValidationResult<User> r = validator.validate(VALID_USER, User.class);
        assertThat(r.isValid()).isTrue();
        assertThat(r.vo()).get().extracting(User::name).isEqualTo("Abhi");
        assertThat(r.errorCode()).isEmpty();
        assertThat(r.errorDescription()).isEmpty();
        assertThat(r.httpStatusCode()).isEmpty();
    }

    @Nested
    class BodyErrors {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "\n", "null"})
        void emptyBody(String body) {
            assertFailure(validator.validate(body, User.class), "E_EMPTY_BODY", "Request body is empty", 400);
        }

        @ParameterizedTest
        @ValueSource(strings = {"{", "{\"name\":}", "not json", "{\"name\":\"a\",}", "{'name':'a'}"})
        void malformedJson(String body) {
            assertFailure(validator.validate(body, User.class), "E_MALFORMED_JSON", "Malformed JSON request body", 400);
        }

        @Test
        void unknownFieldRejectedByDefault() {
            assertFailure(validator.validate("{\"name\":\"a\",\"foo\":1}", User.class),
                    "E_UNKNOWN_FIELD", "Unknown fields: {foo}", 400);
        }

        @Test
        void nestedUnknownFieldReportsFullPath() {
            assertFailure(validator.validate("{\"address\":{\"city\":\"x\",\"zip\":\"1\",\"planet\":\"Mars\"},"
                    + "\"items\":[{\"sku\":\"a\",\"qty\":1}]}", Order.class),
                    "E_UNKNOWN_FIELD", "Unknown fields: {address.planet}", 400);
        }

        @Test
        void unknownFieldIgnoredWhenConfigured() {
            ValidationResult<User> r = TestSupport.jsonValidator(MAPPING, false)
                    .validate(VALID_USER.replace("}", ",\"foo\":1}"), User.class);
            assertThat(r.isValid()).isTrue();
        }

        @Test
        void typeMismatch() {
            assertFailure(validator.validate(VALID_USER.replace("30", "\"thirty\""), User.class),
                    "E_TYPE_MISMATCH", "Invalid type for fields: {age}", 400);
        }

        @Test
        void typeMismatchInListReportsIndexedPath() {
            assertFailure(validator.validate("{\"address\":{\"city\":\"x\",\"zip\":\"1\"},"
                    + "\"items\":[{\"sku\":\"a\",\"qty\":1},{\"sku\":\"b\",\"qty\":\"many\"}]}", Order.class),
                    "E_TYPE_MISMATCH", "Invalid type for fields: {items[1].qty}", 400);
        }

        @Test
        void rootTypeMismatch() {
            assertFailure(validator.validate("[1,2]", User.class), "E_TYPE_MISMATCH", "Invalid type for fields: {$}", 400);
        }

        @Test
        void objectWhereListExpected() {
            assertFailure(validator.validate("{\"address\":{\"city\":\"x\",\"zip\":\"1\"},\"items\":5}", Order.class),
                    "E_TYPE_MISMATCH", "Invalid type for fields: {items}", 400);
        }
    }

    @Nested
    class PriorityAndGrouping {

        @Test
        void groupsSameCodeFieldsInDeterministicOrder() {
            assertFailure(validator.validate("{\"age\":30}", User.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {email, name}", 400);
        }

        @Test
        void mandatoryOutranksFormatAndRange() {
            // name missing (mandatory), email bad (format), age 5 (range), code too long (length)
            assertFailure(validator.validate(
                            "{\"email\":\"bad\",\"phone\":\"12\",\"age\":5,\"code\":\"toolong\"}", User.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {name}", 400);
        }

        @Test
        void formatOutranksLengthAndRange() {
            assertFailure(validator.validate(
                            "{\"name\":\"a\",\"email\":\"bad\",\"phone\":\"12\",\"age\":5,\"code\":\"toolong\"}", User.class),
                    "E_INVALID_FORMAT", "Invalid format for fields: {email, phone}", 400);
        }

        @Test
        void lengthOutranksRange() {
            assertFailure(validator.validate(
                            "{\"name\":\"a\",\"email\":\"a@b.com\",\"age\":500,\"code\":\"toolong\"}", User.class),
                    "E_INVALID_LENGTH", "Invalid length for fields: {code}", 400);
        }

        @Test
        void rangeFromConstraintDefaultWhenNoVoCode() {
            assertFailure(validator.validate("{\"name\":\"a\",\"email\":\"a@b.com\",\"age\":500}", User.class),
                    "E_OUT_OF_RANGE", "Value out of range for fields: {age}", 400);
        }

        @Test
        void fieldIsListedOnceEvenWithTwoViolationsOfSameCode() {
            // email "" violates @NotBlank (USR_003 -> mandatory); @Email accepts "" so only one entry
            assertFailure(validator.validate("{\"name\":\"\",\"email\":\"\",\"age\":30}", User.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {email, name}", 400);
        }

        @Test
        void resultIsStableAcrossRuns() {
            String body = "{\"address\":{},\"items\":[{},{\"sku\":\"x\"},{}]}";
            var first = validator.validate(body, Order.class);
            for (int i = 0; i < 50; i++) {
                assertThat(validator.validate(body, Order.class)).isEqualTo(first);
            }
        }
    }

    @Nested
    class NestedAndListPaths {

        @Test
        void nestedFieldsUseFullPaths() {
            assertFailure(validator.validate("{\"address\":{\"city\":\" \"},\"items\":[{\"sku\":\"a\",\"qty\":1}]}",
                            Order.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {address.city, address.zip}", 400);
        }

        @Test
        void listElementsUseIndexedPathsInNumericOrder() {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                items.append(i == 0 ? "" : ",").append(i == 2 || i == 10 ? "{\"sku\":\"s\"}" : "{\"sku\":\"s\",\"qty\":1}");
            }
            assertFailure(validator.validate("{\"address\":{\"city\":\"c\",\"zip\":\"z\"},\"items\":[" + items + "]}",
                            Order.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {items[2].qty, items[10].qty}", 400);
        }

        @Test
        void mixedNestedListAndTopLevel() {
            assertFailure(validator.validate("{\"address\":{\"zip\":\"z\"},\"items\":[{\"qty\":0}]}", Order.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {address.city, items[0].sku}", 400);
        }

        @Test
        void listElementRangeViolation() {
            assertFailure(validator.validate("{\"address\":{\"city\":\"c\",\"zip\":\"z\"},\"items\":[{\"sku\":\"a\",\"qty\":0}]}",
                            Order.class),
                    "E_OUT_OF_RANGE", "Value out of range for fields: {items[0].qty}", 400);
        }

        @Test
        void missingNestedObjectAndEmptyList() {
            assertFailure(validator.validate("{\"items\":[]}", Order.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {address, items}", 400);
        }
    }

    @Nested
    class Mapping {

        @Test
        void voCodeIsMappedToGenericCode() {
            // USR_005 is a @Pattern but mapped explicitly to E_INVALID_FORMAT
            assertFailure(validator.validate(VALID_USER.replace("9876543210", "x"), User.class),
                    "E_INVALID_FORMAT", "Invalid format for fields: {phone}", 400);
        }

        @Test
        void mappingCanOverrideTheConstraintDefault() {
            // remap the @Size code to mandatory: now it competes with (and groups with) @NotBlank
            JsonRequestValidator v = TestSupport.jsonValidator(Map.of("USR_001", "E_MANDATORY_MISSING",
                    "USR_002", "E_MANDATORY_MISSING"), true);
            assertFailure(v.validate("{\"email\":\"a@b.com\",\"age\":30,\"code\":\"toolong\"}", User.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {code, name}", 400);
        }

        @Test
        void unmappedVoCodeAndPlainMessageFallBackToConstraintDefault() {
            JsonRequestValidator v = TestSupport.jsonValidator(Map.of(), true);
            assertFailure(v.validate("{\"a\":\"\",\"b\":\"bad\"}", Unmapped.class),
                    "E_MANDATORY_MISSING", "Mandatory fields missing: {a}", 400);
            assertFailure(v.validate("{\"a\":\"x\",\"b\":\"bad\"}", Unmapped.class),
                    "E_INVALID_FORMAT", "Invalid format for fields: {b}", 400);
        }

        @Test
        void customErrorCodeMapperBeanIsUsed() {
            ErrorCodeMapper mapper = code -> code.startsWith("XYZ_") ? Optional.of(GenericErrorCode.OUT_OF_RANGE)
                    : Optional.empty();
            JsonRequestValidator v = new JsonRequestValidator(new ObjectMapper(), TestSupport.VALIDATOR, mapper, true);
            assertFailure(v.validate("{\"a\":\"\"}", Unmapped.class),
                    "E_OUT_OF_RANGE", "Value out of range for fields: {a}", 400);
        }
    }

    @Test
    void threadSafeUnderConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ValidationResult<User>>> tasks = IntStream.range(0, 400)
                    .<Callable<ValidationResult<User>>>mapToObj(i -> () -> validator.validate(
                            i % 2 == 0 ? VALID_USER : "{\"age\":30}", User.class))
                    .toList();
            List<Future<ValidationResult<User>>> results = pool.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) {
                ValidationResult<User> r = results.get(i).get();
                if (i % 2 == 0) {
                    assertThat(r.isValid()).isTrue();
                } else {
                    assertThat(r.errorDescription()).contains("Mandatory fields missing: {email, name}");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    static void assertFailure(ValidationResult<?> r, String code, String description, int status) {
        assertThat(r.vo()).isEmpty();
        assertThat(r.errorCode()).contains(code);
        assertThat(r.errorDescription()).contains(description);
        assertThat(r.httpStatusCode()).contains(status);
    }
}
