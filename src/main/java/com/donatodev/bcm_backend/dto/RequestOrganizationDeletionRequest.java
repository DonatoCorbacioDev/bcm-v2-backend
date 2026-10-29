/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * First step of an organization deletion. The password guards against a
 * stolen session, the retyped name against a click by mistake.
 *
 * @param password         the requesting admin's current password
 * @param organizationName the organization's name, typed exactly
 */
public record RequestOrganizationDeletionRequest(

        @NotBlank(message = "Password obbligatoria")
        String password,

        @NotBlank(message = "Nome dell'organizzazione obbligatorio")
        String organizationName
) {}
