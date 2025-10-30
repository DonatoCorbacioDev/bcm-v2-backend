package com.donatodev.bcm_backend.exception;

public class AccountNotVerifiedException extends RuntimeException {
    /**
	 * 
	 */
	private static final long serialVersionUID = -5538732693088684767L;

	public AccountNotVerifiedException(String message) {
        super(message);
    }
}
