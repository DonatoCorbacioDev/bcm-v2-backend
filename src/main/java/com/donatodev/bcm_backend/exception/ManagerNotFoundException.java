package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a manager is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code Manager} entity with the specified identifier does not exist.
 */
public class ManagerNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 8199975191532092195L;

    /**
     * Constructs a new {@code ManagerNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public ManagerNotFoundException(String message) {
        super(message);
    }
}
