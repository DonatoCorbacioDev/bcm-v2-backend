/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

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
