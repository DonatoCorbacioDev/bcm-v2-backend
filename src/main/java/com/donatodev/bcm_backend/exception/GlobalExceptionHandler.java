/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Global exception handler for the entire application.
 * <p>
 * This class intercepts and handles exceptions thrown during request processing,
 * returning a consistent and structured JSON error response via {@link ApiErrorResponse}.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    static final String INTERNAL_ERROR = "Errore interno del server";

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
     * Handles cases where a counterparty is not found.
     */
    @ExceptionHandler(CounterpartyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCounterpartyNotFound(CounterpartyNotFoundException ex) {
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
     * Handles cases where a budget is not found.
     */
    @ExceptionHandler(BudgetNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBudgetNotFound(BudgetNotFoundException ex) {
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
                .orElse("Errore di validazione");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }
    
    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleRefreshTokenException(RefreshTokenException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(OrganizationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationNotFound(OrganizationNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotificationNotFound(NotificationNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SemanticSearchUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSemanticSearchUnavailable(SemanticSearchUnavailableException ex) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Ricerca semantica temporaneamente non disponibile");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Non autorizzato: " + ex.getMessage());
    }

    /** A per-user limit on a costly operation (OWASP API4). */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Accesso negato: " + ex.getMessage());
    }

    /**
     * Handles cases where an uploaded invoice duplicates one already on file.
     */
    @ExceptionHandler(DuplicateInvoiceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateInvoice(DuplicateInvoiceException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles cases where an invoice's supplier IBAN doesn't match the one
     * already verified for that counterparty.
     */
    @ExceptionHandler(IbanMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleIbanMismatch(IbanMismatchException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles cases where an organization has reached the resource limit of
     * its current subscription tier.
     */
    @ExceptionHandler(OrganizationDeletionConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleOrganizationDeletionConflict(OrganizationDeletionConflictException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TierLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTierLimitExceeded(TierLimitExceededException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflitto: esiste già un record con lo stesso valore univoco");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * A path or query parameter of the wrong type, such as an identifier that is
     * not one ({@code /contracts/abc}). Answered here: left to Spring, the error
     * is forwarded to {@code /error}, which the security chain answers with 401.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Parametro non valido: " + ex.getName());
    }

    /** A required query parameter left out; same reason as above for answering it here. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Parametro obbligatorio mancante: " + ex.getParameterName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Corpo della richiesta malformato o illeggibile");
    }

    /**
     * Answers a {@link ResponseStatusException} directly, with its own status.
     * Left unhandled, Spring forwards it to {@code /error}, which the security
     * chain doesn't permit and which carries no JWT on that internal dispatch —
     * so every one of them (a 404, a 503, a 400) reached the client as a 401,
     * and the frontend treats any 401 as an expired session and logs out.
     * MockMvc never performs that error dispatch, which is why only a request
     * against the deployed app showed it.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return buildErrorResponse(status, message);
    }

    /**
     * Answers every exception no handler above names, so none reaches the
     * servlet container's error dispatch to {@code /error}: that dispatch carries
     * no JWT, the security chain answered it 401, and the frontend logs out on
     * any 401 — a server bug signed the user out (OWASP API8). Spring's own
     * exceptions keep the status they carry (unknown path 404, wrong method 405,
     * file too large 413...); anything else is a 500 whose detail goes to the
     * log only, never to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        if (ex instanceof AsyncRequestNotUsableException) {
            // The client went away (a download cut short): there is no one to answer.
            return null;
        }
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            if (status == HttpStatus.NOT_ACCEPTABLE) {
                // The client accepts no JSON: a JSON body could not be written.
                return ResponseEntity.status(status).headers(errorResponse.getHeaders()).build();
            }
            ApiErrorResponse body = new ApiErrorResponse(status.value(), clientMessage(status),
                    LocalDateTime.now(ZoneId.systemDefault()));
            return ResponseEntity.status(status).headers(errorResponse.getHeaders()).body(body);
        }
        logger.error("Unhandled exception", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR);
    }

    private static String clientMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Risorsa non trovata";
            case METHOD_NOT_ALLOWED -> "Metodo non consentito";
            case CONTENT_TOO_LARGE -> "File troppo grande";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato della richiesta non supportato";
            default -> status.is5xxServerError() ? INTERNAL_ERROR : "Richiesta non valida";
        };
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
                LocalDateTime.now(ZoneId.systemDefault())
        );
        return ResponseEntity.status(status).body(response);
    }
}
