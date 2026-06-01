package com.donatodev.bcm_backend.controller;

import java.net.URI;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.entity.BusinessAreas;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.BusinessAreasRepository;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.S3Service;
import com.donatodev.bcm_backend.service.TextractService;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

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

    // Mock AWS clients so no real AWS calls are made in tests
    @MockitoBean private S3Client s3Client;
    @MockitoBean private S3Presigner s3Presigner;
    @MockitoBean private TextractClient textractClient;

    @Autowired private S3Service s3Service;
    @Autowired private TextractService textractService;

    private Long contractId;

    private static final byte[] VALID_PDF = ("%PDF-1.4 fake pdf content for testing").getBytes();

    @BeforeEach
    void setup() throws Exception {
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
                .businessArea(area).startDate(LocalDate.now())
                .status(ContractStatus.ACTIVE).build());

        contractId = contract.getId();

        // Default S3 mock behaviour
        when(s3Client.putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build());

        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://s3.example.com/signed-url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
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
        @DisplayName("Upload non-PDF file returns 400")
        void shouldRejectNonPdfFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "image.png", "image/png", "not a pdf".getBytes());

            mockMvc.perform(multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Upload to non-existent contract returns 404")
        void shouldReturn404ForMissingContract() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);

            mockMvc.perform(multipart("/contracts/99999/documents").file(file))
                    .andExpect(status().isNotFound());
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
        @DisplayName("Returns empty list when no documents uploaded")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/contracts/" + contractId + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns uploaded documents with download URLs")
        void shouldReturnDocumentsWithUrls() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);
            mockMvc.perform(multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/contracts/" + contractId + "/documents"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].fileName").value("contract.pdf"))
                    .andExpect(jsonPath("$[0].downloadUrl").exists());
        }
    }

    @Nested
    @DisplayName("POST /contracts/{id}/documents/{docId}/extract — Textract")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class Extract {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Extracts text from uploaded document via Textract")
        void shouldExtractTextSuccessfully() throws Exception {
            // Upload first
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);
            String uploadResponse = mockMvc.perform(
                    multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long docId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(uploadResponse).get("id").asLong();

            // Mock Textract response
            DetectDocumentTextResponse textractResponse = DetectDocumentTextResponse.builder()
                    .blocks(List.of(
                            Block.builder().blockType(BlockType.LINE).text("Customer: Test Corp").build(),
                            Block.builder().blockType(BlockType.LINE).text("Total Value: €5,000").build()
                    ))
                    .build();
            when(textractClient.detectDocumentText(any(DetectDocumentTextRequest.class)))
                    .thenReturn(textractResponse);

            mockMvc.perform(post("/contracts/" + contractId + "/documents/" + docId + "/extract"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rawText").exists())
                    .andExpect(jsonPath("$.detectedCustomerName").value("Test Corp"))
                    .andExpect(jsonPath("$.detectedAmount").exists());
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
            MockMultipartFile file = new MockMultipartFile(
                    "file", "contract.pdf", "application/pdf", VALID_PDF);
            String uploadResponse = mockMvc.perform(
                    multipart("/contracts/" + contractId + "/documents").file(file))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long docId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(uploadResponse).get("id").asLong();

            mockMvc.perform(delete("/contracts/" + contractId + "/documents/" + docId))
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
    }
}
