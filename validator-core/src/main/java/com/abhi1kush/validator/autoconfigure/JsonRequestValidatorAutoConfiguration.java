package com.abhi1kush.validator.autoconfigure;

import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.abhi1kush.validator.mapping.PropertiesErrorCodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers a {@link JsonRequestValidator} using the application's
 * {@link ObjectMapper} and Jakarta {@link Validator}. Add {@code validator-core} as a dependency
 * and inject {@link JsonRequestValidator}; override {@link ErrorCodeMapper} by declaring your own bean.
 */
@AutoConfiguration(after = {JacksonAutoConfiguration.class, ValidationAutoConfiguration.class})
@ConditionalOnClass({ObjectMapper.class, Validator.class})
@EnableConfigurationProperties(ValidatorProperties.class)
public class JsonRequestValidatorAutoConfiguration {

    /**
     * @param properties validator properties
     * @return mapper backed by {@code validator.error-mapping}
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorCodeMapper errorCodeMapper(ValidatorProperties properties) {
        return new PropertiesErrorCodeMapper(properties.errorMapping());
    }

    /**
     * @param objectMapper    application object mapper (a plain one is used if absent)
     * @param validator       application validator (the default factory is used if absent)
     * @param errorCodeMapper code mapper
     * @param properties      validator properties
     * @return the request validator
     */
    @Bean
    @ConditionalOnMissingBean
    public JsonRequestValidator jsonRequestValidator(ObjectProvider<ObjectMapper> objectMapper,
                                                     ObjectProvider<Validator> validator,
                                                     ErrorCodeMapper errorCodeMapper,
                                                     ValidatorProperties properties) {
        return new JsonRequestValidator(
                objectMapper.getIfAvailable(ObjectMapper::new),
                validator.getIfAvailable(() -> Validation.buildDefaultValidatorFactory().getValidator()),
                errorCodeMapper,
                properties.failOnUnknownProperties());
    }
}
