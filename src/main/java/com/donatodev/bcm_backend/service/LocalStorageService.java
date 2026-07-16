package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalStorageService {

    @Value("${storage.upload-dir:uploads}")
    private String uploadDir;

    // Every caller in this class builds relativePath itself from numeric IDs
    // and a UUID (never from raw user input), but resolveWithinRoot() still
    // rejects anything that would escape uploadDir (e.g. "../"), so the
    // guarantee holds even if that ever changes.
    private Path resolveWithinRoot(String relativePath) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new SecurityException("Storage path escapes upload root: " + relativePath);
        }
        return target;
    }

    private String store(String relativePath, byte[] content) {
        Path target = resolveWithinRoot(relativePath);
        Path parent = target.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store document", e);
        }
        return relativePath;
    }

    // fileName is intentionally excluded from the path to prevent path traversal attacks.
    // The original filename is stored separately in the ContractDocument entity.
    public String storeDocument(Long orgId, Long contractId, byte[] content) {
        String relativePath = String.format("contracts/%d/%d/%s.pdf",
                orgId != null ? orgId : 0L, contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    // fileName is intentionally excluded from the path to prevent path traversal attacks.
    // The original filename is stored separately in the ElectronicInvoice entity.
    public String storeInvoice(Long orgId, Long contractId, byte[] content) {
        String relativePath = String.format("invoices/%d/%d/%s.xml",
                orgId != null ? orgId : 0L, contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    // fileName is intentionally excluded from the path to prevent path traversal attacks.
    // The original filename is stored separately in the SepaPaymentBatch entity.
    public String storeSepaPayment(Long orgId, Long contractId, byte[] content) {
        String relativePath = String.format("sepa/%d/%d/%s.xml",
                orgId != null ? orgId : 0L, contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    public byte[] readDocument(String storagePath) {
        try {
            return Files.readAllBytes(resolveWithinRoot(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read document", e);
        }
    }

    public void deleteDocument(String storagePath) {
        try {
            Files.deleteIfExists(resolveWithinRoot(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document", e);
        }
    }
}
