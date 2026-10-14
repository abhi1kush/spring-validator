package com.abhi1kush.validator.annotation;

import com.abhi1kush.validator.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalMinSizeValidatorTest {

    record Vo(@OptionalMinSize(4) String value) {
    }

    record Bounded(@OptionalMinSize(3) @jakarta.validation.constraints.Size(max = 5) String value) {
    }

    record Invalid(@OptionalMinSize(-1) String value) {
    }

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
        assertThat(TestSupport.jsonValidator(Map.of(), true).validate("{}", Vo.class).isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "abcdefghij", "    "})
    void longEnoughIsValid(String value) {
        assertThat(valid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abc", "   "})
    void tooShortIsInvalid(String value) {
        assertThat(valid(value)).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"", "abc", "abcde", "   "})
    void combinedWithSizeMaxKeepsOptionalSemantics(String value) {
        assertThat(TestSupport.VALIDATOR.validate(new Bounded(value))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "ab", "abcdef", " "})
    void combinedWithSizeMaxRejectsOutOfRange(String value) {
        assertThat(TestSupport.VALIDATOR.validate(new Bounded(value))).isNotEmpty();
    }

    @Test
    void invalidValueMapsToInvalidLength() {
        var r = TestSupport.jsonValidator(Map.of(), true).validate("{\"value\":\"ab\"}", Vo.class);
        assertThat(r.errorCode()).contains("E_INVALID_LENGTH");
        assertThat(r.errorDescription()).contains("Invalid length for fields: {value}");
    }

    @Test
    void negativeMinFailsFast() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TestSupport.VALIDATOR.validate(new Invalid("x")))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
