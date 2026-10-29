/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.BusinessAreas;

/** One element of {@code data/business_areas.json}. */
public record BusinessAreaExportDTO(
        Long id,
        String name,
        String description) {

    public static BusinessAreaExportDTO from(BusinessAreas area) {
        return new BusinessAreaExportDTO(
                area.getId(),
                area.getName(),
                area.getDescription());
    }
}
