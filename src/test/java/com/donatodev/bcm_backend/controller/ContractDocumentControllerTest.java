package com.donatodev.bcm_backend.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.ContractDocumentDTO;
import com.donatodev.bcm_backend.dto.DocumentAnalysisDTO;
import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.ContractDocumentService;
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
class ContractDocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ContractsRepository contractsRepository;
    @Autowired private ContractDocumentRepository documentRepository;
    @Autowired private BusinessAreasRepository businessAreasRepository;
    @Autowired private RolesRepository rolesRepository;
    @Autowired private UsersRepository usersRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean private ContractDocumentService contractDocumentService;

    private Long contractId;

    private static final byte[] VALID_PDF = "%PDF-1.4 fake pdf content for testing".getBytes();

    private ContractDocumentDTO sampleDocDTO(Long contractId) {
        return new ContractDocumentDTO(1L, contractId, "contract.pdf", 1024L,
                "application/pdf", Instant.parse("2027-01-15T12:00:00Z"),
                "http://localhost:8090/api/v1/contracts/" + contractId + "/documents/1/download");
    }

    @BeforeEach
    void setup() {
        refreshTokenRepository.deleteAll();
        documentRepository.deleteAll();
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
    @DisplayName("POST /contracts/{id}/documents — upload")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Upload {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin uploads a valid PDF — returns 201")
        void shouldUploadPdfSuccessfully() throws Exception {
            when(contractDocumentService.uploadDocument(anyLong(), any()))
                    .thenReturn(sampleDocDTO(contractId));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            mockMvc.perform(multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileName").value("contract.pdf"))
                    .andExpect(jsonPath("$.downloadUrl").exists());
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Upload to non-existent contract returns 404")
        void shouldReturn404ForMissingContract() throws Exception {
            when(contractDocumentService.uploadDocument(anyLong(), any()))
                    .thenThrow(new ContractNotFoundException("Contract ID 99999 not found"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            mockMvc.perform(multipart("/contracts/99999/documents").file(file))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Invalid file (non-PDF magic bytes) returns 400")
        void shouldRejectNonPdfFile() throws Exception {
            when(contractDocumentService.uploadDocument(anyLong(), any()))
                    .thenThrow(new IllegalArgumentException("Only PDF files are accepted"));

            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", "not a pdf".getBytes());

            mockMvc.perform(multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(4)
        @DisplayName("Unauthenticated upload returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            mockMvc.perform(multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/documents — list")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class ListDocuments {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns empty list when no documents exist")
        void shouldReturnEmptyList() throws Exception {
            when(contractDocumentService.getDocuments(anyLong())).thenReturn(List.of());

            mockMvc.perform(get("/contracts/" + contractId + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns list of documents with download URLs")
        void shouldReturnDocumentsWithUrls() throws Exception {
            when(contractDocumentService.getDocuments(anyLong()))
                    .thenReturn(List.of(sampleDocDTO(contractId)));

            mockMvc.perform(get("/contracts/" + contractId + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].fileName").value("contract.pdf"))
                    .andExpect(jsonPath("$[0].downloadUrl").exists());
        }
    }

    @Nested
    @DisplayName("GET /contracts/{id}/documents/{docId}/download — download")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Download {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns PDF bytes with correct headers")
        void shouldDownloadDocumentSuccessfully() throws Exception {
            FileDownload download = new FileDownload(
                    VALID_PDF, "contract.pdf", "application/pdf");
            when(contractDocumentService.downloadDocument(anyLong(), anyLong())).thenReturn(download);

            mockMvc.perform(get("/contracts/" + contractId + "/documents/1/download"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(content().bytes(VALID_PDF));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns 404 when document not found")
        void shouldReturn404WhenDocumentNotFound() throws Exception {
            when(contractDocumentService.downloadDocument(anyLong(), anyLong()))
                    .thenThrow(new ContractNotFoundException("Document ID 999 not found"));

            mockMvc.perform(get("/contracts/" + contractId + "/documents/999/download"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(3)
        @DisplayName("Unauthenticated download returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/contracts/" + contractId + "/documents/1/download"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /contracts/{id}/documents/{docId}/extract — PDF analysis")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Extract {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns extracted fields from PDF")
        void shouldExtractTextSuccessfully() throws Exception {
            DocumentAnalysisDTO result = new DocumentAnalysisDTO(
                    1L, "Customer: Test Corp\nTotal: EUR5000",
                    "Test Corp", null, null, null, "EUR5000");

            when(contractDocumentService.extractText(anyLong(), anyLong())).thenReturn(result);

            mockMvc.perform(post("/contracts/" + contractId + "/documents/1/extract"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.detectedCustomerName").value("Test Corp"))
                    .andExpect(jsonPath("$.detectedAmount").value("EUR5000"));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns 404 when document not found")
        void shouldReturn404WhenDocumentNotFound() throws Exception {
            when(contractDocumentService.extractText(anyLong(), anyLong()))
                    .thenThrow(new ContractNotFoundException("Document ID 999 not found"));

            mockMvc.perform(post("/contracts/" + contractId + "/documents/999/extract"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /contracts/{id}/documents/{docId}")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class DeleteDocument {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin deletes a document — returns 204")
        void shouldDeleteSuccessfully() throws Exception {
            doNothing().when(contractDocumentService).deleteDocument(anyLong(), anyLong());

            mockMvc.perform(delete("/contracts/" + contractId + "/documents/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager cannot delete — returns 403")
        void shouldReturn403ForManager() throws Exception {
            mockMvc.perform(delete("/contracts/" + contractId + "/documents/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Delete non-existent document returns 404")
        void shouldReturn404WhenDocumentNotFound() throws Exception {
            doThrow(new ContractNotFoundException("Document ID 999 not found"))
                    .when(contractDocumentService).deleteDocument(anyLong(), anyLong());

            mockMvc.perform(delete("/contracts/" + contractId + "/documents/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
