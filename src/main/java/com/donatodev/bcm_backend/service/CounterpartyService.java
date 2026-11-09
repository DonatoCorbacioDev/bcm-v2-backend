/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.UUID;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.CounterpartyDTO;
import com.donatodev.bcm_backend.dto.CounterpartyInvoicingSummaryDTO;
import com.donatodev.bcm_backend.dto.VerifyCounterpartyIbanRequest;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.CounterpartyNotFoundException;
import com.donatodev.bcm_backend.mapper.CounterpartyMapper;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.FinancialValuesRepository;
import com.donatodev.bcm_backend.util.IbanValidator;

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
    private final ContractsRepository contractsRepository;
    private final ElectronicInvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final FinancialValuesRepository financialValuesRepository;

    public CounterpartyService(CounterpartiesRepository counterpartiesRepository, CounterpartyMapper counterpartyMapper,
                                ContractsRepository contractsRepository, ElectronicInvoiceRepository invoiceRepository,
                                AuditLogService auditLogService, FinancialValuesRepository financialValuesRepository) {
        this.counterpartiesRepository = counterpartiesRepository;
        this.counterpartyMapper = counterpartyMapper;
        this.contractsRepository = contractsRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditLogService = auditLogService;
        this.financialValuesRepository = financialValuesRepository;
    }

    public List<CounterpartyDTO> getAllCounterparties() {
        UUID orgId = TenantContext.require();
        List<Counterparty> counterparties = counterpartiesRepository.findAllByOrganizationId(orgId);
        return counterparties.stream().map(counterpartyMapper::toDTO).toList();
    }

    public CounterpartyDTO getCounterpartyById(UUID id) {
        return findCounterpartyInScope(id)
                .map(counterpartyMapper::toDTO)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));
    }

    /**
     * Contracted value vs. confirmed-invoiced-to-date for the current year.
     * Only {@code CONFIRMED} invoice matches count as invoiced.
     */
    public CounterpartyInvoicingSummaryDTO getInvoicingSummary(UUID id) {
        Counterparty counterparty = findCounterpartyInScope(id)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int year = today.getYear();
        int activeContracts = contractsRepository.countActiveContractsByCounterpartyId(id);
        double contractedValue = contractsRepository.sumActiveAnnualValueByCounterpartyId(id);
        long invoiceCount = invoiceRepository.countByContractCounterpartyId(id);
        LocalDate lastInvoiceDate = invoiceRepository.findLastInvoiceDateByCounterpartyId(id);

        // Only a supplier's invoices reach BCM (received FatturaPA), so only
        // for a supplier is "invoiced vs expected" meaningful. Expected and
        // invoiced cover the same period (January to this month), both IVA
        // esclusa: comparing the yearly contract value with the invoices so
        // far made a punctual supplier look -50% in June.
        boolean comparable = counterparty.getType() == CounterpartyType.SUPPLIER
                || counterparty.getType() == CounterpartyType.BOTH;
        double invoicedYtd = 0.0;
        double expectedYtd = 0.0;
        double variancePercent = 0.0;
        if (comparable) {
            invoicedYtd = invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(id, year).doubleValue();
            expectedYtd = financialValuesRepository.sumCostByCounterpartyUpToMonth(id, year, today.getMonthValue());
            variancePercent = expectedYtd == 0 ? 0.0 : ((invoicedYtd - expectedYtd) / expectedYtd) * 100.0;
        }

        return new CounterpartyInvoicingSummaryDTO(
                counterparty.getId(), counterparty.getName(), activeContracts, contractedValue,
                invoicedYtd, variancePercent, invoiceCount, lastInvoiceDate, expectedYtd, comparable);
    }

    /**
     * Finds a counterparty by ID, scoped to the current tenant when
     * {@link TenantContext} carries an organization ID.
     */
    Optional<Counterparty> findCounterpartyInScope(UUID id) {
        UUID orgId = TenantContext.require();
        return counterpartiesRepository.findByIdAndOrganizationId(id, orgId);
    }

    public CounterpartyDTO createCounterparty(CounterpartyDTO dto) {
        Counterparty counterparty = counterpartyMapper.toEntity(dto);
        // Creation must never merge an existing row selected by a client-supplied ID.
        counterparty.setId(null);
        UUID orgId = TenantContext.require();
        Organization org = new Organization();
        org.setId(orgId);
        counterparty.setOrganization(org);
        counterparty = counterpartiesRepository.save(counterparty);
        return counterpartyMapper.toDTO(counterparty);
    }

    public CounterpartyDTO updateCounterparty(UUID id, CounterpartyDTO dto) {
        Counterparty counterparty = findCounterpartyInScope(id)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        counterparty.setName(dto.name());
        counterparty.setType(dto.type());
        counterparty.setVatNumber(dto.vatNumber());
        counterparty.setTaxCode(dto.taxCode());
        counterparty.setAddress(dto.address());
        counterparty.setPostalCode(CounterpartyMapper.blankToNull(dto.postalCode()));
        counterparty.setCity(CounterpartyMapper.blankToNull(dto.city()));
        counterparty.setProvince(CounterpartyMapper.upperOrNull(dto.province()));
        counterparty.setCountry(CounterpartyMapper.upperOrNull(dto.country()));
        counterparty.setContactName(dto.contactName());
        counterparty.setContactEmail(dto.contactEmail());
        counterparty.setContactPhone(dto.contactPhone());
        counterparty.setNotes(dto.notes());

        counterparty = counterpartiesRepository.save(counterparty);
        return counterpartyMapper.toDTO(counterparty);
    }

    /**
     * Explicitly sets/replaces the IBAN this counterparty is trusted to be
     * paid at for SEPA payments -- a deliberate, separate action from the
     * general update endpoint, used after confirming a supplier's IBAN
     * change out of band. From then on, {@code SepaPaymentService} rejects
     * any invoice whose supplier IBAN doesn't match this one. Logs the old
     * and new IBAN (masked) so a change here is forensically traceable,
     * unlike the generic audit aspect which only records the method name.
     */
    @Transactional
    public CounterpartyDTO confirmVerifiedIban(UUID id, VerifyCounterpartyIbanRequest request) {
        Counterparty counterparty = findCounterpartyInScope(id)
                .orElseThrow(() -> new CounterpartyNotFoundException(COUNTERPARTY_ID_PREFIX + id + NOT_FOUND_SUFFIX));

        String normalizedIban = request.iban().replace(" ", "").toUpperCase(Locale.ROOT);
        if (!IbanValidator.isValid(normalizedIban)) {
            throw new IllegalArgumentException("IBAN non valido");
        }
        String normalizedBic = request.bic() != null
                ? request.bic().replace(" ", "").toUpperCase(Locale.ROOT)
                : null;

        String previousIban = counterparty.getVerifiedIban();
        counterparty.setVerifiedIban(normalizedIban);
        counterparty.setVerifiedBic(normalizedBic == null || normalizedBic.isBlank() ? null : normalizedBic);
        counterparty = counterpartiesRepository.save(counterparty);

        String username = AuthenticatedUserUtils.getUsernameOrThrow();
        String action = previousIban == null ? "set" : "changed";
        String details = "Verified IBAN for counterparty " + id + " " + action
                + " (" + IbanValidator.mask(previousIban) + " -> " + IbanValidator.mask(normalizedIban) + ")";
        auditLogService.save("VERIFIED_IBAN_CONFIRMED", "Counterparty", id, username, TenantContext.require(), details);

        return counterpartyMapper.toDTO(counterparty);
    }

    public void deleteCounterparty(UUID id) {
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
        UUID orgId = TenantContext.require();
        Optional<Counterparty> existing = counterpartiesRepository.findByNameIgnoreCaseAndOrganizationId(name, orgId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Counterparty counterparty = Counterparty.builder()
                .name(name)
                .type(CounterpartyType.CUSTOMER)
                .build();
        Organization org = new Organization();
        org.setId(orgId);
        counterparty.setOrganization(org);
        return counterpartiesRepository.save(counterparty);
    }
}
