/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * An organization-deletion confirmation that can't proceed: wrong, expired or
 * missing code. Like {@link RegistrationCodeException} it answers 400, and is
 * its own type so the confirm transaction can commit the failed-attempt
 * counter instead of rolling it back.
 */
public class DeletionCodeException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public DeletionCodeException(String message) {
        super(message);
    }
}
