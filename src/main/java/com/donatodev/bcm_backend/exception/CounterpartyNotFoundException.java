package com.donatodev.bcm_backend.exception;

/**
 * Exception thrown when a counterparty is not found in the system.
 */
public class CounterpartyNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CounterpartyNotFoundException(String message) {
        super(message);
    }
}
