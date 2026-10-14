package com.abhi1kush.api.controller;

import com.abhi1kush.api.vo.OrderVO;
import com.abhi1kush.api.web.ValidationResponses;
import com.abhi1kush.validator.core.JsonRequestValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sample order endpoint; the raw body is validated with {@link JsonRequestValidator}. */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderController {

    private final JsonRequestValidator validator;

    /** @param validator request validator (constructor injection) */
    public OrderController(JsonRequestValidator validator) {
        this.validator = validator;
    }

    /**
     * @param body raw JSON body (optional so that an empty body yields {@code E_EMPTY_BODY})
     * @return 201 with the order, or the mapped error
     */
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody(required = false) String body) {
        return ValidationResponses.toResponse(validator.validate(body, OrderVO.class), HttpStatus.CREATED);
    }
}
