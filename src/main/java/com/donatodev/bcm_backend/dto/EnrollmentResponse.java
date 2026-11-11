/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.List;

/** The session opened by a completed 2FA setup, and the recovery codes, shown once. */
public record EnrollmentResponse(String token, List<String> recoveryCodes) {}
