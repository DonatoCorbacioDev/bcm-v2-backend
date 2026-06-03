package com.donatodev.bcm_backend.service;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalStorageService localStorageService;

    private static final byte[] CONTENT = "%PDF-test content".getBytes();

    @BeforeEach
    void setup() {
        localStorageService = new LocalStorageService();
        ReflectionTestUtils.setField(localStorageService, "uploadDir", tempDir.toString());
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: LocalStorageService")
    @SuppressWarnings("unused")
    class VerifyLocalStorageService {

        @Test
        @Order(1)
        @DisplayName("storeDocument: creates file and returns relative path ending in .pdf")
        void shouldStoreDocumentAndReturnPath() {
            String path = localStorageService.storeDocument(1L, 42L, CONTENT);

            assertNotNull(path);
            assertTrue(path.startsWith("contracts/1/42/"));
            assertTrue(path.endsWith(".pdf"));
            assertTrue(Files.exists(tempDir.resolve(path)));
        }

        @Test
        @Order(2)
        @DisplayName("storeDocument: null orgId defaults to 0 in path")
        void shouldUseZeroOrgIdWhenNull() {
            String path = localStorageService.storeDocument(null, 42L, CONTENT);

            assertTrue(path.startsWith("contracts/0/42/"));
        }

        @Test
        @Order(3)
        @DisplayName("storeDocument: creates nested directories automatically")
        void shouldCreateDirectories() {
            localStorageService.storeDocument(5L, 99L, CONTENT);

            assertTrue(Files.isDirectory(tempDir.resolve("contracts/5/99")));
        }

        @Test
        @Order(4)
        @DisplayName("readDocument: returns stored file bytes")
        void shouldReadStoredDocument() {
            String path = localStorageService.storeDocument(1L, 1L, CONTENT);

            byte[] result = localStorageService.readDocument(path);

            assertArrayEquals(CONTENT, result);
        }

        @Test
        @Order(5)
        @DisplayName("readDocument: throws UncheckedIOException when file not found")
        void shouldThrowWhenFileNotFound() {
            assertThrows(java.io.UncheckedIOException.class,
                    () -> localStorageService.readDocument("contracts/0/0/nonexistent.pdf"));
        }

        @Test
        @Order(6)
        @DisplayName("deleteDocument: removes file from disk")
        void shouldDeleteDocument() {
            String path = localStorageService.storeDocument(1L, 1L, CONTENT);
            assertTrue(Files.exists(tempDir.resolve(path)));

            localStorageService.deleteDocument(path);

            assertTrue(Files.notExists(tempDir.resolve(path)));
        }

        @Test
        @Order(7)
        @DisplayName("deleteDocument: does not throw when file does not exist")
        void shouldNotThrowWhenDeletingNonExistentFile() {
            assertDoesNotThrow(() -> localStorageService.deleteDocument("contracts/0/0/ghost.pdf"));
        }

        @Test
        @Order(8)
        @DisplayName("storeDocument: throws UncheckedIOException when upload dir is blocked by a file")
        void shouldThrowWhenDirectoryCannotBeCreated() throws Exception {
            // Place a regular file where storeDocument expects to create a directory
            java.nio.file.Files.write(tempDir.resolve("contracts"), new byte[0]);

            assertThrows(java.io.UncheckedIOException.class,
                    () -> localStorageService.storeDocument(1L, 42L, CONTENT));
        }

        @Test
        @Order(9)
        @DisplayName("deleteDocument: throws UncheckedIOException when path is a non-empty directory")
        void shouldThrowWhenDeleteTargetIsNonEmptyDirectory() throws Exception {
            String fakePath = "contracts/1/1/doc.pdf";
            Path target = tempDir.resolve(fakePath);
            Files.createDirectories(target);
            Files.createFile(target.resolve("child.txt"));

            assertThrows(java.io.UncheckedIOException.class,
                    () -> localStorageService.deleteDocument(fakePath));
        }
    }
}
