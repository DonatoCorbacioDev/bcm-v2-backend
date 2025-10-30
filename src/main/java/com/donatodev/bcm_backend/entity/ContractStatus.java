package com.donatodev.bcm_backend.entity;

/**
 * Enum representing the possible statuses of a contract.
 * 
 * <ul>
 *   <li><b>ACTIVE</b>: The contract is currently in effect.</li>
 *   <li><b>EXPIRED</b>: The contract has reached its end date and is no longer valid.</li>
 *   <li><b>CANCELLED</b>: The contract was terminated before its natural expiration.</li>
 * </ul>
 */
public enum ContractStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED,
    DRAFT
}
