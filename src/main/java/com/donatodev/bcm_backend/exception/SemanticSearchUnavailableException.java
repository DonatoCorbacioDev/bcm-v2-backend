/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when semantic search cannot generate a query embedding
 * because the underlying ML model (Ollama, via Spring AI) is unreachable or
 * not pulled.
 */
public class SemanticSearchUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SemanticSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
