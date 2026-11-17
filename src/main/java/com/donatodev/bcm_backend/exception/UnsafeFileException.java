/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * An uploaded file refused as dangerous (OWASP File Upload Cheat Sheet): the
 * antivirus recognised it, or it is a PDF carrying active content. Answered
 * with 422 and the given (Italian) message; the refusal is recorded as a
 * security event by whoever throws it.
 */
public class UnsafeFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsafeFileException(String message) {
        super(message);
    }
}
