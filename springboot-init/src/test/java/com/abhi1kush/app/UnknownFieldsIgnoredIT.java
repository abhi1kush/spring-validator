package com.abhi1kush.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the configurable unknown-fields policy end to end. */
@SpringBootTest(properties = "validator.fail-on-unknown-properties=false")
@AutoConfigureMockMvc
class UnknownFieldsIgnoredIT {

    @Autowired
    MockMvc mvc;

    @Test
    void unknownFieldIgnored() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(ApiEndpointsIT.VALID_USER.replace("\"age\"", "\"role\":\"admin\",\"age\"")))
                .andExpect(status().isCreated());
    }
}
