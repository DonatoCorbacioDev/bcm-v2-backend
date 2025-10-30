package com.donatodev.bcm_backend.exception;

import java.time.LocalDateTime;

/**
 * Represents a structured JSON error response returned by the API.
 * <p>
 * This record is used to encapsulate the HTTP status code, error message,
 * and the timestamp at which the error occurred.
 * It is typically returned by the {@link GlobalExceptionHandler} class
 * when handling exceptions thrown during request processing.
 *
 * @param status    the HTTP status code (e.g., 404, 500)
 * @param message   a human-readable error message
 * @param timestamp the exact time when the error was generated
 */
public record ApiErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {}
