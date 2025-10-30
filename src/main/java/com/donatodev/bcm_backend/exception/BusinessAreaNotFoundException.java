package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a business area is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code BusinessArea} entity with the given identifier does not exist.
 */
public class BusinessAreaNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -3000662941974946453L;

    /**
     * Constructs a new {@code BusinessAreaNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public BusinessAreaNotFoundException(String message) {
        super(message);
    }
}
