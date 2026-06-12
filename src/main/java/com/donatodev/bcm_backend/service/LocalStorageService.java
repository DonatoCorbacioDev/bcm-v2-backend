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

    private String store(String relativePath, byte[] content) {
        Path target = Paths.get(uploadDir).resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
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

    public byte[] readDocument(String storagePath) {
        try {
            return Files.readAllBytes(Paths.get(uploadDir).resolve(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read document", e);
        }
    }

    public void deleteDocument(String storagePath) {
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document", e);
        }
    }
}
