/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.support;

import java.util.UUID;

import com.donatodev.bcm_backend.auth.ReauthenticationService;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.jwt.JwtUtils;

/**
 * A fresh re-authentication for tests of the operations that ask for one
 * (ReauthenticationService): what POST /auth/reauth would return to this user,
 * without the password step, which ReauthenticationTest covers.
 */
public final class TestReauth {

    public static final String HEADER = ReauthenticationService.HEADER;

    private TestReauth() {
    }

    /** A re-authentication token of {@code username} in {@code orgId}. */
    public static String token(JwtUtils jwtUtils, String username, UUID orgId) {
        Users user = Users.builder().username(username).organization(Organization.builder().id(orgId).build()).build();
        return jwtUtils.generateReauthToken(user);
    }
}
