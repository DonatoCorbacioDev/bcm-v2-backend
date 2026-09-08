/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

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