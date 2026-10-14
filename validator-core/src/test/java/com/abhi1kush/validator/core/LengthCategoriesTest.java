package com.abhi1kush.validator.core;

import com.abhi1kush.validator.TestSupport;
import com.abhi1kush.validator.annotation.OptionalMinSize;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static com.abhi1kush.validator.core.JsonRequestValidatorTest.assertFailure;
import static org.assertj.core.api.Assertions.assertThat;

/** Length violations are split into E_MAX_LENGTH_EXCEEDED and E_MIN_LENGTH_NOT_MET. */
class LengthCategoriesTest {

    static final String MAX = "E_MAX_LENGTH_EXCEEDED";
    static final String MIN = "E_MIN_LENGTH_NOT_MET";
    static final String MAX_MSG = "Max length exceeded for fields: ";
    static final String MIN_MSG = "Length is less than allowed length for fields: ";

    record Vo(@Size(min = 2, max = 4) String a,
              @Size(min = 2, max = 4) String b,
              @Length(min = 2, max = 4) String c,
              @OptionalMinSize(3) String d,
              @Size(max = 3) String e,
              @Size(min = 1, max = 2) List<String> tags,
              @Size(max = 1) Map<String, String> attrs,
              @Size(min = 1, max = 2) int[] codes,
              @Size(min = 2, max = 3, message = "VO_LEN") String f) {
    }

    private final JsonRequestValidator validator = TestSupport.jsonValidator(Map.of(), true);

    @Test
    void enumOrderMaxAboveMin() {
        assertThat(GenericErrorCode.MAX_LENGTH_EXCEEDED.priority())
                .isGreaterThan(GenericErrorCode.MIN_LENGTH_NOT_MET.priority())
                .isLessThan(GenericErrorCode.INVALID_DATE.priority());
        assertThat(GenericErrorCode.MIN_LENGTH_NOT_MET.priority())
                .isGreaterThan(GenericErrorCode.OUT_OF_RANGE.priority());
    }

    @Test
    void validLengthsPass() {
        assertThat(validator.validate("{\"a\":\"ab\",\"b\":\"abcd\",\"c\":\"abc\",\"d\":\"abc\",\"e\":\"\","
                + "\"tags\":[\"x\"],\"attrs\":{},\"codes\":[1,2]}", Vo.class).isValid()).isTrue();
    }

    @Test
    void tooLongField() {
        assertFailure(validator.validate("{\"a\":\"abcde\"}", Vo.class), MAX, MAX_MSG + "{a}", 400);
    }

    @Test
    void tooShortField() {
        assertFailure(validator.validate("{\"a\":\"x\"}", Vo.class), MIN, MIN_MSG + "{a}", 400);
    }

    @Test
    void bothAtOnceMaxWins() {
        assertFailure(validator.validate("{\"a\":\"x\",\"b\":\"abcdef\"}", Vo.class), MAX, MAX_MSG + "{b}", 400);
    }

    @Test
    void groupingPerCategory() {
        String body = "{\"a\":\"x\",\"b\":\"y\",\"c\":\"z\",\"d\":\"ab\"}";
        assertFailure(validator.validate(body, Vo.class), MIN, MIN_MSG + "{a, b, c, d}", 400);
        body = "{\"a\":\"abcde\",\"b\":\"x\",\"c\":\"abcde\",\"e\":\"abcd\"}";
        assertFailure(validator.validate(body, Vo.class), MAX, MAX_MSG + "{a, c, e}", 400);
    }

    @ParameterizedTest(name = "@Size(min=2,max=4) \"{0}\" -> {1}")
    @CsvSource({"x,E_MIN_LENGTH_NOT_MET", "abcde,E_MAX_LENGTH_EXCEEDED", "abcdefghij,E_MAX_LENGTH_EXCEEDED"})
    void sizeWithBothBoundsDecidesPerValue(String value, String code) {
        assertThat(validator.validate("{\"a\":\"" + value + "\"}", Vo.class).errorCode()).contains(code);
        assertThat(validator.validate("{\"c\":\"" + value + "\"}", Vo.class).errorCode()).contains(code);
    }

    @Test
    void emptyStringIsLengthZeroForSize() {
        assertFailure(validator.validate("{\"a\":\"\"}", Vo.class), MIN, MIN_MSG + "{a}", 400);
        assertThat(validator.validate("{\"e\":\"\",\"d\":\"\"}", Vo.class).isValid()).isTrue();
    }

    @Test
    void collectionsMapsAndArrays() {
        assertFailure(validator.validate("{\"tags\":[]}", Vo.class), MIN, MIN_MSG + "{tags}", 400);
        assertFailure(validator.validate("{\"tags\":[\"a\",\"b\",\"c\"]}", Vo.class), MAX, MAX_MSG + "{tags}", 400);
        assertFailure(validator.validate("{\"attrs\":{\"k\":\"v\",\"k2\":\"v\"}}", Vo.class), MAX, MAX_MSG + "{attrs}", 400);
        assertFailure(validator.validate("{\"codes\":[]}", Vo.class), MIN, MIN_MSG + "{codes}", 400);
        assertFailure(validator.validate("{\"codes\":[1,2,3]}", Vo.class), MAX, MAX_MSG + "{codes}", 400);
    }

    @Test
    void voCodeMappingOverridesLengthCategory() {
        // f is too long, but its VO code is explicitly mapped to MIN (and vice versa)
        assertFailure(TestSupport.jsonValidator(Map.of("VO_LEN", "E_MIN_LENGTH_NOT_MET"), true)
                .validate("{\"f\":\"abcdef\"}", Vo.class), MIN, MIN_MSG + "{f}", 400);
        assertFailure(TestSupport.jsonValidator(Map.of("VO_LEN", "MAX_LENGTH_EXCEEDED"), true)
                .validate("{\"f\":\"a\"}", Vo.class), MAX, MAX_MSG + "{f}", 400);
        // unmapped VO code falls back to the per-value decision
        assertFailure(validator.validate("{\"f\":\"abcdef\"}", Vo.class), MAX, MAX_MSG + "{f}", 400);
    }
}
