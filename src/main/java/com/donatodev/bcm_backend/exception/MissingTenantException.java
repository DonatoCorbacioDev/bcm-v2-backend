/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * A request reached tenant data without an organization to scope it to.
 * Answered as any other denied access (403): failing closed instead of
 * looking across every organization (OWASP API1, BOLA).
 */
public class MissingTenantException extends AccessDeniedException {

    private static final long serialVersionUID = 1L;

    public MissingTenantException() {
        super("organizzazione non definita");
    }
}
