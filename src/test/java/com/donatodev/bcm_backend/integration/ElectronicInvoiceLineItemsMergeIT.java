/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import com.donatodev.bcm_backend.entity.InvoiceLineItem;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.CounterpartiesRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * Regression coverage for a bug found 2026-09-15 while manually verifying the
 * invoice-matching flow with a real upload: {@code ElectronicInvoiceService
 * .uploadInvoice} saves the invoice once to get an id, then saves it again
 * after {@code computeSuggestion} sets the match fields. Once an entity has a
 * non-null id, {@code JpaRepository.save} always goes through {@code
 * EntityManager.merge}, and Hibernate's merge for a {@code @OneToMany}
 * collection clears/repopulates the managed {@code PersistentBag} backing it
 * -- which throws {@code UnsupportedOperationException} if that bag was
 * seeded from an immutable {@code List} (e.g. {@code Stream.toList()}).
 * <p>
 * {@code ElectronicInvoiceServiceTest} mocks {@code ElectronicInvoiceRepository
 * .save}, so it can't see this: Mockito's stub never touches Hibernate's real
 * merge/collection-replacement logic. Only a real persistence context (real
 * MySQL, via {@link AbstractMySQLIntegrationTest}) exercises it, which is why
 * this lives here instead of as another unit test.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: ElectronicInvoice line items survive a persist-then-merge save")
class ElectronicInvoiceLineItemsMergeIT extends AbstractMySQLIntegrationTest {

    @Autowired private ElectronicInvoiceRepository invoiceRepository;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private CounterpartiesRepository counterpartiesRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private OrganizationRepository organizationRepository;

    private Contracts contract;

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        Organization org = organizationRepository.save(Organization.builder()
                .name("Line Items Merge IT Org").slug("line-items-merge-it-org").build());
        BusinessAreas area = businessAreasRepository.save(BusinessAreas.builder()
                .name("Line Items Merge IT Area").organization(org).build());
        Counterparty counterparty = counterpartiesRepository.save(Counterparty.builder()
                .name("Line Items Merge IT Counterparty")
                .type(CounterpartyType.CUSTOMER)
                .organization(org)
                .build());
        contract = contractsRepository.save(Contracts.builder()
                .counterparty(counterparty)
                .contractNumber("IT-LINE-ITEMS-MERGE-001")
                .businessArea(area)
                .startDate(LocalDate.now())
                .status(ContractStatus.ACTIVE)
                .organization(org)
                .build());
    }

    private ElectronicInvoice newInvoiceWithLineItems(List<InvoiceLineItem> lineItems) {
        ElectronicInvoice invoice = ElectronicInvoice.builder()
                .contract(contract)
                .storagePath("invoices/it-test/" + java.util.UUID.randomUUID() + ".xml")
                .fileName("it-test-invoice.xml")
                .fileSize(1024L)
                .contentType("application/xml")
                .orgId(contract.getOrganization().getId())
                .supplierName("Line Items Merge Supplier")
                .invoiceNumber("IT-001")
                .documentType("TD01")
                .totalAmount(new BigDecimal("500.00"))
                .currency("EUR")
                .build();
        lineItems.forEach(item -> item.setInvoice(invoice));
        invoice.setLineItems(lineItems);
        return invoice;
    }

    @Test
    @DisplayName("save() then save() again (mirrors uploadInvoice's persist + post-match-suggestion re-save) does not throw")
    void resavingAnAlreadyPersistedInvoiceWithLineItemsDoesNotThrow() {
        List<InvoiceLineItem> lineItems = Stream.of(1, 2)
                .map(lineNumber -> InvoiceLineItem.builder()
                        .lineNumber(lineNumber)
                        .description("Line " + lineNumber)
                        .quantity(BigDecimal.ONE)
                        .unitPrice(new BigDecimal("250.00"))
                        .totalPrice(new BigDecimal("250.00"))
                        .vatRate(BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        ElectronicInvoice invoice = invoiceRepository.save(newInvoiceWithLineItems(lineItems));

        ElectronicInvoice resaved = assertDoesNotThrow(() -> invoiceRepository.save(invoice),
                "Re-saving an already-persisted invoice (non-null id -> merge) must not throw "
                        + "UnsupportedOperationException on the lineItems collection");

        assertEquals(2, resaved.getLineItems().size(),
                "Line items must survive the persist-then-merge round trip");
    }

    @Test
    @DisplayName("an immutable line-items list reproduces the original UnsupportedOperationException on re-save")
    void immutableLineItemsListStillFailsOnResave() {
        // Stream.toList() (used by the pre-fix toLineItemEntities) returns an
        // *immutable* List -- this documents exactly why the collector above
        // was changed to Collectors.toCollection(ArrayList::new).
        List<InvoiceLineItem> immutableLineItems = Stream.of(InvoiceLineItem.builder()
                        .lineNumber(1)
                        .description("Immutable line")
                        .quantity(BigDecimal.ONE)
                        .unitPrice(BigDecimal.TEN)
                        .totalPrice(BigDecimal.TEN)
                        .vatRate(BigDecimal.ZERO)
                        .build())
                .toList();

        ElectronicInvoice invoice = invoiceRepository.save(newInvoiceWithLineItems(immutableLineItems));

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> invoiceRepository.save(invoice),
                "This is the historical bug, pinned so a future refactor can't silently reintroduce it");
    }
}
