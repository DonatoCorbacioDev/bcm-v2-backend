/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.UUID;
import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractStatus;

/**
 * Data Transfer Object for Contract History.
 * <p>
 * Represents a historical record of status changes for a specific contract.
 *
 * @param id              the unique identifier of the contract history entry
 * @param contractId      the ID of the contract associated with this history entry
 * @param modifiedById    the ID of the user who made the modification, or null
 *                        if that user has since been deleted
 * @param modificationDate the timestamp when the modification occurred
 * @param previousStatus  the contract status before the change
 * @param newStatus       the contract status after the change
 * @param modifiedByName  who made the change, for display: the person's name,
 *                        else their username; null when there is no author
 *                        (a scheduler change with no admin, or a deleted user).
 *                        Read-only: ignored when the DTO is turned into an entity.
 */
public record ContractHistoryDTO(
        UUID id,
        UUID contractId,
        UUID modifiedById,
        LocalDateTime modificationDate,
        ContractStatus previousStatus,
        ContractStatus newStatus,
        String modifiedByName
) {

    /** Without the display name: what a client sends when it writes a record. */
    public ContractHistoryDTO(UUID id, UUID contractId, UUID modifiedById, LocalDateTime modificationDate,
            ContractStatus previousStatus, ContractStatus newStatus) {
        this(id, contractId, modifiedById, modificationDate, previousStatus, newStatus, null);
    }
}
