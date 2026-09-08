/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

public class ContractsByAreaDTO {

    private final String areaName;
    private final Long count;

    public ContractsByAreaDTO(String areaName, Long count) {
        this.areaName = areaName;
        this.count = count;
    }

    public String getAreaName() {
        return areaName;
    }

    public Long getCount() {
        return count;
    }
}
