package com.donatodev.bcm_backend.entity;

import java.time.LocalDateTime;

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
 * Represents the {@code managers} table in the database.
 * <p>
 * This entity stores information about contract managers, including personal details,
 * contact information, department, and their associated contracts.
 */
@Entity
@Table(name = "managers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Managers {

    /**
     * Unique identifier for the manager.
     * This ID is auto-generated and used as the primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * First name of the manager.
     * This field is required.
     */
    @Column(name = "first_name", nullable = false)
    private String firstName;

    /**
     * Last name (surname) of the manager.
     * This field is required.
     */
    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Email address of the manager.
     * This field must be unique and is required.
     */
    @Column(name = "email", nullable = false, unique = true)
    private String email;

    /**
     * Optional phone number of the manager.
     */
    @Column(name = "phone_number")
    private String phoneNumber;

    /**
     * Department to which the manager belongs (e.g., IT, HR, Finance).
     * This field is optional.
     */
    @Column(name = "department")
    private String department;

    /**
     * Timestamp indicating when the manager record was created.
     * This field is set once at creation time and is not updatable.
     */
    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
