package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
            String path = localStorageService.storeDocument(uid(1), uid(42), CONTENT);

            assertNotNull(path);
            assertTrue(path.startsWith("contracts/" + uid(1) + "/" + uid(42) + "/"));
            assertTrue(path.endsWith(".pdf"));
            assertTrue(Files.exists(tempDir.resolve(path)));
        }

        @Test
        @Order(2)
        @DisplayName("storeDocument: null orgId defaults to 0 in path")
        void shouldUseZeroOrgIdWhenNull() {
            String path = localStorageService.storeDocument(null, uid(42), CONTENT);

            assertTrue(path.startsWith("contracts/0/" + uid(42) + "/"));
        }

        @Test
        @Order(3)
        @DisplayName("storeDocument: creates nested directories automatically")
        void shouldCreateDirectories() {
            localStorageService.storeDocument(uid(5), uid(99), CONTENT);

            assertTrue(Files.isDirectory(tempDir.resolve("contracts/" + uid(5) + "/" + uid(99))));
        }

        @Test
        @Order(4)
        @DisplayName("readDocument: returns stored file bytes")
        void shouldReadStoredDocument() {
            String path = localStorageService.storeDocument(uid(1), uid(1), CONTENT);

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
            String path = localStorageService.storeDocument(uid(1), uid(1), CONTENT);
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

            UUID id1 = uid(1);
            UUID id42 = uid(42);
            assertThrows(java.io.UncheckedIOException.class,
                    () -> localStorageService.storeDocument(id1, id42, CONTENT));
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

        @Test
        @Order(10)
        @DisplayName("storeInvoice: creates file and returns relative path ending in .xml")
        void shouldStoreInvoiceAndReturnPath() {
            String path = localStorageService.storeInvoice(uid(1), uid(42), CONTENT);

            assertNotNull(path);
            assertTrue(path.startsWith("invoices/" + uid(1) + "/" + uid(42) + "/"));
            assertTrue(path.endsWith(".xml"));
            assertTrue(Files.exists(tempDir.resolve(path)));
        }

        @Test
        @Order(11)
        @DisplayName("storeInvoice: null orgId defaults to 0 in path")
        void shouldUseZeroOrgIdWhenNullForInvoice() {
            String path = localStorageService.storeInvoice(null, uid(42), CONTENT);

            assertTrue(path.startsWith("invoices/0/" + uid(42) + "/"));
        }

        @Test
        @Order(12)
        @DisplayName("storeSepaPayment: creates file and returns relative path ending in .xml")
        void shouldStoreSepaPaymentAndReturnPath() {
            String path = localStorageService.storeSepaPayment(uid(1), uid(42), CONTENT);

            assertNotNull(path);
            assertTrue(path.startsWith("sepa/" + uid(1) + "/" + uid(42) + "/"));
            assertTrue(path.endsWith(".xml"));
            assertTrue(Files.exists(tempDir.resolve(path)));
        }

        @Test
        @Order(13)
        @DisplayName("storeSepaPayment: null orgId defaults to 0 in path")
        void shouldUseZeroOrgIdWhenNullForSepaPayment() {
            String path = localStorageService.storeSepaPayment(null, uid(42), CONTENT);

            assertTrue(path.startsWith("sepa/0/" + uid(42) + "/"));
        }

        @Test
        @Order(14)
        @DisplayName("readDocument: rejects a path that escapes the upload root")
        void shouldRejectPathEscapingUploadRoot() {
            assertThrows(SecurityException.class,
                    () -> localStorageService.readDocument("../../../../etc/passwd"));
        }
            @Test
        @DisplayName("deleteOrganizationFiles: removes the organization's documents, invoices and SEPA files only")
        void shouldDeleteOnlyThatOrganizationsFiles() {
            String doc = localStorageService.storeDocument(uid(7), uid(1), CONTENT);
            String invoice = localStorageService.storeInvoice(uid(7), uid(2), CONTENT);
            String sepa = localStorageService.storeSepaPayment(uid(7), uid(1), CONTENT);
            String otherOrg = localStorageService.storeDocument(uid(8), uid(1), CONTENT);
            // A file no row points to any more goes too, and so does the folder
            // named after the organization's numeric id from before V55 (ADR-0009).
            assertDoesNotThrow(() -> Files.writeString(tempDir.resolve("contracts/" + uid(7) + "/orphan.pdf"), "x"));
            assertDoesNotThrow(() -> Files.createDirectories(tempDir.resolve("contracts/7")));
            assertDoesNotThrow(() -> Files.writeString(tempDir.resolve("contracts/7/before-v55.pdf"), "x"));

            int deleted = localStorageService.deleteOrganizationFiles(uid(7), 7L);

            assertEquals(5, deleted);
            for (String path : new String[] {doc, invoice, sepa}) {
                assertFalse(Files.exists(tempDir.resolve(path)), path);
            }
            assertFalse(Files.exists(tempDir.resolve("contracts/" + uid(7))));
            assertFalse(Files.exists(tempDir.resolve("contracts/7")));
            assertTrue(Files.exists(tempDir.resolve(otherOrg)));
        }

        @Test
        @DisplayName("deleteOrganizationFiles: a file that can't be removed is reported, not silently left")
        void shouldReportFileThatCannotBeDeleted() throws Exception {
            String doc = localStorageService.storeDocument(uid(6), uid(1), CONTENT);
            java.nio.file.Path file = tempDir.resolve(doc);
            java.nio.file.Path parent = file.getParent();
            boolean posix = java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
            // POSIX: a file can't be removed from a directory without write permission.
            // Windows: a read-only file can't be removed.
            if (posix) {
                Files.setPosixFilePermissions(parent, java.nio.file.attribute.PosixFilePermissions.fromString("r-x------"));
            } else {
                Files.setAttribute(file, "dos:readonly", true);
            }
            try {
                UUID id6 = uid(6);
                assertThrows(java.io.UncheckedIOException.class, () -> localStorageService.deleteOrganizationFiles(id6, null));
            } finally {
                if (posix) {
                    Files.setPosixFilePermissions(parent, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
                } else {
                    Files.setAttribute(file, "dos:readonly", false);
                }
            }
        }

        @Test
        @DisplayName("deleteOrganizationFiles: nothing stored yet deletes nothing")
        void shouldDeleteNothingWhenNoFiles() {
            assertEquals(0, localStorageService.deleteOrganizationFiles(uid(9), null));
        }

        @Test
        @DisplayName("deleteOrganizationFiles: refuses a missing organization")
        void shouldRefuseMissingOrganization() {
            assertThrows(IllegalArgumentException.class, () -> localStorageService.deleteOrganizationFiles(null, null));
        }

        @Test
        @DisplayName("deleteOrganizationFiles: never touches the shared fallback directory 0, even as an old id")
        void shouldNeverDeleteFallbackDirectory() {
            String shared = localStorageService.storeDocument(null, uid(1), CONTENT);

            assertEquals(0, localStorageService.deleteOrganizationFiles(uid(9), 0L));
            assertTrue(Files.exists(tempDir.resolve(shared)));
        }
    }
}
