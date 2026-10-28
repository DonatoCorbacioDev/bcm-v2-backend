/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

/**
 * The Terms of Service version a new registration must accept. Must equal
 * {@code TERMS_VERSION} in the frontend's {@code lib/legal.ts}, which is the
 * text published at /termini: a registration carrying any other version is
 * rejected, so a stored acceptance always points at a known text.
 */
public final class LegalTerms {

    public static final String CURRENT_VERSION = "2026-10-21";

    /**
     * Sections of /termini approved specifically under arts. 1341-1342 c.c.,
     * stored with every acceptance. Must match {@code ONEROUS_CLAUSES} in
     * {@code lib/legal.ts}.
     */
    public static final String ONEROUS_CLAUSES = "5, 9, 11, 12, 13, 15";

    private LegalTerms() {
    }
}
