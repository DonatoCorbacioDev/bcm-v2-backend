package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import com.donatodev.bcm_backend.config.TenantContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.dto.ConfirmInvoiceMatchRequest;
import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.FinancialValues;
import com.donatodev.bcm_backend.entity.InvoiceLineItem;
import com.donatodev.bcm_backend.entity.InvoiceMatchStatus;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.DuplicateInvoiceException;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ElectronicInvoiceServiceTest {

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void setup() {
        TenantContext.set(TENANT);
        electronicInvoiceService = new ElectronicInvoiceService(
                invoiceRepository, contractAccessGuard, localStorageService, fatturaPaXmlParserService,
                invoiceMatchingService);
        ReflectionTestUtils.setField(electronicInvoiceService, "backendBaseUrl", BACKEND_URL);

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        SecurityContextHolder.setContext(ctx);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private ContractAccessGuard contractAccessGuard;
    @Mock private LocalStorageService localStorageService;
    @Mock private FatturaPaXmlParserService fatturaPaXmlParserService;
    @Mock private InvoiceMatchingService invoiceMatchingService;

    private ElectronicInvoiceService electronicInvoiceService;

    private static final byte[] VALID_XML = "<?xml version=\"1.0\"?><FatturaElettronica></FatturaElettronica>".getBytes();
    private static final UUID CONTRACT_ID = uid(1);
    private static final UUID INVOICE_ID = uid(10);
    private static final String BACKEND_URL = "http://localhost:8090/api/v1";

    private Contracts fakeContract() {
        Contracts c = new Contracts();
        c.setId(CONTRACT_ID);
        return c;
    }

    private List<InvoiceLineItemDTO> sampleLineItems() {
        return List.of(new InvoiceLineItemDTO(1, "Servizio di consulenza", new BigDecimal("10.00"),
                "HUR", new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("22.00")));
    }

    private FatturaPaInvoiceData sampleParsedData() {
        return new FatturaPaInvoiceData("Acme Forniture S.r.l.", "IT12345678901", "TD01", "2024/001",
                LocalDate.of(2024, Month.MARCH, 15), new BigDecimal("1220.00"), "EUR", sampleLineItems(),
                null, null, null);
    }

    private ElectronicInvoice fakeInvoice(Contracts contract, List<InvoiceLineItemDTO> lineItems) {
        ElectronicInvoice invoice = new ElectronicInvoice();
        invoice.setId(INVOICE_ID);
        invoice.setContract(contract);
        invoice.setStoragePath("invoices/0/1/uuid-invoice.xml");
        invoice.setFileName("invoice.xml");
        invoice.setFileSize((long) VALID_XML.length);
        invoice.setContentType("application/xml");
        invoice.setUploadedAt(Instant.parse("2027-01-15T12:00:00Z"));
        invoice.setSupplierName("Acme Forniture S.r.l.");
        invoice.setSupplierVatNumber("IT12345678901");
        invoice.setDocumentType("TD01");
        invoice.setInvoiceNumber("2024/001");
        invoice.setInvoiceDate(LocalDate.of(2024, Month.MARCH, 15));
        invoice.setTotalAmount(new BigDecimal("1220.00"));
        invoice.setCurrency("EUR");
        invoice.setLineItems(lineItems.stream()
                .map(dto -> InvoiceLineItem.builder()
                        .invoice(invoice)
                        .lineNumber(dto.lineNumber())
                        .description(dto.description())
                        .quantity(dto.quantity())
                        .unitOfMeasure(dto.unitOfMeasure())
                        .unitPrice(dto.unitPrice())
                        .totalPrice(dto.totalPrice())
                        .vatRate(dto.vatRate())
                        .build())
                .toList());
        return invoice;
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: ElectronicInvoiceService")
    @SuppressWarnings("unused")
    class VerifyElectronicInvoiceService {

        // ---- uploadInvoice ----

        @Test
        @Order(1)
        @DisplayName("uploadInvoice: happy path returns DTO with parsed data and line items")
        void shouldUploadInvoiceSuccessfully() throws IOException {
            Contracts contract = fakeContract();
            ElectronicInvoice saved = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(localStorageService.storeInvoice(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("invoices/0/1/uuid-invoice.xml");
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            ElectronicInvoiceDTO result = electronicInvoiceService.uploadInvoice(CONTRACT_ID, file);

            assertNotNull(result);
            assertEquals("invoice.xml", result.fileName());
            assertEquals("Acme Forniture S.r.l.", result.supplierName());
            assertEquals("IT12345678901", result.supplierVatNumber());
            assertEquals("2024/001", result.invoiceNumber());
            assertEquals(1, result.lineItems().size());
            assertTrue(result.downloadUrl().contains("/contracts/" + CONTRACT_ID + "/invoices/" + INVOICE_ID + "/download"));
        }

        @Test
        @Order(2)
        @DisplayName("uploadInvoice: throws ContractNotFoundException when contract missing")
        void shouldThrowWhenContractNotFound() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenThrow(new ContractNotFoundException("Contract ID " + CONTRACT_ID + " not found"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(3)
        @DisplayName("uploadInvoice: throws IllegalArgumentException on empty file")
        void shouldThrowOnEmptyFile() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", new byte[0]);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(30)
        @DisplayName("uploadInvoice: throws DuplicateInvoiceException when supplier/number/type already on file")
        void shouldThrowOnDuplicateInvoice() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(invoiceRepository.existsByOrgIdAndSupplierVatNumberAndInvoiceNumberAndDocumentType(
                    any(), eq("IT12345678901"), eq("2024/001"), any())).thenReturn(true);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            assertThrows(DuplicateInvoiceException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
            verify(localStorageService, never()).storeInvoice(any(), any(), any());
        }

        @Test
        @Order(4)
        @DisplayName("uploadInvoice: throws IllegalArgumentException when file exceeds 5MB")
        void shouldThrowWhenFileTooLarge() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            byte[] huge = new byte[5 * 1024 * 1024 + 1];
            huge[0] = '<';
            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", huge);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(5)
        @DisplayName("uploadInvoice: throws IllegalArgumentException when content is not XML")
        void shouldThrowOnNonXmlContent() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.txt", "text/plain", "not xml content".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadInvoice: parser failure leaves no orphaned file or DB row")
        void shouldNotPersistWhenParserFails() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(fatturaPaXmlParserService.parse(any()))
                    .thenThrow(new IllegalArgumentException("Invalid or malformed XML"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));

            verify(localStorageService, never()).storeInvoice(any(), any(), any());
            verify(invoiceRepository, never()).save(any());
        }

        // ---- getInvoices ----

        @Test
        @Order(7)
        @DisplayName("getInvoices: returns mapped DTOs with line items")
        void shouldReturnInvoiceList() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(invoice));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(INVOICE_ID, result.get(0).id());
            assertEquals(1, result.get(0).lineItems().size());
            assertFalse(result.get(0).ibanChanged(), "no IBAN on the invoice: nothing changed");
        }

        @Test
        @DisplayName("getInvoices: flags an invoice whose IBAN differs from the supplier's verified one")
        void shouldFlagChangedIban() {
            Contracts contract = fakeContract();
            contract.setCounterparty(com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Acme Forniture S.r.l.").verifiedIban("IT17M0503411701000062194812").build());
            ElectronicInvoice sameIban = fakeInvoice(contract, sampleLineItems());
            sameIban.setSupplierIban("IT17 M050 3411 7010 0006 2194 812");
            ElectronicInvoice otherIban = fakeInvoice(contract, sampleLineItems());
            otherIban.setSupplierIban("IT07B3600003200000087345519");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(sameIban, otherIban));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertFalse(result.get(0).ibanChanged());
            assertTrue(result.get(1).ibanChanged());
        }

        @Test
        @Order(8)
        @DisplayName("getInvoices: returns empty list when no invoices")
        void shouldReturnEmptyInvoiceList() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of());

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(0, result.size());
        }

        @Test
        @Order(9)
        @DisplayName("getInvoices: throws ContractNotFoundException when contract missing")
        void shouldThrowWhenContractNotFoundOnList() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenThrow(new ContractNotFoundException("Contract ID " + CONTRACT_ID + " not found"));

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.getInvoices(CONTRACT_ID));
        }

        // ---- getInvoice ----

        @Test
        @Order(11)
        @DisplayName("getInvoice: returns DTO with deserialized line items")
        void shouldReturnInvoiceDetail() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            ElectronicInvoiceDTO result = electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID);

            assertEquals(INVOICE_ID, result.id());
            assertEquals(1, result.lineItems().size());
            assertEquals("Servizio di consulenza", result.lineItems().get(0).description());
        }

        @Test
        @Order(10)
        @DisplayName("getInvoice: includes the SEPA batch id when the invoice was already paid")
        void shouldReturnInvoiceDetailWithSepaBatchId() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            com.donatodev.bcm_backend.entity.SepaPaymentBatch batch =
                    com.donatodev.bcm_backend.entity.SepaPaymentBatch.builder().id(uid(77)).build();
            invoice.setSepaBatch(batch);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            ElectronicInvoiceDTO result = electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID);

            assertEquals(uid(77), result.sepaBatchId());
        }

        @Test
        @Order(12)
        @DisplayName("getInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFound() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- downloadInvoice ----

        @Test
        @Order(13)
        @DisplayName("downloadInvoice: returns bytes and metadata")
        void shouldDownloadInvoice() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(localStorageService.readDocument(invoice.getStoragePath())).thenReturn(VALID_XML);

            FileDownload result = electronicInvoiceService.downloadInvoice(CONTRACT_ID, INVOICE_ID);

            assertNotNull(result.bytes());
            assertEquals("invoice.xml", result.fileName());
            assertEquals("application/xml", result.contentType());
        }

        @Test
        @Order(14)
        @DisplayName("downloadInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFoundOnDownload() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.downloadInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- deleteInvoice ----

        @Test
        @Order(15)
        @DisplayName("deleteInvoice: deletes from local storage and repository")
        void shouldDeleteInvoice() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            electronicInvoiceService.deleteInvoice(CONTRACT_ID, INVOICE_ID);

            verify(localStorageService).deleteDocument(invoice.getStoragePath());
            verify(invoiceRepository).delete(invoice);
        }

        @Test
        @Order(16)
        @DisplayName("deleteInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFoundOnDelete() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.deleteInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- ContractAccessGuard delegation ----

        @Test
        @Order(17)
        @DisplayName("getInvoices: delegates contract lookup and manager-access check to ContractAccessGuard")
        void shouldDelegateAccessChecksToGuard() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(invoice));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(1, result.size());
            verify(contractAccessGuard).getContractInScope(CONTRACT_ID);
            verify(contractAccessGuard).checkManagerCanAccess(contract);
        }

        // ---- line items round trip ----

        @Test
        @Order(18)
        @DisplayName("getInvoice: no line items maps to an empty list, not null")
        void shouldRoundTripEmptyLineItems() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, List.of());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            ElectronicInvoiceDTO result = electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID);

            assertNotNull(result.lineItems());
            assertEquals(0, result.lineItems().size());
        }

        @Test
        @Order(20)
        @DisplayName("uploadInvoice: accepts XML content without an XML declaration")
        void shouldAcceptXmlWithoutDeclaration() throws IOException {
            Contracts contract = fakeContract();
            ElectronicInvoice saved = fakeInvoice(contract, sampleLineItems());

            byte[] xmlWithoutDeclaration = "<FatturaElettronica></FatturaElettronica>".getBytes(StandardCharsets.UTF_8);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(localStorageService.storeInvoice(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("invoices/0/1/uuid-invoice.xml");
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", xmlWithoutDeclaration);

            ElectronicInvoiceDTO result = electronicInvoiceService.uploadInvoice(CONTRACT_ID, file);

            assertNotNull(result);
        }

        @Test
        @Order(21)
        @DisplayName("updatePaymentDetails: normalizes and saves a valid IBAN/BIC/due date")
        void shouldUpdatePaymentDetails() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "de89 3704 0044 0532 0130 00", "cobadeffxxx", LocalDate.of(2024, Month.JUNE, 30));

            ElectronicInvoiceDTO result = electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request);

            // The response is masked, but the stored entity keeps the real value.
            assertEquals("DE...3000", result.supplierIban());
            assertEquals("CO...FXXX", result.supplierBic());
            assertEquals(LocalDate.of(2024, Month.JUNE, 30), result.paymentDueDate());
            assertEquals("DE89370400440532013000", invoice.getSupplierIban());
            assertEquals("COBADEFFXXX", invoice.getSupplierBic());
        }

        @Test
        @Order(22)
        @DisplayName("updatePaymentDetails: omitting the BIC leaves the existing value unchanged")
        void shouldLeaveBicUnchangedWhenOmitted() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            invoice.setSupplierBic("COBADEFFXXX");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "DE89370400440532013000", null, null);

            ElectronicInvoiceDTO result = electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request);

            assertEquals("CO...FXXX", result.supplierBic());
            assertEquals("COBADEFFXXX", invoice.getSupplierBic());
        }

        @Test
        @Order(25)
        @DisplayName("updatePaymentDetails: an empty-string BIC clears the stored value")
        void shouldClearBicWithEmptyString() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            invoice.setSupplierBic("COBADEFFXXX");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "DE89370400440532013000", "", null);

            ElectronicInvoiceDTO result = electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request);

            assertEquals(null, result.supplierBic());
            assertEquals(null, invoice.getSupplierBic());
        }

        @Test
        @Order(27)
        @DisplayName("updatePaymentDetails: a blank IBAN clears the stored value")
        void shouldClearIbanWhenBlank() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            invoice.setSupplierIban("DE89370400440532013000");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(" ", null, null);

            ElectronicInvoiceDTO result = electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request);

            assertEquals(null, result.supplierIban());
            assertEquals(null, invoice.getSupplierIban());
        }

        @Test
        @Order(26)
        @DisplayName("updatePaymentDetails: a null IBAN leaves the existing value unchanged")
        void shouldLeaveIbanUnchangedWhenNull() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            invoice.setSupplierIban("DE89370400440532013000");

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    null, null, LocalDate.of(2024, Month.JULY, 1));

            ElectronicInvoiceDTO result = electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request);

            assertEquals("DE...3000", result.supplierIban());
            assertEquals("DE89370400440532013000", invoice.getSupplierIban());
        }

        @Test
        @Order(23)
        @DisplayName("updatePaymentDetails: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFoundOnUpdatePaymentDetails() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "DE89370400440532013000", null, null);

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request));
        }

        @Test
        @Order(24)
        @DisplayName("updatePaymentDetails: rejects an invalid IBAN")
        void shouldRejectInvalidIbanOnPaymentDetails() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "IT00X0000000000000000000000", null, null);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request));
        }

        @Test
        @Order(25)
        @DisplayName("updatePaymentDetails: rejects edits once the invoice is already in a SEPA batch")
        void shouldRejectPaymentDetailsEditWhenAlreadyBatched() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            invoice.setSepaBatch(new com.donatodev.bcm_backend.entity.SepaPaymentBatch());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            var request = new com.donatodev.bcm_backend.dto.UpdateInvoicePaymentDetailsRequest(
                    "DE89370400440532013000", null, null);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.updatePaymentDetails(CONTRACT_ID, INVOICE_ID, request));
            verify(invoiceRepository, never()).save(any());
        }

        // ---- uploadInvoice: match-suggestion failure degrades without breaking the upload ----

        @Test
        @Order(26)
        @DisplayName("uploadInvoice: a match-suggestion failure is logged and swallowed, upload still succeeds")
        void shouldDegradeGracefullyWhenMatchSuggestionFails() throws IOException {
            Contracts contract = fakeContract();
            ElectronicInvoice saved = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(localStorageService.storeInvoice(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("invoices/0/1/uuid-invoice.xml");
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenReturn(saved);
            doThrow(new RuntimeException("scoring blew up")).when(invoiceMatchingService).computeSuggestion(any());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            ElectronicInvoiceDTO result = electronicInvoiceService.uploadInvoice(CONTRACT_ID, file);

            assertNotNull(result);
            assertEquals(INVOICE_ID, result.id());
            verify(invoiceRepository, times(1)).save(any(ElectronicInvoice.class));
        }

        @Test
        @Order(27)
        @DisplayName("uploadInvoice: a match-suggestion failure with a null message is also swallowed")
        void shouldDegradeGracefullyWhenMatchSuggestionFailsWithNullMessage() throws IOException {
            Contracts contract = fakeContract();
            ElectronicInvoice saved = fakeInvoice(contract, sampleLineItems());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(localStorageService.storeInvoice(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("invoices/0/1/uuid-invoice.xml");
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenReturn(saved);
            doThrow(new RuntimeException()).when(invoiceMatchingService).computeSuggestion(any());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            ElectronicInvoiceDTO result = electronicInvoiceService.uploadInvoice(CONTRACT_ID, file);

            assertNotNull(result);
            assertEquals(INVOICE_ID, result.id());
        }

        // ---- confirmMatch ----

        @Test
        @Order(27)
        @DisplayName("confirmMatch: delegates to InvoiceMatchingService with the override id and returns the mapped DTO")
        void shouldConfirmMatchWithOverride() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, sampleLineItems());
            FinancialValues matched = new FinancialValues();
            matched.setId(uid(55));
            Users user = new Users();
            user.setUsername("admin");
            invoice.setMatchStatus(InvoiceMatchStatus.CONFIRMED);
            invoice.setMatchedFinancialValue(matched);
            invoice.setMatchedByUser(user);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceMatchingService.confirmMatch(invoice, uid(55), "admin")).thenReturn(invoice);

            ElectronicInvoiceDTO result = electronicInvoiceService.confirmMatch(
                    CONTRACT_ID, INVOICE_ID, new ConfirmInvoiceMatchRequest(uid(55)));

            assertEquals(InvoiceMatchStatus.CONFIRMED, result.matchStatus());
            assertEquals(uid(55), result.matchedFinancialValueId());
            assertEquals("admin", result.matchedByUsername());
            verify(contractAccessGuard).checkManagerCanAccess(contract);
        }

        @Test
        @Order(28)
        @DisplayName("confirmMatch: a null request body confirms whatever was already suggested")
        void shouldConfirmMatchWithNullRequest() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, List.of());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceMatchingService.confirmMatch(invoice, null, "admin")).thenReturn(invoice);

            ElectronicInvoiceDTO result = electronicInvoiceService.confirmMatch(CONTRACT_ID, INVOICE_ID, null);

            assertNotNull(result);
            verify(invoiceMatchingService).confirmMatch(invoice, null, "admin");
        }

        @Test
        @Order(29)
        @DisplayName("confirmMatch: throws ContractNotFoundException when the invoice is missing")
        void shouldThrowWhenConfirmMatchInvoiceNotFound() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.confirmMatch(CONTRACT_ID, INVOICE_ID, null));
        }

        // ---- rejectMatch ----

        @Test
        @Order(30)
        @DisplayName("rejectMatch: delegates to InvoiceMatchingService and returns the mapped DTO")
        void shouldRejectMatch() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, List.of());
            invoice.setMatchStatus(InvoiceMatchStatus.REJECTED);

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(invoiceMatchingService.rejectMatch(invoice)).thenReturn(invoice);

            ElectronicInvoiceDTO result = electronicInvoiceService.rejectMatch(CONTRACT_ID, INVOICE_ID);

            assertEquals(InvoiceMatchStatus.REJECTED, result.matchStatus());
            verify(contractAccessGuard).checkManagerCanAccess(contract);
        }

        @Test
        @Order(31)
        @DisplayName("rejectMatch: throws ContractNotFoundException when the invoice is missing")
        void shouldThrowWhenRejectMatchInvoiceNotFound() {
            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(fakeContract());
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.rejectMatch(CONTRACT_ID, INVOICE_ID));
        }

        // ---- recomputeMatches ----

        @Test
        @Order(32)
        @DisplayName("recomputeMatches: delegates to InvoiceMatchingService and returns the mapped DTOs")
        void shouldRecomputeMatches() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, List.of());

            when(contractAccessGuard.getContractInScope(CONTRACT_ID)).thenReturn(contract);
            when(invoiceMatchingService.recomputeForContract(CONTRACT_ID)).thenReturn(List.of(invoice));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.recomputeMatches(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(INVOICE_ID, result.get(0).id());
            verify(contractAccessGuard).checkManagerCanAccess(contract);
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("FileDownload record")
    @SuppressWarnings("unused")
    class FileDownloadRecord {

        @Test
        @Order(1)
        @DisplayName("equals: same reference returns true")
        void equalsSameReference() {
            FileDownload download = new FileDownload(VALID_XML, "f.xml", "application/xml");
            assertEquals(download, download);
        }

        @Test
        @Order(2)
        @DisplayName("equals: null returns false")
        void equalsNull() {
            FileDownload download = new FileDownload(VALID_XML, "f.xml", "application/xml");
            assertNotEquals(null, download);
        }

        @Test
        @Order(3)
        @DisplayName("equals: different type returns false")
        void equalsDifferentType() {
            FileDownload download = new FileDownload(VALID_XML, "f.xml", "application/xml");
            assertNotEquals("string", download);
        }

        @Test
        @Order(4)
        @DisplayName("equals: same content returns true, hashCode matches")
        void equalsAndHashCode() {
            FileDownload d1 = new FileDownload(VALID_XML, "f.xml", "application/xml");
            FileDownload d2 = new FileDownload(VALID_XML, "f.xml", "application/xml");
            assertEquals(d1, d2);
            assertEquals(d1.hashCode(), d2.hashCode());
        }

        @Test
        @Order(5)
        @DisplayName("equals: returns false when bytes differ")
        void notEqualsDifferentBytes() {
            FileDownload d1 = new FileDownload(VALID_XML, "f.xml", "application/xml");
            FileDownload d2 = new FileDownload(new byte[]{1, 2, 3}, "f.xml", "application/xml");
            assertNotEquals(d1, d2);
        }

        @Test
        @Order(6)
        @DisplayName("equals: returns false when fileName differs")
        void notEqualsDifferentFileName() {
            FileDownload d1 = new FileDownload(VALID_XML, "a.xml", "application/xml");
            FileDownload d2 = new FileDownload(VALID_XML, "b.xml", "application/xml");
            assertNotEquals(d1, d2);
        }

        @Test
        @Order(7)
        @DisplayName("equals: returns false when contentType differs")
        void notEqualsDifferentContentType() {
            FileDownload d1 = new FileDownload(VALID_XML, "f.xml", "application/xml");
            FileDownload d2 = new FileDownload(VALID_XML, "f.xml", "text/xml");
            assertNotEquals(d1, d2);
        }

        @Test
        @Order(8)
        @DisplayName("toString: contains fileName")
        void toStringContainsFileName() {
            FileDownload download = new FileDownload(VALID_XML, "invoice.xml", "application/xml");
            assertTrue(download.toString().contains("invoice.xml"));
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("fillCounterpartyAddress: the supplier's address from its invoice")
    class FillCounterpartyAddress {

        private final FatturaPaInvoiceData.SupplierAddress sede =
                new FatturaPaInvoiceData.SupplierAddress("Via dei Mille 18", "10123", "Torino", "to", "IT");

        private FatturaPaInvoiceData invoiceFrom(String vat) {
            return new FatturaPaInvoiceData("Nuvola Cloud Services S.r.l.", vat, "TD01", "NCS-1",
                    LocalDate.of(2026, Month.OCTOBER, 1), new BigDecimal("5856.00"), "EUR", List.of(),
                    null, null, null, null, null, sede);
        }

        @Test
        @DisplayName("fills every empty field when the invoice's VAT number is the counterparty's")
        void fillsEmptyFields() {
            com.donatodev.bcm_backend.entity.Counterparty cp = com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Nuvola").vatNumber("07824150960").build();

            ElectronicInvoiceService.fillCounterpartyAddress(cp, invoiceFrom("IT07824150960"));

            assertEquals("Via dei Mille 18", cp.getAddress());
            assertEquals("10123", cp.getPostalCode());
            assertEquals("Torino", cp.getCity());
            assertEquals("TO", cp.getProvince());
            assertEquals("IT", cp.getCountry());
        }

        @Test
        @DisplayName("never overwrites what a person entered")
        void keepsExistingValues() {
            com.donatodev.bcm_backend.entity.Counterparty cp = com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Nuvola").vatNumber("IT07824150960").address("Corso Francia 5").city("Rivoli").build();

            ElectronicInvoiceService.fillCounterpartyAddress(cp, invoiceFrom("IT07824150960"));

            assertEquals("Corso Francia 5", cp.getAddress());
            assertEquals("Rivoli", cp.getCity());
            assertEquals("10123", cp.getPostalCode());
        }

        @Test
        @DisplayName("does nothing for an invoice without a Sede, and fills fields holding only spaces")
        void withoutAddressAndBlankFields() {
            com.donatodev.bcm_backend.entity.Counterparty cp = com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Nuvola").vatNumber("IT07824150960").postalCode("  ").province("TO").country("IT").build();
            FatturaPaInvoiceData noSede = new FatturaPaInvoiceData("Nuvola Cloud Services S.r.l.", "IT07824150960",
                    "TD01", "NCS-1", LocalDate.of(2026, Month.OCTOBER, 1), new BigDecimal("5856.00"), "EUR", List.of(),
                    null, null, null, null, null, null);

            ElectronicInvoiceService.fillCounterpartyAddress(cp, noSede);
            assertEquals("  ", cp.getPostalCode());

            ElectronicInvoiceService.fillCounterpartyAddress(cp, invoiceFrom("IT07824150960"));
            assertEquals("10123", cp.getPostalCode());
            assertEquals("TO", cp.getProvince());
            assertEquals("IT", cp.getCountry());

            cp.setPostalCode("10100");
            ElectronicInvoiceService.fillCounterpartyAddress(cp, invoiceFrom("IT07824150960"));
            assertEquals("10100", cp.getPostalCode(), "a postal code typed in stays");
        }

        @Test
        @DisplayName("touches nothing for another supplier's invoice, or a counterparty without a VAT number")
        void ignoresOtherSuppliers() {
            com.donatodev.bcm_backend.entity.Counterparty other = com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Ferri").vatNumber("IT05391280772").build();
            com.donatodev.bcm_backend.entity.Counterparty noVat = com.donatodev.bcm_backend.entity.Counterparty.builder()
                    .name("Nuvola").build();

            ElectronicInvoiceService.fillCounterpartyAddress(other, invoiceFrom("IT07824150960"));
            ElectronicInvoiceService.fillCounterpartyAddress(noVat, invoiceFrom("IT07824150960"));
            ElectronicInvoiceService.fillCounterpartyAddress(null, invoiceFrom("IT07824150960"));

            assertNull(other.getCity());
            assertNull(noVat.getCity());
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("amountDifference: the invoice against what the contract expects")
    class AmountDifference {

        private ElectronicInvoice invoice(BigDecimal taxable, BigDecimal total, Double expected, String type) {
            ElectronicInvoice i = new ElectronicInvoice();
            i.setTaxableAmount(taxable);
            i.setTotalAmount(total);
            i.setDocumentType(type);
            if (expected != null) {
                com.donatodev.bcm_backend.entity.FinancialValues fv = new com.donatodev.bcm_backend.entity.FinancialValues();
                fv.setFinancialAmount(expected);
                i.setMatchedFinancialValue(fv);
            }
            return i;
        }

        @Test
        @DisplayName("is the taxable amount minus the expected instalment")
        void difference() {
            assertEquals(new BigDecimal("150.00"),
                    ElectronicInvoiceService.amountDifference(invoice(new BigDecimal("650.00"), null, 500.0, "TD01")));
        }

        @Test
        @DisplayName("is not given without an expected amount, without any amount, or for a credit note")
        void notGiven() {
            assertNull(ElectronicInvoiceService.amountDifference(invoice(new BigDecimal("650.00"), null, null, "TD01")));
            assertNull(ElectronicInvoiceService.amountDifference(invoice(null, null, 500.0, "TD01")));
            assertNull(ElectronicInvoiceService.amountDifference(
                    invoice(new BigDecimal("650.00"), null, 500.0, ElectronicInvoice.CREDIT_NOTE)));
        }
    }
}
