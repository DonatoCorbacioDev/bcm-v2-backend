package com.donatodev.bcm_backend.exception;

public class EmailSendingException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = -8549109426731463081L;

	public EmailSendingException(String message, Throwable cause) {
        super(message, cause);
    }
}
