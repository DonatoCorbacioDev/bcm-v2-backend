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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.CreateSepaPaymentRequest;
import com.donatodev.bcm_backend.dto.SepaPaymentBatchDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.FileDownload;
import com.donatodev.bcm_backend.service.SepaPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SepaPaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private SepaPaymentService sepaPaymentService;

    private Long contractId;

    private static final byte[] SAMPLE_XML = "<Document/>".getBytes();

    private SepaPaymentBatchDTO sampleBatchDTO() {
        return new SepaPaymentBatchDTO(1L, contractId, LocalDate.of(2027, Month.JULY, 1),
                new BigDecimal("150.50"), "EUR", 2, "sepa-1-2027-07-01.xml", Instant.parse("2027-06-30T10:00:00Z"));
    }

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
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
                .customerName("Acme").contractNumber("CTR-SEPA-001")
                .businessArea(area).startDate(LocalDate.of(2027, Month.JUNE, 15))
                .status(ContractStatus.ACTIVE).build());

        contractId = contract.getId();
    }

    @Nested
    @DisplayName("POST /contracts/{id}/sepa-payments")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class CreatePayment {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin generates a SEPA payment — returns 201 with XML attachment")
        void shouldCreateAsAdmin() throws Exception {
            FileDownload download = new FileDownload(SAMPLE_XML, "sepa-1.xml", "application/xml");
            when(sepaPaymentService.createSepaPayment(anyLong(), any(), any())).thenReturn(download);

            CreateSepaPaymentRequest request = new CreateSepaPaymentRequest(List.of(1L, 2L), null);

            mockMvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType("application/xml"))
                    .andExpect(content().bytes(SAMPLE_XML));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager generates a SEPA payment — returns 201")
        void shouldCreateAsManager() throws Exception {
            FileDownload download = new FileDownload(SAMPLE_XML, "sepa-1.xml", "application/xml");
            when(sepaPaymentService.createSepaPayment(anyLong(), any(), any())).thenReturn(download);

            CreateSepaPaymentRequest request = new CreateSepaPaymentRequest(List.of(1L), null);

            mockMvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @Order(3)
        @WithMockUser
        @DisplayName("Plain user cannot generate — returns 403")
        void shouldReturn403ForPlainUser() throws Exception {
            CreateSepaPaymentRequest request = new CreateSepaPaymentRequest(List.of(1L), null);

            mockMvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(4)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Empty invoice list fails validation — returns 400")
        void shouldReturn400ForEmptyInvoiceList() throws Exception {
            CreateSepaPaymentRequest request = new CreateSepaPaymentRequest(List.of(), null);

            mockMvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(5)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Service rejects invalid selection — returns 400")
        void shouldReturn400WhenServiceRejects() throws Exception {
            when(sepaPaymentService.createSepaPayment(anyLong(), any(), any()))
                    .thenThrow(new IllegalArgumentException("Organization has no IBAN configured for SEPA payments"));

            CreateSepaPaymentRequest request = new CreateSepaPaymentRequest(List.of(1L), null);

            mockMvc.perform(post("/contracts/" + contractId + "/sepa-payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/sepa-payments")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class ListPayments {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns the list of generated batches")
        void shouldListPayments() throws Exception {
            when(sepaPaymentService.getPayments(anyLong())).thenReturn(List.of(sampleBatchDTO()));

            mockMvc.perform(get("/contracts/" + contractId + "/sepa-payments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].fileName").value("sepa-1-2027-07-01.xml"));
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/sepa-payments/{batchId}/download")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class DownloadPayment {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns XML bytes with correct headers")
        void shouldDownloadSuccessfully() throws Exception {
            FileDownload download = new FileDownload(SAMPLE_XML, "sepa-1.xml", "application/xml");
            when(sepaPaymentService.downloadPayment(anyLong(), anyLong())).thenReturn(download);

            mockMvc.perform(get("/contracts/" + contractId + "/sepa-payments/1/download"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/xml"))
                    .andExpect(content().bytes(SAMPLE_XML));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns 404 when batch not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(sepaPaymentService.downloadPayment(anyLong(), anyLong()))
                    .thenThrow(new ContractNotFoundException("SEPA payment batch ID 999 not found for contract " + contractId));

            mockMvc.perform(get("/contracts/" + contractId + "/sepa-payments/999/download"))
                    .andExpect(status().isNotFound());
        }
    }
}
