/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * The invoiced totals compared with contract amounts, on real MySQL (the
 * CASE expression in the JPQL sums is exactly what H2 might accept and MySQL
 * not, or the other way round): taxable amount, not the VAT-inclusive total;
 * a credit note subtracts; an invoice without a taxable amount falls back to
 * its total; only confirmed matches count.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: invoiced totals are taxable amounts, credit notes subtract")
class InvoiceTotalsIT extends AbstractMySQLIntegrationTest {

    @Autowired private ElectronicInvoiceRepository invoiceRepository;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private OrganizationRepository organizationRepository;

    private Contracts contract;
    private Counterparty supplier;
    private final int year = LocalDate.now().getYear();

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        Organization org = organizationRepository.save(Organization.builder()
                .name("Invoice Totals IT Org").slug("invoice-totals-it-org").build());
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                .name("Invoice Totals IT Area").organization(org).build());
        supplier = counterpartiesRepository.save(Counterparty.builder()
                .name("Invoice Totals IT Supplier").type(CounterpartyType.SUPPLIER).organization(org).build());
        contract = contractsRepository.save(Contracts.builder()
                .counterparty(supplier).contractNumber("IT-INVOICE-TOTALS-001").businessArea(area)
                .startDate(LocalDate.now().minusMonths(6)).status(ContractStatus.ACTIVE).organization(org).build());
    }

    private void invoice(String number, String type, String total, String taxable, InvoiceMatchStatus status) {
        invoiceRepository.save(ElectronicInvoice.builder()
                .contract(contract)
                .storagePath("invoices/it-test/" + java.util.UUID.randomUUID() + ".xml")
                .fileName(number + ".xml").fileSize(1024L).contentType("application/xml")
                .orgId(contract.getOrganization().getId())
                .supplierName(supplier.getName()).invoiceNumber(number).documentType(type)
                .invoiceDate(LocalDate.of(year, java.time.Month.JANUARY, 15))
                .totalAmount(new BigDecimal(total))
                .taxableAmount(taxable == null ? null : new BigDecimal(taxable))
                .matchStatus(status)
                .currency("EUR")
                .build());
    }

    @Test
    @DisplayName("Sums taxable amounts, subtracts credit notes, falls back to the total, ignores unconfirmed")
    void sumsComparableAmounts() {
        invoice("F1", "TD01", "1220.00", "1000.00", InvoiceMatchStatus.CONFIRMED);   // +1000, not 1220
        invoice("NC1", "TD04", "244.00", "200.00", InvoiceMatchStatus.CONFIRMED);    // -200
        invoice("OLD", "TD01", "300.00", null, InvoiceMatchStatus.CONFIRMED);        // +300, no taxable stored
        invoice("F2", "TD01", "610.00", "500.00", InvoiceMatchStatus.SUGGESTED);     // not confirmed: 0

        BigDecimal byCounterparty = invoiceRepository.sumConfirmedInvoicedAmountByCounterpartyIdAndYear(supplier.getId(), year);
        BigDecimal byOrg = invoiceRepository.sumConfirmedInvoicedAmountByOrgIdAndYear(contract.getOrganization().getId(), year);

        assertEquals(0, new BigDecimal("1100.00").compareTo(byCounterparty), "got " + byCounterparty);
        assertEquals(0, new BigDecimal("1100.00").compareTo(byOrg), "got " + byOrg);
    }
}
