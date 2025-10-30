package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the role (e.g., "ADMIN", "MANAGER", "USER").
     * Must be unique and not null.
     */
    @Column(name = "role", nullable = false, unique = true)
    private String role;
}
