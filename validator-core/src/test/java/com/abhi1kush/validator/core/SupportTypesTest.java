package com.abhi1kush.validator.core;

import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportTypesTest {

    @Test
    void pathComparatorOrdersDigitsNumerically() {
        List<String> paths = new ArrayList<>(List.of("items[10].qty", "name", "items[2].qty", "address.zip",
                "address.city", "items[2].price", "items[01].qty"));
        paths.sort(PropertyPathComparator.INSTANCE);
        assertThat(paths).containsExactly("address.city", "address.zip", "items[01].qty", "items[2].price",
                "items[2].qty", "items[10].qty", "name");
    }

    @Test
    void prioritiesAreUnique() {
        assertThat(Arrays.stream(GenericErrorCode.values()).map(GenericErrorCode::priority).distinct().count())
                .isEqualTo(GenericErrorCode.values().length);
    }

    @Test
    void fromValueAcceptsCodeOrName() {
        assertThat(GenericErrorCode.fromValue("E_MANDATORY_MISSING")).contains(GenericErrorCode.MANDATORY_MISSING);
        assertThat(GenericErrorCode.fromValue("mandatory_missing")).contains(GenericErrorCode.MANDATORY_MISSING);
        assertThat(GenericErrorCode.fromValue("nope")).isEmpty();
    }

    @Test
    void propertiesMapperFailsFastOnUnknownGenericCode() {
        assertThatThrownBy(() -> new PropertiesErrorCodeMapper(Map.of("USR_001", "E_TYPO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("E_TYPO");
    }

    @Test
    void validationResultEnforcesInvariant() {
        assertThatThrownBy(() -> new ValidationResult<>(Optional.of("x"), Optional.of("E"), Optional.of("d"),
                Optional.of(400))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationResult<>(Optional.empty(), Optional.of("E"), Optional.empty(),
                Optional.of(400))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ValidationResult.success("x").isValid()).isTrue();
        assertThat(ValidationResult.failure("E", "d", 400).isValid()).isFalse();
    }

    @Test
    void voCodePattern() {
        assertThat(JsonRequestValidator.VO_CODE_PATTERN.matcher("USR_001").matches()).isTrue();
        assertThat(JsonRequestValidator.VO_CODE_PATTERN.matcher("ORDER_ITEM_01").matches()).isTrue();
        assertThat(JsonRequestValidator.VO_CODE_PATTERN.matcher("must not be blank").matches()).isFalse();
        assertThat(JsonRequestValidator.VO_CODE_PATTERN.matcher("{jakarta.validation.constraints.NotBlank.message}")
                .matches()).isFalse();
        assertThat(JsonRequestValidator.VO_CODE_PATTERN.matcher("USR").matches()).isFalse();
    }
}
