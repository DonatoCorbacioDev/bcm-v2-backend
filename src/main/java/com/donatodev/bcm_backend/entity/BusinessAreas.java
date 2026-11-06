/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity that maps the "business_areas" table in the database.
 * Represents the business domains in which contracts operate.
 */
@Entity
@Table(name = "business_areas",
        uniqueConstraints = @UniqueConstraint(name = "uq_ba_name_org", columnNames = {"name", "organization_id"}))
@SuperBuilder
@NoArgsConstructor
public class BusinessAreas extends OrgNamedEntity {
}
