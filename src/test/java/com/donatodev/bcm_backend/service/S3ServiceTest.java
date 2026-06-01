package com.donatodev.bcm_backend.service;

import java.net.URI;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedGetObjectRequest presignedRequest;

    private S3Service s3Service;

    @BeforeEach
    void setup() {
        s3Service = new S3Service(s3Client, s3Presigner);
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
    }

    @Nested
    @DisplayName("Unit Test: S3Service")
    @org.junit.jupiter.api.TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @SuppressWarnings("unused")
    class VerifyS3Service {

        @Test
        @Order(1)
        @DisplayName("uploadDocument calls S3 putObject and returns an S3 key")
        void shouldUploadDocumentAndReturnKey() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            byte[] content = "%PDF-test content".getBytes();
            String key = s3Service.uploadDocument(1L, 42L, "contract.pdf", "application/pdf", content);

            assertNotNull(key);
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @Order(2)
        @DisplayName("generatePresignedUrl returns a URL string")
        void shouldGeneratePresignedUrl() throws Exception {
            when(s3Presigner.presignGetObject(
                    ArgumentMatchers.<Consumer<GetObjectPresignRequest.Builder>>any()))
                    .thenReturn(presignedRequest);
            when(presignedRequest.url()).thenReturn(URI.create("https://s3.amazonaws.com/test-bucket/key").toURL());

            String url = s3Service.generatePresignedUrl("contracts/1/42/uuid-contract.pdf");

            assertNotNull(url);
            assertEquals("https://s3.amazonaws.com/test-bucket/key", url);
        }

        @Test
        @Order(3)
        @DisplayName("deleteDocument calls S3 deleteObject")
        void shouldDeleteDocument() {
            s3Service.deleteDocument("contracts/1/42/uuid-contract.pdf");
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }
    }
}
