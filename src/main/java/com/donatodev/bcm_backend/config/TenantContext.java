/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORG = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    public static Long get() {
        return CURRENT_ORG.get();
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
