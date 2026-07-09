package com.donatodev.bcm_backend.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.SepaPaymentBatchDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.SepaPaymentBatchRepository;

@ExtendWith(MockitoExtension.class)
class SepaPaymentServiceTest {

    private static final Long CONTRACT_ID = 1L;
    private static final Long ORG_ID = 5L;

    @Mock private ContractAccessGuard contractAccessGuard;
    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SepaPaymentBatchRepository batchRepository;
    @Mock private LocalStorageService localStorageService;

    private SepaPaymentService sepaPaymentService;

    @BeforeEach
    void setup() {
        sepaPaymentService = new SepaPaymentService(
                contractAccessGuard, invoiceRepository, organizationRepository, batchRepository, localStorageService);
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    private Organization fakeOrganization(String iban, String bic) {
        return Organization.builder().id(ORG_ID).name("Acme S.r.l.").iban(iban).bic(bic).build();
    }

    private ElectronicInvoice fakeInvoice(Long id, String supplierIban, String currency, BigDecimal amount) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(id);
        invoice.setSupplierName("Fornitore " + id);
        invoice.setSupplierIban(supplierIban);
        invoice.setSupplierBic("COBADEFFXXX");
        invoice.setCurrency(currency);
        invoice.setTotalAmount(amount);
        invoice.setInvoiceNumber("INV-" + id);
        return invoice;
    }

    @Nested
    @DisplayName("createSepaPayment")
    class CreateSepaPayment {

        @Test
        @DisplayName("happy path: builds a valid pain.001 XML, persists batch, tags invoices")
        void shouldCreateSepaPayment() throws Exception {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice1 = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            ElectronicInvoice invoice2 = fakeInvoice(11L, "FR1420041010050500013M02606", "EUR", new BigDecimal("50.50"));

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L, 11L)))
                    .thenReturn(List.of(invoice1, invoice2));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));
            when(localStorageService.storeSepaPayment(eq(ORG_ID), eq(CONTRACT_ID), any()))
                    .thenReturn("sepa/5/1/uuid.xml");
            when(batchRepository.save(any(SepaPaymentBatch.class))).thenAnswer(inv -> {
                SepaPaymentBatch batch = inv.getArgument(0);
                batch.setId(99L);
                return batch;
            });

            LocalDate executionDate = LocalDate.now().plusDays(1);
            FileDownload result = sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L, 11L), executionDate);

            assertEquals("application/xml", result.contentType());

            Document doc = parse(result.bytes());
            assertEquals("2", textOf(doc, "NbOfTxs"));
            assertEquals(2, doc.getElementsByTagName("CdtTrfTxInf").getLength());
            assertEquals(0, new BigDecimal("150.50").compareTo(new BigDecimal(textOf(doc, "CtrlSum"))));
            assertTrue(xmlContains(doc, "IBAN", "DE89370400440532013000"));
            assertTrue(xmlContains(doc, "IBAN", "IT60X0542811101000000123456"));
            assertTrue(xmlContains(doc, "IBAN", "FR1420041010050500013M02606"));

            verify(invoiceRepository).saveAll(List.of(invoice1, invoice2));
            assertEquals(99L, invoice1.getSepaBatch().getId());
            assertEquals(99L, invoice2.getSepaBatch().getId());
        }

        @Test
        @DisplayName("throws when the organization has no IBAN configured")
        void shouldThrowWhenOrgHasNoIban() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization(null, null);
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L))).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L), null));
            verify(batchRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws when a selected invoice has no supplier IBAN")
        void shouldThrowWhenInvoiceHasNoSupplierIban() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, null, "EUR", new BigDecimal("100.00"));

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L))).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L), null));
        }

        @Test
        @DisplayName("throws when a selected invoice is already part of a SEPA batch")
        void shouldThrowWhenInvoiceAlreadyBatched() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", new BigDecimal("100.00"));
            invoice.setSepaBatch(SepaPaymentBatch.builder().id(1L).build());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L))).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L), null));
        }

        @Test
        @DisplayName("throws when some invoice IDs don't belong to the contract")
        void shouldThrowWhenInvoiceIdsNotFound() {
            Contracts contract = fakeContract();
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L, 11L)))
                    .thenReturn(List.of(fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN)));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L, 11L), null));
        }

        @Test
        @DisplayName("throws when selected invoices mix currencies")
        void shouldThrowWhenCurrenciesMixed() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice1 = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN);
            ElectronicInvoice invoice2 = fakeInvoice(11L, "FR1420041010050500013M02606", "USD", BigDecimal.TEN);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L, 11L)))
                    .thenReturn(List.of(invoice1, invoice2));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L, 11L), null));
        }

        @Test
        @DisplayName("throws when the requested execution date is in the past")
        void shouldThrowWhenExecutionDateInPast() {
            Contracts contract = fakeContract();
            Organization org = fakeOrganization("DE89370400440532013000", "COBADEFFXXX");
            ElectronicInvoice invoice = fakeInvoice(10L, "IT60X0542811101000000123456", "EUR", BigDecimal.TEN);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdAndIdIn(CONTRACT_ID, List.of(10L))).thenReturn(List.of(invoice));
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(org));

            assertThrows(IllegalArgumentException.class,
                    () -> sepaPaymentService.createSepaPayment(CONTRACT_ID, List.of(10L), LocalDate.now().minusDays(1)));
        }
    }

    @Nested
    @DisplayName("getPayments / downloadPayment")
    class GetAndDownload {

        @Test
        @DisplayName("getPayments: maps batches to DTOs")
        void shouldListPayments() {
            Contracts contract = fakeContract();
            SepaPaymentBatch batch = SepaPaymentBatch.builder()
                    .id(1L).contract(contract).executionDate(LocalDate.now())
                    .totalAmount(new BigDecimal("10.00")).currency("EUR")
                    .numberOfTransactions(1).fileName("sepa-1.xml").build();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByContractIdOrderByCreatedAtDesc(CONTRACT_ID)).thenReturn(List.of(batch));

            List<SepaPaymentBatchDTO> result = sepaPaymentService.getPayments(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals("sepa-1.xml", result.get(0).fileName());
        }

        @Test
        @DisplayName("downloadPayment: returns stored bytes")
        void shouldDownloadPayment() {
            Contracts contract = fakeContract();
            SepaPaymentBatch batch = SepaPaymentBatch.builder()
                    .id(1L).contract(contract).storagePath("sepa/5/1/uuid.xml").fileName("sepa-1.xml").build();

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByIdAndContractId(1L, CONTRACT_ID)).thenReturn(Optional.of(batch));
            when(localStorageService.readDocument("sepa/5/1/uuid.xml")).thenReturn("<xml/>".getBytes());

            FileDownload result = sepaPaymentService.downloadPayment(CONTRACT_ID, 1L);

            assertEquals("sepa-1.xml", result.fileName());
        }

        @Test
        @DisplayName("downloadPayment: throws when batch not found for contract")
        void shouldThrowWhenBatchNotFound() {
            Contracts contract = fakeContract();
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(batchRepository.findByIdAndContractId(1L, CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> sepaPaymentService.downloadPayment(CONTRACT_ID, 1L));
        }
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String textOf(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        return nodes.item(0).getTextContent();
    }

    private boolean xmlContains(Document doc, String tagName, String value) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            if (value.equals(nodes.item(i).getTextContent())) {
                return true;
            }
        }
        return false;
    }
}
