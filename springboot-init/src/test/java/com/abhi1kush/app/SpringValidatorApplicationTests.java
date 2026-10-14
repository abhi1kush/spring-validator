package com.abhi1kush.app;

import com.abhi1kush.validator.core.JsonRequestValidator;
import com.abhi1kush.validator.mapping.ErrorCodeMapper;
import com.abhi1kush.validator.core.GenericErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Context loads and application.yml mapping is bound. */
@SpringBootTest
class SpringValidatorApplicationTests {

    @Autowired
    JsonRequestValidator validator;

    @Autowired
    ErrorCodeMapper mapper;

    @Test
    void contextLoadsAndYamlMappingIsBound() {
        assertThat(validator).isNotNull();
        assertThat(mapper.map("USR_001")).contains(GenericErrorCode.MANDATORY_MISSING);
        assertThat(mapper.map("ADR_002")).contains(GenericErrorCode.INVALID_FORMAT);
        assertThat(mapper.map("NOPE_001")).isEmpty();
    }
}
