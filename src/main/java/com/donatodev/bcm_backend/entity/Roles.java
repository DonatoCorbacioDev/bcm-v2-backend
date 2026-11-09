/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a user role within the system.
 * <p>
 * Each role defines access permissions and responsibilities for users.
 * Examples: ADMIN, MANAGER, USER.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Roles {

    /**
     * Primary key for the role entity.
     * Automatically generated.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /**
     * Name of the role (e.g., "ADMIN", "MANAGER", "USER").
     * Must be unique and not null.
     */
    @Column(name = "role", nullable = false, unique = true)
    private String role;
}
