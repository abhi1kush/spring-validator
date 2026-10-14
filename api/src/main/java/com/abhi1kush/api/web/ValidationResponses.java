package com.abhi1kush.api.web;

import com.abhi1kush.validator.core.ValidationResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Converts a {@link ValidationResult} into an HTTP response. */
public final class ValidationResponses {

    private ValidationResponses() {
    }

    /**
     * @param result        validation result
     * @param successStatus status used when the VO is valid
     * @return the VO with {@code successStatus}, or {@link ErrorResponse} with the mapped status
     */
    public static ResponseEntity<Object> toResponse(ValidationResult<?> result, HttpStatus successStatus) {
        if (result.isValid()) {
            return ResponseEntity.status(successStatus).body(result.vo().orElseThrow());
        }
        return ResponseEntity.status(result.httpStatusCode().orElseThrow())
                .body(new ErrorResponse(result.errorCode().orElseThrow(), result.errorDescription().orElseThrow()));
    }
}
