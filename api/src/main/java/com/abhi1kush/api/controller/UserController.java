package com.abhi1kush.api.controller;

import com.abhi1kush.api.vo.UserVO;
import com.abhi1kush.api.web.ValidationResponses;
import com.abhi1kush.validator.core.JsonRequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sample user endpoint; the raw body is validated with {@link JsonRequestValidator}. */
@RestController
@RequestMapping(path = "/api/users", produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final JsonRequestValidator validator;

    /** @param validator request validator (constructor injection) */
    public UserController(JsonRequestValidator validator) {
        this.validator = validator;
    }

    /**
     * @param body raw JSON body (optional so that an empty body yields {@code E_EMPTY_BODY})
     * @return 201 with the user, or the mapped error
     */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) String body) {
        return ValidationResponses.toResponse(validator.validate(body, UserVO.class), HttpStatus.CREATED);
    }
}
