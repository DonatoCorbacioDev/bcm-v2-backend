package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a financial value is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code FinancialValue} entity with the specified identifier does not exist.
 */
public class FinancialValueNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 6195724680774759473L;

    /**
     * Constructs a new {@code FinancialValueNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public FinancialValueNotFoundException(String message) {
        super(message);
    }
}
