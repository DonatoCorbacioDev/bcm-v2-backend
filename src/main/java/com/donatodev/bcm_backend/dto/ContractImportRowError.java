package com.donatodev.bcm_backend.dto;

/**
 * A single failed row from a bulk contract import.
 *
 * @param rowNumber the 1-based Excel row number (as the user would see it, including the header row)
 * @param message   a human-readable reason the row was rejected
 */
public record ContractImportRowError(int rowNumber, String message) {
}
