/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

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
