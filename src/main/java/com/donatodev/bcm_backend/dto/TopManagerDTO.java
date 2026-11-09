/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;

public class TopManagerDTO {

    private final UUID managerId;
    private final String managerName;
    private final Long contractsCount;

    public TopManagerDTO(UUID managerId, String managerName, Long contractsCount) {
        this.managerId = managerId;
        this.managerName = managerName;
        this.contractsCount = contractsCount;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public String getManagerName() {
        return managerName;
    }

    public Long getContractsCount() {
        return contractsCount;
    }
}
