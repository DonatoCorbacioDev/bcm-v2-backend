/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.exception;

/**
 * A change was made from an out-of-date copy: the row's version (JPA
 * {@code @Version}, V61) moved on since the caller read it, because someone
 * else changed it meanwhile. Refused rather than applied, which would silently
 * undo that other change (OWASP Database Security, concurrent updates).
 * Answers 409, as does the same conflict detected by JPA itself.
 */
public class StaleDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String MESSAGE =
            "Il dato è stato modificato da un altro utente nel frattempo: ricarica la pagina e riprova";

    public StaleDataException() {
        super(MESSAGE);
    }

    /**
     * Refuses a change made from version {@code sent} of a row now at {@code current}.
     * A null {@code sent} (a client that does not send it) is let through:
     * overlapping requests are still caught by JPA at the write.
     */
    public static void check(Long sent, long current) {
        if (sent != null && sent != current) {
            throw new StaleDataException();
        }
    }
}
