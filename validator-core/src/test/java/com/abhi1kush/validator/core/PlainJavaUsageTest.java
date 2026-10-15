package com.abhi1kush.validator.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The builder works without any Spring class and never touches the caller's ObjectMapper. */
class PlainJavaUsageTest {

    record Vo(@NotBlank(message = "P_001") String firstName) {
    }

    @Test
    void builderWithoutSpring() {
        try (JsonRequestValidator v = JsonRequestValidator.builder()
                .errorMapping(Map.of("P_001", "E_INVALID_VALUE")).build()) {
            assertThat(v.validate("{\"firstName\":\"a\"}", Vo.class).isValid()).isTrue();
            assertThat(v.validate("{}", Vo.class).errorCode()).contains("E_INVALID_VALUE");
            assertThat(v.validate("{\"x\":1}", Vo.class).errorCode()).contains("E_UNKNOWN_FIELD");
        }
    }

    @Test
    void callerMapperIsCopiedAndNeverModified() {
        ObjectMapper caller = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (JsonRequestValidator v = JsonRequestValidator.builder().objectMapper(caller).build()) {
            assertThat(v.validate("{\"first_name\":\"a\",\"x\":1}", Vo.class).errorCode()).contains("E_UNKNOWN_FIELD");
            assertThat(caller.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
            caller.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE);
            assertThat(v.validate("{}", Vo.class).errorDescription()).contains("Mandatory fields missing: {first_name}");
        }
    }

    @Test
    void independentInstancesWithDifferentMappings() {
        try (JsonRequestValidator a = JsonRequestValidator.builder().errorMapping(Map.of("P_001", "E_OUT_OF_RANGE")).build();
             JsonRequestValidator b = JsonRequestValidator.builder().errorMapping(Map.of("P_001", "E_INVALID_FORMAT")).build()) {
            assertThat(a.validate("{}", Vo.class).errorCode()).contains("E_OUT_OF_RANGE");
            assertThat(b.validate("{}", Vo.class).errorCode()).contains("E_INVALID_FORMAT");
            a.close();
            assertThat(b.validate("{}", Vo.class).errorCode()).contains("E_INVALID_FORMAT");
        }
    }

    @Test
    void closeIsIdempotentAndValidateFailsAfterwards() {
        JsonRequestValidator v = JsonRequestValidator.builder().build();
        v.close();
        v.close();
        assertThatThrownBy(() -> v.validate("{}", Vo.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void errorCodeMapperTakesPrecedenceOverMapping() {
        try (JsonRequestValidator v = JsonRequestValidator.builder()
                .errorMapping(Map.of("P_001", "E_OUT_OF_RANGE"))
                .errorCodeMapper(code -> java.util.Optional.of(GenericErrorCode.INVALID_FORMAT)).build()) {
            assertThat(v.validate("{}", Vo.class).errorCode()).contains("E_INVALID_FORMAT");
        }
    }
}
