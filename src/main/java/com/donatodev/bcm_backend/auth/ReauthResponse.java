/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.auth;

/**
 * A re-authentication token, valid five minutes, to send back in X-Reauth-Token
 * with the operation that asked for it.
 */
public record ReauthResponse(String reauthToken) {
}
