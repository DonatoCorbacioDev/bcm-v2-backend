/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.util.ArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

    // Every caller builds relativePath with at least one subdirectory segment
    // (see storeDocument/storeInvoice/storeSepaPayment below), so target always
    // has a parent under uploadDir.
    private String store(String relativePath, byte[] content) {
        Path target = resolveWithinRoot(relativePath);
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
    public String storeDocument(UUID orgId, UUID contractId, byte[] content) {
        String relativePath = String.format("contracts/%s/%s/%s.pdf",
                orgId != null ? orgId : "0", contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    // fileName is intentionally excluded from the path to prevent path traversal attacks.
    // The original filename is stored separately in the ElectronicInvoice entity.
    public String storeInvoice(UUID orgId, UUID contractId, byte[] content) {
        String relativePath = String.format("invoices/%s/%s/%s.xml",
                orgId != null ? orgId : "0", contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    // fileName is intentionally excluded from the path to prevent path traversal attacks.
    // The original filename is stored separately in the SepaPaymentBatch entity.
    public String storeSepaPayment(UUID orgId, UUID contractId, byte[] content) {
        String relativePath = String.format("sepa/%s/%s/%s.xml",
                orgId != null ? orgId : "0", contractId, UUID.randomUUID());
        return store(relativePath, content);
    }

    public byte[] readDocument(String storagePath) {
        try {
            return Files.readAllBytes(resolveWithinRoot(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read document", e);
        }
    }

    /**
     * Opens a stored file for streaming, for callers that copy it elsewhere
     * (the organization export) without holding it all in memory. The caller
     * closes the stream; a missing file throws {@link java.nio.file.NoSuchFileException}.
     */
    public InputStream openDocument(String storagePath) throws IOException {
        return Files.newInputStream(resolveWithinRoot(storagePath));
    }

    /** The directories every stored file lives under, one subdirectory per organization id. */
    private static final List<String> ORGANIZATION_ROOTS = List.of("contracts", "invoices", "sepa");

    /**
     * Deletes every stored file of an organization (contract documents,
     * invoices, SEPA files) by removing its directory under each root, so
     * files no row points to any more go too. Used when the organization
     * itself is deleted. Files uploaded before ids became UUIDs (ADR-0009)
     * live under the organization's old numeric id, so both directories go.
     *
     * @param orgId    the organization
     * @param legacyId its numeric id before V55, or null if created afterwards
     * @return how many files were deleted
     */
    public int deleteOrganizationFiles(UUID orgId, Long legacyId) {
        if (orgId == null) {
            throw new IllegalArgumentException("Invalid organization id: null");
        }
        List<String> names = new ArrayList<>(List.of(orgId.toString()));
        // 0 is the shared fallback directory of files stored without an org: never deleted.
        if (legacyId != null && legacyId > 0) {
            names.add(legacyId.toString());
        }
        int deleted = 0;
        for (String prefix : ORGANIZATION_ROOTS) {
            for (String name : names) {
                deleted += deleteDirectory(resolveWithinRoot(prefix + "/" + name), orgId);
            }
        }
        return deleted;
    }

    private static int deleteDirectory(Path dir, UUID orgId) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        // Deepest first, so each directory is empty when its turn comes.
        // Files.walk doesn't follow symbolic links: a link is deleted, never its target.
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isRegularFile(path)) {
                    deleted++;
                }
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete files of organization " + orgId, e);
        }
        return deleted;
    }

    public void deleteDocument(String storagePath) {
        try {
            Files.deleteIfExists(resolveWithinRoot(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete document", e);
        }
    }
}
