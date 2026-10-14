package com.abhi1kush.api.web;

/**
 * JSON error body returned on validation failure.
 *
 * @param errorCode        generic error code
 * @param errorDescription human readable description
 */
public record ErrorResponse(String errorCode, String errorDescription) {
}
