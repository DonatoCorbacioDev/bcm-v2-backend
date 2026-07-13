package com.donatodev.bcm_backend.dto;

import java.util.List;

/**
 * Outcome of a bulk contract import from an uploaded spreadsheet. Import is
 * best-effort per row: valid rows are saved even if others in the same file
 * fail.
 *
 * @param totalRows     number of non-blank data rows found in the file
 * @param importedCount number of rows successfully saved as contracts
 * @param errorCount    number of rows rejected
 * @param errors        per-row rejection reasons, in file order
 */
public record ContractImportResultDTO(
        int totalRows,
        int importedCount,
        int errorCount,
        List<ContractImportRowError> errors) {
}
