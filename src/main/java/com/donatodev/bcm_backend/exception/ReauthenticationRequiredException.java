/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * An operation that needs the signed-in user to enter their password again
 * first (ReauthenticationService). Answered 428 Precondition Required, a status
 * nothing else in the API uses, so the frontend can ask for the password and
 * repeat the request.
 */
public class ReauthenticationRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReauthenticationRequiredException(String message) {
        super(message);
    }
}
