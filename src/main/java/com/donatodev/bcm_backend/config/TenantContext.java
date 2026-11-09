/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.config;

import java.util.UUID;
import com.donatodev.bcm_backend.exception.MissingTenantException;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORG = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(UUID organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    public static UUID get() {
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
    public static UUID require() {
        UUID orgId = CURRENT_ORG.get();
        if (orgId == null) {
            throw new MissingTenantException();
        }
        return orgId;
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }
}
