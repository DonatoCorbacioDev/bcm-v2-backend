/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

public class NotificationNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
