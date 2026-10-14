package com.abhi1kush.validator.annotation;

import com.abhi1kush.validator.TestSupport;
import com.abhi1kush.validator.core.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalDateValidatorTest {

    record Vo(@OptionalDate String date) {
    }

    record DmyVo(@OptionalDate(pattern = "dd/MM/uuuu") String date) {
    }

    record PastVo(@OptionalDate(when = OptionalDate.Bound.PAST) String date) {
    }

    record PastOrPresentVo(@OptionalDate(when = OptionalDate.Bound.PAST_OR_PRESENT) String date) {
    }

    record FutureVo(@OptionalDate(when = OptionalDate.Bound.FUTURE) String date) {
    }

    record FutureOrPresentVo(@OptionalDate(when = OptionalDate.Bound.FUTURE_OR_PRESENT) String date) {
    }

    private static boolean valid(Object vo) {
        return TestSupport.VALIDATOR.validate(vo).isEmpty();
    }

    @Test
    void nullIsValid() {
        assertThat(valid(new Vo(null))).isTrue();
    }

    @Test
    void emptyIsValid() {
        assertThat(valid(new Vo(""))).isTrue();
    }

    @Test
    void absentFieldIsValid() {
        assertThat(TestSupport.jsonValidator(Map.of(), true).validate("{}", Vo.class).isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-29", "2023-12-31", "0001-01-01", "2026-10-14"})
    void validDatesPass(String date) {
        assertThat(valid(new Vo(date))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-02-30", "2023-02-29", "2024-13-01", "2024-1-01", "2024-01-1",
            "20240101", "2024/01/01", "abc", " ", " 2024-01-01", "2024-01-01T00:00"})
    void invalidDatesFailStrictly(String date) {
        assertThat(valid(new Vo(date))).isFalse();
    }

    @Test
    void customPatternIsHonoured() {
        assertThat(valid(new DmyVo("29/02/2024"))).isTrue();
        assertThat(valid(new DmyVo("30/02/2024"))).isFalse();
        assertThat(valid(new DmyVo("2024-02-29"))).isFalse();
    }

    @Test
    void pastAndFutureBounds() {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String tomorrow = LocalDate.now().plusDays(1).toString();

        assertThat(valid(new PastVo(yesterday))).isTrue();
        assertThat(valid(new PastVo(today))).isFalse();
        assertThat(valid(new PastVo(tomorrow))).isFalse();

        assertThat(valid(new PastOrPresentVo(today))).isTrue();
        assertThat(valid(new PastOrPresentVo(tomorrow))).isFalse();

        assertThat(valid(new FutureVo(tomorrow))).isTrue();
        assertThat(valid(new FutureVo(today))).isFalse();
        assertThat(valid(new FutureVo(yesterday))).isFalse();

        assertThat(valid(new FutureOrPresentVo(today))).isTrue();
        assertThat(valid(new FutureOrPresentVo(yesterday))).isFalse();
    }

    @Test
    void boundsIgnoreNullAndEmpty() {
        assertThat(valid(new PastVo(null))).isTrue();
        assertThat(valid(new FutureVo(""))).isTrue();
    }

    @Test
    void invalidDateMapsToInvalidDate() {
        ValidationResult<Vo> r = TestSupport.jsonValidator(Map.of(), true)
                .validate("{\"date\":\"2024-02-30\"}", Vo.class);
        assertThat(r.errorCode()).contains("E_INVALID_DATE");
        assertThat(r.errorDescription()).contains("Invalid date for fields: {date}");
    }
}
