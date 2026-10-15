package com.abhi1kush.app;

import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.abhi1kush.validator.spring.JsonRequestValidatorProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** The app opts in explicitly; its own ObjectMapper and Validator stay Spring Boot's defaults. */
@SpringBootTest
class SpringValidatorApplicationTests {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    Environment env;

    @Test
    void contextLoadsAndNamespacedMappingIsBound() {
        assertThat(ctx.getBeansOfType(JsonRequestValidator.class)).containsOnlyKeys("jsonRequestValidator");
        assertThat(ctx.getBeansOfType(ErrorCodeMapper.class)).isEmpty();
        assertThat(ctx.getBeansOfType(JsonRequestValidatorProperties.class)).isEmpty();
        JsonRequestValidatorProperties props =
                JsonRequestValidatorProperties.bind(env, JsonRequestValidatorProperties.PREFIX);
        assertThat(props.errorMapping()).containsEntry("USR_001", "E_MANDATORY_MISSING")
                .containsEntry("ADR_002", "E_INVALID_FORMAT");
    }

    @Test
    void sharedBeansKeepSpringBootDefaults() {
        assertThat(ctx.getBeansOfType(ObjectMapper.class)).hasSize(1);
        // Spring Boot's own default (lenient) is untouched although the library rejects unknown fields
        assertThat(ctx.getBean(ObjectMapper.class).isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(ctx.getBeansOfType(Validator.class)).containsOnlyKeys("defaultValidator");
    }
}
