package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a role is not found in the system.
 * <p>
 * This exception is typically used in service or controller layers
 * to indicate that a {@code Role} entity with the specified identifier does not exist.
 */
public class RoleNotFoundException extends RuntimeException {

    private static final long serialVersionUID = -8906684857405064285L;

    /**
     * Constructs a new {@code RoleNotFoundException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public RoleNotFoundException(String message) {
        super(message);
    }
}
