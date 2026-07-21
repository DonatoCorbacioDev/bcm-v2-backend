package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a budget is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code Budget} entity with the given identifier does not exist.
 */
public class BudgetNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new {@code BudgetNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public BudgetNotFoundException(String message) {
        super(message);
    }
}
