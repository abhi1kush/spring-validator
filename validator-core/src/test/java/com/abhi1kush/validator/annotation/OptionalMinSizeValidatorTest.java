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
}
