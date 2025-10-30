package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a user is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code Users} entity with the specified identifier does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -8882854227659994560L;

    /**
     * Constructs a new {@code UserNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}
