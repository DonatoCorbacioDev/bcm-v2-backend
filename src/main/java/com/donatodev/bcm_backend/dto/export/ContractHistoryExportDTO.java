/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractHistory;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Users;

/** One element of {@code data/contract_history.json}. */
public record ContractHistoryExportDTO(
        Long id,
        Long contractId,
        Long modifiedByUserId,
        LocalDateTime modificationDate,
        ContractStatus previousStatus,
        ContractStatus newStatus) {

    public static ContractHistoryExportDTO from(ContractHistory change) {
        return new ContractHistoryExportDTO(
                change.getId(),
                change.getContract().getId(),
                ExportIds.idOf(change.getModifiedBy(), Users::getId),
                change.getModificationDate(),
                change.getPreviousStatus(),
                change.getNewStatus());
    }
}
