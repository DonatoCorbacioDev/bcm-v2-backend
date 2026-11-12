/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Page requests built from client parameters (OWASP API4): a page never holds
 * more than {@link #MAX_PAGE_SIZE} rows, so {@code ?size=1000000} can't load a
 * whole table in one request. Out-of-range values are refused (400), not
 * silently clamped, so a client sees its mistake.
 */
public final class PageLimits {

    // The frontend asks for 1000 contracts to fill its contract pickers: the
    // largest page an organization of this size needs, and still bounded.
    public static final int MAX_PAGE_SIZE = 1000;

    private PageLimits() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new IllegalArgumentException("Il numero di pagina non può essere negativo");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("La dimensione della pagina deve essere tra 1 e " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
