package com.abhi1kush.validator.annotation;

import com.abhi1kush.validator.TestSupport;
import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalSizeValidatorTest {

    record Vo(@OptionalSize(min = 3, max = 5) String value) {
    }

    record Invalid(@OptionalSize(min = 5, max = 3) String value) {
    }

    private final JsonRequestValidator json = TestSupport.jsonValidator(Map.of(), true);

    private static boolean valid(String value) {
        return TestSupport.VALIDATOR.validate(new Vo(value)).isEmpty();
    }

    @Test
    void nullIsValid() {
        assertThat(valid(null)).isTrue();
    }

    @Test
    void emptyIsValid() {
        assertThat(valid("")).isTrue();
    }

    @Test
    void absentFieldIsValid() {
        assertThat(json.validate("{}", Vo.class).isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcd", "abcde", "   "})
    void inRangeIsValidIncludingWhitespaceWhichIsNotTrimmed(String value) {
        assertThat(valid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abcdef", " ", "      "})
    void outOfRangeIsInvalid(String value) {
        assertThat(valid(value)).isFalse();
    }

    @Test
    void invalidValueMapsToInvalidLength() {
        ValidationResult<Vo> r = json.validate("{\"value\":\"ab\"}", Vo.class);
        assertThat(r.errorCode()).contains("E_INVALID_LENGTH");
        assertThat(r.errorDescription()).contains("Invalid length for fields: {value}");
    }

    @Test
    void misconfiguredBoundsFailFast() {
        assertThatThrownBy(() -> TestSupport.VALIDATOR.validate(new Invalid("x")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
