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
