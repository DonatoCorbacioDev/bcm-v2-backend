package com.donatodev.bcm_backend.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ElectronicInvoiceDTO;
import com.donatodev.bcm_backend.dto.InvoiceLineItemDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.ElectronicInvoiceService;
import com.donatodev.bcm_backend.service.FileDownload;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ElectronicInvoiceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private ElectronicInvoiceRepository electronicInvoiceRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean private ElectronicInvoiceService electronicInvoiceService;

    private Long contractId;

    private static final byte[] VALID_XML = "<?xml version=\"1.0\"?><FatturaElettronica></FatturaElettronica>".getBytes();

    private List<InvoiceLineItemDTO> sampleLineItems() {
        return List.of(new InvoiceLineItemDTO(1, "Servizio di consulenza", new BigDecimal("10.00"),
                "HUR", new BigDecimal("100.00"), new BigDecimal("1000.00"), new BigDecimal("22.00")));
    }

    private ElectronicInvoiceDTO sampleInvoiceDTO(Long contractId) {
        return new ElectronicInvoiceDTO(1L, contractId, "invoice.xml", (long) VALID_XML.length,
                Instant.parse("2027-01-15T12:00:00Z"),
                "http://localhost:8090/api/v1/contracts/" + contractId + "/invoices/1/download",
                "Acme Forniture S.r.l.", "IT12345678901", "TD01", "2024/001",
                LocalDate.of(2024, Month.MARCH, 15), new BigDecimal("1220.00"), "EUR", sampleLineItems());
    }

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        electronicInvoiceRepository.deleteAll();
        contractsRepository.deleteAll();
        businessAreasRepository.deleteAll();
        usersRepository.deleteAll();
        rolesRepository.deleteAll();

        Roles role = rolesRepository.save(Roles.builder().role("ADMIN").build());
        rolesRepository.save(Roles.builder().role("MANAGER").build());
        usersRepository.save(Users.builder().username("admin").passwordHash("pw")
                .verified(true).role(role).build());

        BusinessAreas area = businessAreasRepository.save(
                BusinessAreas.builder().name("IT").description("IT dept").build());

        Contracts contract = contractsRepository.save(Contracts.builder()
                .customerName("Acme").contractNumber("CTR-001")
                .businessArea(area).startDate(LocalDate.of(2027, Month.JUNE, 15))
                .status(ContractStatus.ACTIVE).build());

        contractId = contract.getId();
    }

    @Nested
    @DisplayName("POST /contracts/{id}/invoices — upload")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Upload {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin uploads a valid FatturaPA XML — returns 201")
        void shouldUploadInvoiceSuccessfully() throws Exception {
            when(electronicInvoiceService.uploadInvoice(anyLong(), any()))
                    .thenReturn(sampleInvoiceDTO(contractId));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            mockMvc.perform(multipart("/contracts/" + contractId + "/invoices").file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.supplierName").value("Acme Forniture S.r.l."))
                    .andExpect(jsonPath("$.invoiceNumber").value("2024/001"))
                    .andExpect(jsonPath("$.lineItems", hasSize(1)));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Upload to non-existent contract returns 404")
        void shouldReturn404ForMissingContract() throws Exception {
            when(electronicInvoiceService.uploadInvoice(anyLong(), any()))
                    .thenThrow(new ContractNotFoundException("Contract ID 99999 not found"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            mockMvc.perform(multipart("/contracts/99999/invoices").file(file))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Invalid XML returns 400")
        void shouldRejectInvalidXml() throws Exception {
            when(electronicInvoiceService.uploadInvoice(anyLong(), any()))
                    .thenThrow(new IllegalArgumentException("Invalid or malformed XML"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", "not xml".getBytes());

            mockMvc.perform(multipart("/contracts/" + contractId + "/invoices").file(file))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(4)
        @DisplayName("Unauthenticated upload returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "invoice.xml", "application/xml", VALID_XML);

            mockMvc.perform(multipart("/contracts/" + contractId + "/invoices").file(file))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/invoices — list")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class ListInvoices {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns empty list when no invoices exist")
        void shouldReturnEmptyList() throws Exception {
            when(electronicInvoiceService.getInvoices(anyLong())).thenReturn(List.of());

            mockMvc.perform(get("/contracts/" + contractId + "/invoices"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns list of invoices with line items")
        void shouldReturnInvoicesWithLineItems() throws Exception {
            when(electronicInvoiceService.getInvoices(anyLong()))
                    .thenReturn(List.of(sampleInvoiceDTO(contractId)));

            mockMvc.perform(get("/contracts/" + contractId + "/invoices"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].invoiceNumber").value("2024/001"))
                    .andExpect(jsonPath("$[0].lineItems", hasSize(1)));
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/invoices/{invoiceId} — detail")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class GetInvoiceDetail {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns invoice detail with line items")
        void shouldReturnInvoiceDetail() throws Exception {
            when(electronicInvoiceService.getInvoice(anyLong(), anyLong()))
                    .thenReturn(sampleInvoiceDTO(contractId));

            mockMvc.perform(get("/contracts/" + contractId + "/invoices/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supplierVatNumber").value("IT12345678901"))
                    .andExpect(jsonPath("$.lineItems[0].description").value("Servizio di consulenza"));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns 404 when invoice not found")
        void shouldReturn404WhenInvoiceNotFound() throws Exception {
            when(electronicInvoiceService.getInvoice(anyLong(), anyLong()))
                    .thenThrow(new ContractNotFoundException("Invoice ID 999 not found for contract " + contractId));

            mockMvc.perform(get("/contracts/" + contractId + "/invoices/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/invoices/{invoiceId}/download — download")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Download {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns XML bytes with correct headers")
        void shouldDownloadInvoiceSuccessfully() throws Exception {
            FileDownload download = new FileDownload(VALID_XML, "invoice.xml", "application/xml");
            when(electronicInvoiceService.downloadInvoice(anyLong(), anyLong())).thenReturn(download);

            mockMvc.perform(get("/contracts/" + contractId + "/invoices/1/download"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/xml"))
                    .andExpect(content().bytes(VALID_XML));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns 404 when invoice not found")
        void shouldReturn404WhenInvoiceNotFound() throws Exception {
            when(electronicInvoiceService.downloadInvoice(anyLong(), anyLong()))
                    .thenThrow(new ContractNotFoundException("Invoice ID 999 not found for contract " + contractId));

            mockMvc.perform(get("/contracts/" + contractId + "/invoices/999/download"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(3)
        @DisplayName("Unauthenticated download returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/contracts/" + contractId + "/invoices/1/download"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /contracts/{id}/invoices/{invoiceId}")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class DeleteInvoice {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin deletes an invoice — returns 204")
        void shouldDeleteSuccessfully() throws Exception {
            doNothing().when(electronicInvoiceService).deleteInvoice(anyLong(), anyLong());

            mockMvc.perform(delete("/contracts/" + contractId + "/invoices/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager cannot delete — returns 403")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(delete("/contracts/" + contractId + "/invoices/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Delete non-existent invoice returns 404")
        void shouldReturn404WhenInvoiceNotFound() throws Exception {
            doThrow(new ContractNotFoundException("Invoice ID 999 not found for contract " + contractId))
                    .when(electronicInvoiceService).deleteInvoice(anyLong(), anyLong());

            mockMvc.perform(delete("/contracts/" + contractId + "/invoices/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
