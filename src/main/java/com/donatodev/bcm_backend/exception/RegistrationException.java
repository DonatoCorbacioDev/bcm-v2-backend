package com.donatodev.bcm_backend.exception;

public class RegistrationException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = -9215755254194103157L;

	public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}