package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a financial type is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code FinancialType} entity with the specified identifier does not exist.
 */
public class FinancialTypeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -5450318331144864930L;

    /**
     * Constructs a new {@code FinancialTypeNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public FinancialTypeNotFoundException(String message) {
        super(message);
    }
}
