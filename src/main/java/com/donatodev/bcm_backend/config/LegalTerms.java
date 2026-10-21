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

    private LegalTerms() {
    }
}
