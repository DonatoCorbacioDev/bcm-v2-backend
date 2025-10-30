package com.donatodev.bcm_backend.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Global exception handler for the entire application.
 * <p>
 * This class intercepts and handles exceptions thrown during request processing,
 * returning a consistent and structured JSON error response via {@link ApiErrorResponse}.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles cases where a contract is not found.
     */
    @ExceptionHandler(ContractNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleContractNotFound(ContractNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a manager is not found.
     */
    @ExceptionHandler(ManagerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleManagerNotFound(ManagerNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a business area is not found.
     */
    @ExceptionHandler(BusinessAreaNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessAreaNotFound(BusinessAreaNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a financial type is not found.
     */
    @ExceptionHandler(FinancialTypeNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFinancialTypeNotFound(FinancialTypeNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a financial value is not found.
     */
    @ExceptionHandler(FinancialValueNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFinancialValueNotFound(FinancialValueNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a user is not found.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a role is not found.
     */
    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRoleNotFound(RoleNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles cases where a contract history record is not found.
     */
    @ExceptionHandler(ContractHistoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleContractHistoryNotFound(ContractHistoryNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles validation errors thrown when using {@code @Valid}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }
    
    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ApiErrorResponse> handleRegistrationException(RegistrationException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
    
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized: " + ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden: " + ex.getMessage());
    }

    /**
     * Builds a structured {@link ApiErrorResponse} with the given status and message.
     *
     * @param status  the HTTP status to return
     * @param message the message describing the error
     * @return a {@code ResponseEntity<ApiErrorResponse>} with proper status and body
     */
    private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, String message) {
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                message,
                LocalDateTime.now()
        );
        return ResponseEntity.status(status).body(response);
    }
}
