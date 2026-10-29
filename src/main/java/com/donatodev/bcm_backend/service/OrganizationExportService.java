/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.donatodev.bcm_backend.dto.export.ExportManifest;
import com.donatodev.bcm_backend.dto.export.ExportManifest.ExportedFile;
import com.donatodev.bcm_backend.dto.export.OrganizationExportDTO;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.service.OrganizationExportSections.Section;
import com.donatodev.bcm_backend.service.OrganizationExportSections.StoredFileRef;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Builds the self-service export of an organization's data (Terms sez. 13,
 * GDPR art. 20): one JSON file per kind of record under {@code data/}, the
 * original uploaded documents under {@code files/}, and a manifest.
 */
@Service
public class OrganizationExportService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationExportService.class);

    /** Bumped when a file is renamed or a field changes meaning, so a reader can tell layouts apart. */
    static final int FORMAT_VERSION = 1;
    static final String MANIFEST_PATH = "manifest.json";

    private final OrganizationRepository organizationRepository;
    private final OrganizationExportSections sections;
    private final LocalStorageService storageService;
    private final AuditLogService auditLogService;
    private final ObjectWriter jsonWriter;
    private final TransactionTemplate readOnlyTransaction;

    /** A data file, already serialized. */
    private record JsonFile(String path, byte[] json, int records) {
    }

    /** Everything read from the database, taken in one transaction. */
    private record Snapshot(Long orgId, String orgName, List<JsonFile> dataFiles, List<StoredFileRef> storedFiles) {
    }

    public OrganizationExportService(OrganizationRepository organizationRepository,
            OrganizationExportSections sections, LocalStorageService storageService,
            AuditLogService auditLogService, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.organizationRepository = organizationRepository;
        this.sections = sections;
        this.storageService = storageService;
        this.auditLogService = auditLogService;
        this.jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    /**
     * Records who exported the organization. Called on the request thread,
     * before streaming starts: a full export is exactly what to look for after
     * a suspected leak, so an attempt is logged even if the download breaks.
     */
    public void recordExport(Long orgId) {
        auditLogService.save("DATA_EXPORTED", "Organization", orgId,
                AuthenticatedUserUtils.getUsernameOrThrow(), orgId, "Full data export (ZIP)");
    }

    /**
     * Writes the archive to {@code out}. Takes the orgId instead of reading
     * TenantContext because it runs on the async thread that streams the
     * response, where TenantContext isn't set.
     * <p>
     * Two phases: the database is read in one read-only transaction, so every
     * data file reflects the same moment; the original files are then copied
     * from disk outside it, so a slow download doesn't hold a database
     * connection for minutes.
     */
    public void writeExport(Long orgId, OutputStream out) throws IOException {
        Snapshot snapshot = Objects.requireNonNull(readOnlyTransaction.execute(status -> takeSnapshot(orgId)));

        List<ExportedFile> exported = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (JsonFile file : snapshot.dataFiles()) {
                exported.add(writeEntry(zip, file));
            }
            for (StoredFileRef file : snapshot.storedFiles()) {
                copyStoredFile(zip, file, exported, missing);
            }
            writeEntry(zip, serialize(MANIFEST_PATH, new ExportManifest(FORMAT_VERSION, Instant.now(),
                    snapshot.orgId(), snapshot.orgName(), exported, missing), 1));
        }
        if (!missing.isEmpty()) {
            logger.warn("Export of org {}: {} stored files missing on disk, listed in the manifest",
                    orgId, missing.size());
        }
    }

    private Snapshot takeSnapshot(Long orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("Organizzazione non trovata"));
        List<JsonFile> dataFiles = new ArrayList<>();
        dataFiles.add(serialize("data/organization.json", OrganizationExportDTO.from(org), 1));
        for (Section section : sections.all()) {
            List<?> records = section.loader().apply(orgId);
            dataFiles.add(serialize(section.path(), records, records.size()));
        }
        return new Snapshot(org.getId(), org.getName(), dataFiles, sections.files(orgId));
    }

    /**
     * Serialized in memory: record data is small next to the documents, and
     * having the bytes makes size and digest trivial.
     */
    private JsonFile serialize(String path, Object value, int records) {
        try {
            return new JsonFile(path, jsonWriter.writeValueAsBytes(value), records);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException("Failed to serialize " + path, e);
        }
    }

    private static ExportedFile writeEntry(ZipOutputStream zip, JsonFile file) throws IOException {
        zip.putNextEntry(new ZipEntry(file.path()));
        zip.write(file.json());
        zip.closeEntry();
        return new ExportedFile(file.path(), file.records(), file.json().length,
                HexFormat.of().formatHex(sha256().digest(file.json())));
    }

    /**
     * Streams one original file into the archive in small chunks, hashing it
     * on the way: a 50 MB PDF never sits in memory whole. A file that is gone
     * from disk is listed in the manifest instead of failing the export, so
     * the customer still gets everything else.
     */
    private void copyStoredFile(ZipOutputStream zip, StoredFileRef file,
            List<ExportedFile> exported, List<String> missing) throws IOException {
        InputStream in;
        try {
            in = storageService.openDocument(file.storagePath());
        } catch (NoSuchFileException e) {
            missing.add(file.archivePath());
            return;
        }
        try (in) {
            zip.putNextEntry(new ZipEntry(file.archivePath()));
            // Not closed: closing it would close the ZipOutputStream underneath.
            DigestOutputStream digesting = new DigestOutputStream(zip, sha256());
            long size = in.transferTo(digesting);
            zip.closeEntry();
            exported.add(new ExportedFile(file.archivePath(), null, size,
                    HexFormat.of().formatHex(digesting.getMessageDigest().digest())));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
