/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.support;

import java.util.UUID;

/**
 * Fixed, readable identifiers for tests (ADR-0009): {@code uid(7)} is
 * {@code 00000000-0000-0000-0000-000000000007}, so a test can still say
 * "contract 7" and "organization 1" the way it did with numeric ids.
 */
public final class TestIds {

    private TestIds() {
    }

    public static UUID uid(long n) {
        return new UUID(0L, n);
    }
}
