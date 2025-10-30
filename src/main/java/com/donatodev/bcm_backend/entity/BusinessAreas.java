package com.donatodev.bcm_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity that maps the "business_areas" table in the database.
 * <p>
 * Represents the business domains in which contracts operate.
 */
@Entity
@Table(name = "business_areas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessAreas {

    /**
     * Primary key. Auto-incremented.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name of the business area.
     * Cannot be null.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Optional description of the business area.
     */
    @Column(name = "description")
    private String description;
}
