/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.CounterpartyNotFoundException;
import com.donatodev.bcm_backend.mapper.CounterpartyMapper;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;

/**
 * Service class responsible for business logic related to counterparties.
 * <p>
 * Provides methods to retrieve, create, update, and delete counterparties,
 * plus a resolve-or-create lookup used by contract creation/import flows.
 */
@Service
public class CounterpartyService {

    private static final String COUNTERPARTY_ID_PREFIX = "ID controparte ";
    private static final String NOT_FOUND_SUFFIX = " non trovata";

    private final CounterpartiesRepository counterpartiesRepository;
    private final CounterpartyMapper counterpartyMapper;

    public CounterpartyService(CounterpartiesRepository counterpartiesRepository, CounterpartyMapper counterpartyMapper) {
        this.counterpartiesRepository = counterpartiesRepository;
        this.counterpartyMapper = counterpartyMapper;
    }

    public List<CounterpartyDTO> getAllCounterparties() {
        Long orgId = TenantContext.get();
        List<Counterparty> counterparties = (orgId != null)
                ? counterpartiesRepository.findAllByOrganizationId(orgId)
                : counterpartiesRepository.findAll();
        return counterparties.stream().map(counterpartyMapper::toDTO).toList();
    }

    public CounterpartyDTO getCounterpartyById(Long id) {
        return findCounterpartyInScope(id)
                .map(counterpartyMapper::toDTO)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }

    /**
     * Finds a counterparty by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    Optional<Counterparty> findCounterpartyInScope(Long id) {
        Long orgId = TenantContext.get();
        return (orgId != null)
                ? counterpartiesRepository.findByIdAndOrganizationId(id, orgId)
                : counterpartiesRepository.findById(id);
    }

    public CounterpartyDTO createCounterparty(CounterpartyDTO dto) {
        Counterparty counterparty = counterpartyMapper.toEntity(dto);
        Long orgId = TenantContext.get();
        if (orgId != null) {
            Organization org = new Organization();
            org.setId(orgId);
            counterparty.setOrganization(org);
        }
        counterparty = counterpartiesRepository.save(counterparty);
        return counterpartyMapper.toDTO(counterparty);
    }

    public CounterpartyDTO updateCounterparty(Long id, CounterpartyDTO dto) {
        Counterparty counterparty = findCounterpartyInScope(id)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        counterparty.setName(dto.name());
        counterparty.setType(dto.type());
        counterparty.setVatNumber(dto.vatNumber());
        counterparty.setTaxCode(dto.taxCode());
        counterparty.setAddress(dto.address());
        counterparty.setContactName(dto.contactName());
        counterparty.setContactEmail(dto.contactEmail());
        counterparty.setContactPhone(dto.contactPhone());
        counterparty.setNotes(dto.notes());

        counterparty = counterpartiesRepository.save(counterparty);
        return counterpartyMapper.toDTO(counterparty);
    }

    public void deleteCounterparty(Long id) {
        Counterparty counterparty = findCounterpartyInScope(id)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));
        counterpartiesRepository.delete(counterparty);
    }

    /**
     * Finds a counterparty by exact (case-insensitive) name within the current
     * tenant, creating one with type {@link CounterpartyType#CUSTOMER} if none
     * exists. Used by {@code ContractImportService} so an Excel import doesn't
     * force the user to pre-create every counterparty by hand first.
     */
    public Counterparty resolveOrCreateByName(String name) {
        Long orgId = TenantContext.get();
        Optional<Counterparty> existing = (orgId != null)
                ? counterpartiesRepository.findByNameIgnoreCaseAndOrganizationId(name, orgId)
                : Optional.empty();
        if (existing.isPresent()) {
            return existing.get();
        }

        Counterparty counterparty = Counterparty.builder()
                .name(name)
                .type(CounterpartyType.CUSTOMER)
                .build();
        if (orgId != null) {
            Organization org = new Organization();
            org.setId(orgId);
            counterparty.setOrganization(org);
        }
        return counterpartiesRepository.save(counterparty);
    }
}
