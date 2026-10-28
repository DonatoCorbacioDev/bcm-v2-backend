/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * A registration confirmation that can't proceed: wrong, expired or unknown
 * code. Extends {@link IllegalArgumentException} so it answers 400 like any
 * other invalid input; it exists as its own type only so the confirm
 * transaction can commit the failed-attempt counter instead of rolling it back.
 */
public class RegistrationCodeException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public RegistrationCodeException(String message) {
        super(message);
    }
}
