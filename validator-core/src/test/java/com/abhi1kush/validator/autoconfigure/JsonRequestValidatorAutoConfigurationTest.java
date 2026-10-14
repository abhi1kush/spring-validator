package com.abhi1kush.validator.autoconfigure;

import com.abhi1kush.validator.core.GenericErrorCode;
import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRequestValidatorAutoConfigurationTest {

    record Vo(@NotBlank(message = "ABC_001") String a) {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    ValidationAutoConfiguration.class, JsonRequestValidatorAutoConfiguration.class));

    @Test
    void registersBeansWithPropertiesMapping() {
        runner.withPropertyValues("validator.error-mapping.ABC_001=E_OUT_OF_RANGE").run(ctx -> {
            assertThat(ctx).hasSingleBean(JsonRequestValidator.class)
                    .getBean(ErrorCodeMapper.class).isInstanceOf(PropertiesErrorCodeMapper.class);
            var r = ctx.getBean(JsonRequestValidator.class).validate("{\"a\":\"\"}", Vo.class);
            assertThat(r.errorCode()).contains("E_OUT_OF_RANGE");
        });
    }

    @Test
    void unknownFieldsRejectedByDefaultAndConfigurable() {
        runner.run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                .validate("{\"a\":\"x\",\"b\":1}", Vo.class).errorCode()).contains("E_UNKNOWN_FIELD"));
        runner.withPropertyValues("validator.fail-on-unknown-properties=false")
                .run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                        .validate("{\"a\":\"x\",\"b\":1}", Vo.class).isValid()).isTrue());
    }

    @Test
    void userMapperBeanBacksOff() {
        runner.withBean(ErrorCodeMapper.class, () -> code -> Optional.of(GenericErrorCode.INVALID_VALUE))
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ErrorCodeMapper.class)
                            .doesNotHaveBean(PropertiesErrorCodeMapper.class);
                    assertThat(ctx.getBean(JsonRequestValidator.class).validate("{\"a\":\"\"}", Vo.class)
                            .errorCode()).contains("E_INVALID_VALUE");
                });
    }

    @Test
    void invalidMappingFailsStartup() {
        runner.withPropertyValues("validator.error-mapping.ABC_001=E_TYPO")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void worksWithoutJacksonOrValidationAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JsonRequestValidatorAutoConfiguration.class))
                .run(ctx -> assertThat(ctx.getBean(JsonRequestValidator.class)
                        .validate("{\"a\":\"\"}", Vo.class).errorCode()).contains("E_MANDATORY_MISSING"));
    }
}
