/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;

/** One element of {@code data/counterparties.json}. */
public record CounterpartyExportDTO(
        Long id,
        String name,
        CounterpartyType type,
        String vatNumber,
        String taxCode,
        String address,
        String contactName,
        String contactEmail,
        String contactPhone,
        String notes,
        String verifiedIban,
        String verifiedBic) {

    public static CounterpartyExportDTO from(Counterparty counterparty) {
        return new CounterpartyExportDTO(
                counterparty.getId(),
                counterparty.getName(),
                counterparty.getType(),
                counterparty.getVatNumber(),
                counterparty.getTaxCode(),
                counterparty.getAddress(),
                counterparty.getContactName(),
                counterparty.getContactEmail(),
                counterparty.getContactPhone(),
                counterparty.getNotes(),
                counterparty.getVerifiedIban(),
                counterparty.getVerifiedBic());
    }
}
