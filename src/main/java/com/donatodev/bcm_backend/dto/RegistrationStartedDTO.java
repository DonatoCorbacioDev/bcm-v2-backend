/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

/**
 * Answer to a registration request: a code has been emailed, and the client
 * must send it back to {@code /organizations/register/confirm}.
 *
 * @param registrationId          opaque id of the pending registration
 * @param expiresInMinutes        how long the emailed code stays valid
 * @param resendCooldownSeconds   wait before another code can be requested
 */
public record RegistrationStartedDTO(String registrationId, int expiresInMinutes, int resendCooldownSeconds) {}
