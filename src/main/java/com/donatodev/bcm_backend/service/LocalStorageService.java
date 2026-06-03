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

    public String storeDocument(Long orgId, Long contractId, String fileName, byte[] content) {
        String relativePath = String.format("contracts/%d/%d/%s-%s",
                orgId != null ? orgId : 0L, contractId, UUID.randomUUID(), fileName);
        Path target = Paths.get(uploadDir).resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store document", e);
        }
        return relativePath;
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
