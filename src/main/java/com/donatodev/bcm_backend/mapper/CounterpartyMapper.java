/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.mapper;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.entity.Counterparty;

/**
 * Mapper class responsible for converting between {@link Counterparty} entities
 * and {@link CounterpartyDTO} data transfer objects.
 */
@Component
public class CounterpartyMapper {

    public CounterpartyDTO toDTO(Counterparty counterparty) {
        return new CounterpartyDTO(
                counterparty.getId(),
                counterparty.getName(),
                counterparty.getType(),
                counterparty.getVatNumber(),
                counterparty.getTaxCode(),
                counterparty.getAddress(),
                counterparty.getContactName(),
                counterparty.getContactEmail(),
                counterparty.getContactPhone(),
                counterparty.getNotes()
        );
    }

    public Counterparty toEntity(CounterpartyDTO dto) {
        return Counterparty.builder()
                .id(dto.id())
                .name(dto.name())
                .type(dto.type())
                .vatNumber(dto.vatNumber())
                .taxCode(dto.taxCode())
                .address(dto.address())
                .contactName(dto.contactName())
                .contactEmail(dto.contactEmail())
                .contactPhone(dto.contactPhone())
                .notes(dto.notes())
                .build();
    }
}
