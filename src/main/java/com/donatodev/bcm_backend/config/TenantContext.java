/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import com.donatodev.bcm_backend.exception.MissingTenantException;

public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORG = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    public static Long get() {
        return CURRENT_ORG.get();
    }

    /**
     * The current organization, for code that reads or writes tenant data:
     * without one the request is refused, never widened to every
     * organization. Background jobs, which run without a tenant on purpose,
     * use {@link #get()} and pass the organization explicitly.
     *
     * @throws MissingTenantException when no organization is set
     */
    public static Long require() {
        Long orgId = CURRENT_ORG.get();
        if (orgId == null) {
            throw new MissingTenantException();
        }
        return orgId;
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
