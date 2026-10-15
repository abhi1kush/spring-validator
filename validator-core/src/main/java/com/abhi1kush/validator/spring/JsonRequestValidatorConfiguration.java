package com.abhi1kush.validator.spring;

import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Imported only by {@link EnableJsonRequestValidator}. It is deliberately <b>not</b> annotated with
 * {@code @Configuration} so that component scanning never registers it by accident.
 *
 * <p>Registers exactly one bean, {@link JsonRequestValidator} (bean name {@code jsonRequestValidator}), besides this
 * imported class itself. {@code spring-validator.*} is bound with a {@link org.springframework.boot.context.properties.bind.Binder}
 * inside the bean method, so not even Spring Boot's configuration-properties infrastructure beans are added.
 * The application's {@code ObjectMapper}, if unique, is only used as a template and copied; the application's
 * Jakarta {@code Validator} is never used. No {@code @Primary}, no generic-typed beans.
 */
public class JsonRequestValidatorConfiguration {

    /**
     * @param environment     source of {@code spring-validator.*}
     * @param objectMapper    application mapper, copied if exactly one exists
     * @param errorCodeMapper optional custom mapper (library type); overrides {@code error-mapping}
     * @return the validator; Spring calls {@link JsonRequestValidator#close()} on shutdown
     */
    @Bean
    public JsonRequestValidator jsonRequestValidator(Environment environment,
                                                     ObjectProvider<ObjectMapper> objectMapper,
                                                     ObjectProvider<ErrorCodeMapper> errorCodeMapper) {
        JsonRequestValidatorProperties properties =
                JsonRequestValidatorProperties.bind(environment, JsonRequestValidatorProperties.PREFIX);
        JsonRequestValidator.Builder builder = properties.toBuilder();
        if (properties.useApplicationObjectMapper()) {
            objectMapper.ifUnique(builder::objectMapper);
        }
        errorCodeMapper.ifUnique(builder::errorCodeMapper);
        return builder.build();
    }
}
