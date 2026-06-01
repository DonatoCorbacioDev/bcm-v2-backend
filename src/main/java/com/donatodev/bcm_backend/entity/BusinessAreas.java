package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity that maps the "business_areas" table in the database.
 * Represents the business domains in which contracts operate.
 */
@Entity
@Table(name = "business_areas")
@SuperBuilder
@NoArgsConstructor
public class BusinessAreas extends OrgNamedEntity {
}
