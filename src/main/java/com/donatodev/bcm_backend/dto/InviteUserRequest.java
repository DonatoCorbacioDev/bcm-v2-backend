/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InviteUserRequest {

    @NotBlank
    @Size(min = 4, max = 255)
    private String username;

    @NotBlank
    @Pattern(regexp = "ADMIN|MANAGER", message = "Ruolo non valido")
    private String role;

    @NotNull
    private UUID managerId;
}
