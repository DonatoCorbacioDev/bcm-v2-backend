package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a contract history entry is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * when attempting to access or manipulate a {@code ContractHistory} entity
 * that does not exist in the database.
 */
public class ContractHistoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 8338919483573307317L;

    /**
     * Constructs a new {@code ContractHistoryNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public ContractHistoryNotFoundException(String message) {
        super(message);
    }
}
