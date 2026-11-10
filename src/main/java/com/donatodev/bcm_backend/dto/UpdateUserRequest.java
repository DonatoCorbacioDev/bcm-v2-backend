/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.donatodev.bcm_backend.validation.ValidPassword;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(min = 4, max = 30, message = "Lo username deve avere tra 4 e 30 caratteri")
    private String username;

    private String role;

    private UUID managerId;

    /** Optional: absent or blank keeps the current password. */
    @ValidPassword(optional = true)
    private String password;
}
