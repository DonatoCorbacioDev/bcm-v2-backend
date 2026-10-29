/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.ContractManager;

/** One element of {@code data/contract_managers.json}: the managers assigned to a contract besides its owner. */
public record ContractManagerExportDTO(
        Long contractId,
        Long managerId) {

    public static ContractManagerExportDTO from(ContractManager assignment) {
        return new ContractManagerExportDTO(
                assignment.getContract().getId(),
                assignment.getManager().getId());
    }
}
