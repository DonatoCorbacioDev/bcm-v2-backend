package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.dto.FatturaPaInvoiceData;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ElectronicInvoiceServiceTest {

    @Mock private ElectronicInvoiceRepository invoiceRepository;
    @Mock private ContractsRepository contractsRepository;
    @Mock private LocalStorageService localStorageService;
    @Mock private FatturaPaXmlParserService fatturaPaXmlParserService;
    @Mock private UsersRepository usersRepository;

    private ElectronicInvoiceService electronicInvoiceService;
    private ObjectMapper objectMapper;

    private static final byte[] VALID_XML = "<?xml version=\"1.0\"?><FatturaElettronica></FatturaElettronica>".getBytes();
    private static final long CONTRACT_ID = 1L;
    private static final long INVOICE_ID = 10L;
    private static final String BACKEND_URL = "http://localhost:8090/api/v1";

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        electronicInvoiceService = new ElectronicInvoiceService(
                invoiceRepository, contractsRepository, localStorageService, fatturaPaXmlParserService, objectMapper, usersRepository);
        ReflectionTestUtils.setField(electronicInvoiceService, "backendBaseUrl", BACKEND_URL);
    }

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
                LocalDate.of(2024, Month.MARCH, 15), new BigDecimal("1220.00"), "EUR", sampleLineItems());
    }

    private ElectronicInvoice fakeInvoice(Contracts contract, String lineItemsJson) {
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
        invoice.setLineItemsJson(lineItemsJson);
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
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice saved = fakeInvoice(contract, lineItemsJson);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
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
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.empty());

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(3)
        @DisplayName("uploadInvoice: throws IllegalArgumentException on empty file")
        void shouldThrowOnEmptyFile() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", new byte[0]);

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(4)
        @DisplayName("uploadInvoice: throws IllegalArgumentException when file exceeds 5MB")
        void shouldThrowWhenFileTooLarge() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

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
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.txt", "text/plain", "not xml content".getBytes());

            assertThrows(IllegalArgumentException.class,
                    () -> electronicInvoiceService.uploadInvoice(CONTRACT_ID, file));
        }

        @Test
        @Order(6)
        @DisplayName("uploadInvoice: parser failure leaves no orphaned file or DB row")
        void shouldNotPersistWhenParserFails() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));
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
        void shouldReturnInvoiceList() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(invoice));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(1, result.size());
            assertEquals(INVOICE_ID, result.get(0).id());
            assertEquals(1, result.get(0).lineItems().size());
        }

        @Test
        @Order(8)
        @DisplayName("getInvoices: returns empty list when no invoices")
        void shouldReturnEmptyInvoiceList() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of());

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(0, result.size());
        }

        @Test
        @Order(9)
        @DisplayName("getInvoices: throws ContractNotFoundException when contract missing")
        void shouldThrowWhenContractNotFoundOnList() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.getInvoices(CONTRACT_ID));
        }

        // ---- getInvoice ----

        @Test
        @Order(10)
        @DisplayName("getInvoice: returns DTO with deserialized line items")
        void shouldReturnInvoiceDetail() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            ElectronicInvoiceDTO result = electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID);

            assertEquals(INVOICE_ID, result.id());
            assertEquals(1, result.lineItems().size());
            assertEquals("Servizio di consulenza", result.lineItems().get(0).description());
        }

        @Test
        @Order(11)
        @DisplayName("getInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFound() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- downloadInvoice ----

        @Test
        @Order(12)
        @DisplayName("downloadInvoice: returns bytes and metadata")
        void shouldDownloadInvoice() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));
            when(localStorageService.readDocument(invoice.getStoragePath())).thenReturn(VALID_XML);

            FileDownload result = electronicInvoiceService.downloadInvoice(CONTRACT_ID, INVOICE_ID);

            assertNotNull(result.bytes());
            assertEquals("invoice.xml", result.fileName());
            assertEquals("application/xml", result.contentType());
        }

        @Test
        @Order(13)
        @DisplayName("downloadInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFoundOnDownload() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.downloadInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- deleteInvoice ----

        @Test
        @Order(14)
        @DisplayName("deleteInvoice: deletes from local storage and repository")
        void shouldDeleteInvoice() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            electronicInvoiceService.deleteInvoice(CONTRACT_ID, INVOICE_ID);

            verify(localStorageService).deleteDocument(invoice.getStoragePath());
            verify(invoiceRepository).delete(invoice);
        }

        @Test
        @Order(15)
        @DisplayName("deleteInvoice: throws ContractNotFoundException when invoice missing")
        void shouldThrowWhenInvoiceNotFoundOnDelete() {
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(fakeContract()));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> electronicInvoiceService.deleteInvoice(CONTRACT_ID, INVOICE_ID));
        }

        // ---- TenantContext scoping ----

        @Test
        @Order(16)
        @DisplayName("getInvoices: with TenantContext uses org-scoped contract lookup")
        void shouldGetInvoicesWithTenantContext() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            TenantContext.set(12L);
            try {
                when(contractsRepository.findByIdAndOrganization_Id(CONTRACT_ID, 12L)).thenReturn(Optional.of(contract));
                when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                        .thenReturn(List.of(invoice));

                List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

                assertEquals(1, result.size());
                verify(contractsRepository).findByIdAndOrganization_Id(CONTRACT_ID, 12L);
            } finally {
                TenantContext.clear();
            }
        }

        // ---- lineItemsJson round trip ----

        @Test
        @Order(17)
        @DisplayName("getInvoice: empty lineItemsJson array deserializes to empty list, not null")
        void shouldRoundTripEmptyLineItems() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, "[]");

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            ElectronicInvoiceDTO result = electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID);

            assertNotNull(result.lineItems());
            assertEquals(0, result.lineItems().size());
        }

        @Test
        @Order(18)
        @DisplayName("getInvoice: throws UncheckedIOException when lineItemsJson is malformed")
        void shouldThrowWhenLineItemsJsonIsMalformed() {
            Contracts contract = fakeContract();
            ElectronicInvoice invoice = fakeInvoice(contract, "not valid json");

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(invoiceRepository.findByIdAndContractId(INVOICE_ID, CONTRACT_ID))
                    .thenReturn(Optional.of(invoice));

            assertThrows(UncheckedIOException.class,
                    () -> electronicInvoiceService.getInvoice(CONTRACT_ID, INVOICE_ID));
        }

        @Test
        @Order(19)
        @DisplayName("uploadInvoice: accepts XML content without an XML declaration")
        void shouldAcceptXmlWithoutDeclaration() throws IOException {
            Contracts contract = fakeContract();
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice saved = fakeInvoice(contract, lineItemsJson);

            byte[] xmlWithoutDeclaration = "<FatturaElettronica></FatturaElettronica>".getBytes(StandardCharsets.UTF_8);

            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(fatturaPaXmlParserService.parse(any())).thenReturn(sampleParsedData());
            when(localStorageService.storeInvoice(any(), eq(CONTRACT_ID), any()))
                    .thenReturn("invoices/0/1/uuid-invoice.xml");
            when(invoiceRepository.save(any(ElectronicInvoice.class))).thenReturn(saved);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", xmlWithoutDeclaration);

            ElectronicInvoiceDTO result = electronicInvoiceService.uploadInvoice(CONTRACT_ID, file);

            assertNotNull(result);
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

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Manager access control")
    @SuppressWarnings("unused")
    class ManagerAccessControl {

        @Test
        @Order(1)
        @DisplayName("getInvoices: MANAGER assigned to contract can access invoices")
        void shouldAllowManagerToAccessAssignedContractInvoices() throws Exception {
            Managers manager = Managers.builder().id(7L).build();
            Contracts contract = fakeContract();
            contract.setManager(manager);
            String lineItemsJson = objectMapper.writeValueAsString(sampleLineItems());
            ElectronicInvoice invoice = fakeInvoice(contract, lineItemsJson);

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(manager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(usersRepository.findByUsername("mgr")).thenReturn(Optional.of(managerUser));
            when(invoiceRepository.findByContractIdOrderByUploadedAtDesc(CONTRACT_ID))
                    .thenReturn(List.of(invoice));

            List<ElectronicInvoiceDTO> result = electronicInvoiceService.getInvoices(CONTRACT_ID);

            assertEquals(1, result.size());
            SecurityContextHolder.clearContext();
        }

        @Test
        @Order(2)
        @DisplayName("getInvoices: MANAGER not assigned to contract throws AccessDeniedException")
        void shouldDenyManagerAccessToUnassignedContractInvoices() {
            Managers contractManager = Managers.builder().id(99L).build();
            Managers userManager = Managers.builder().id(7L).build();
            Contracts contract = fakeContract();
            contract.setManager(contractManager);

            Users managerUser = Users.builder()
                    .username("mgr")
                    .role(Roles.builder().role("MANAGER").build())
                    .manager(userManager)
                    .build();
            User principal = new User("mgr", "x", List.of(() -> "ROLE_MANAGER"));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            when(contractsRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contract));
            when(usersRepository.findByUsername("mgr")).thenReturn(Optional.of(managerUser));

            assertThrows(AccessDeniedException.class,
                    () -> electronicInvoiceService.getInvoices(CONTRACT_ID));
            SecurityContextHolder.clearContext();
        }
    }
}
