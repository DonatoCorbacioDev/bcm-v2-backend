package com.donatodev.bcm_backend.exception;

public class AccessDeniedException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = 4774326966207178504L;

	public AccessDeniedException(String message) {
        super(message);
    }
}
