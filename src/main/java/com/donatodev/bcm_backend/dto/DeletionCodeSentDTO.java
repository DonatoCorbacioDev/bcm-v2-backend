/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * @param expiresInMinutes      how long the emailed code stays valid
 * @param resendCooldownSeconds wait before asking for another code
 */
public record DeletionCodeSentDTO(int expiresInMinutes, int resendCooldownSeconds) {}
