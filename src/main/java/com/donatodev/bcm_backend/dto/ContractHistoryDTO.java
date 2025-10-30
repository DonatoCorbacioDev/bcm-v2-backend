package com.donatodev.bcm_backend.dto;

import java.time.LocalDateTime;

import com.donatodev.bcm_backend.entity.ContractStatus;

/**
 * Data Transfer Object for Contract History.
 * <p>
 * Represents a historical record of status changes for a specific contract.
 *
 * @param id              the unique identifier of the contract history entry
 * @param contractId      the ID of the contract associated with this history entry
 * @param modifiedById    the ID of the user who made the modification
 * @param modificationDate the timestamp when the modification occurred
 * @param previousStatus  the contract status before the change
 * @param newStatus       the contract status after the change
 */
public record ContractHistoryDTO(
        Long id,
        Long contractId,
        Long modifiedById,
        LocalDateTime modificationDate,
        ContractStatus previousStatus,
        ContractStatus newStatus
) {}
