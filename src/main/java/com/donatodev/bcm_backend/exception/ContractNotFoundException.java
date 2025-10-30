package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a contract is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code Contracts} entity with the specified identifier does not exist.
 */
public class ContractNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -6046094739840563322L;

    /**
     * Constructs a new {@code ContractNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public ContractNotFoundException(String message) {
        super(message);
    }
}
