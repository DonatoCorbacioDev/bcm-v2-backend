/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.Users;

/**
 * Who must use two-factor authentication (OWASP API2): every ADMIN, since an
 * admin account reaches every contract, user and payment detail of its
 * organization. A stolen password alone must not open it.
 *
 * <p>{@code app.security.two-factor-exempt-usernames} lists the accounts that
 * may skip it. In production that is only the public demo's internal admin,
 * which the nightly reset script signs in as with a password: an organization
 * of made-up data whose password lives only on the server.
 */
@Component
public class TwoFactorPolicy {

    private static final String ADMIN = "ADMIN";

    private final Set<String> exemptUsernames;

    public TwoFactorPolicy(@Value("${app.security.two-factor-exempt-usernames:}") String exemptUsernames) {
        this.exemptUsernames = Arrays.stream(exemptUsernames.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether this account must have two-factor authentication turned on. */
    public boolean isRequired(Users user) {
        return user.getRole() != null && ADMIN.equals(user.getRole().getRole())
                && !exemptUsernames.contains(user.getUsername());
    }

    /** Whether this account must set two-factor authentication up before it gets a session. */
    public boolean blocksSession(Users user) {
        return isRequired(user) && !user.isTotpEnabled();
    }
}
