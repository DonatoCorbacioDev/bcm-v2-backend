/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.donatodev.bcm_backend.dto.export.ExportManifest;
import com.donatodev.bcm_backend.dto.export.ExportManifest.ExportedFile;
import com.donatodev.bcm_backend.dto.export.OrganizationExportDTO;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.util.AuthenticatedUserUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Builds the self-service export of an organization's data (Terms sez. 13,
 * GDPR art. 20): one JSON file per kind of record under {@code data/}, the
 * original uploaded documents under {@code files/}, and a manifest.
 */
@Service
public class OrganizationExportService {

    /** Bumped when a file is renamed or a field changes meaning, so a reader can tell layouts apart. */
    static final int FORMAT_VERSION = 1;
    static final String MANIFEST_PATH = "manifest.json";

    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final ObjectWriter jsonWriter;

    public OrganizationExportService(OrganizationRepository organizationRepository,
            AuditLogService auditLogService, ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.auditLogService = auditLogService;
        this.jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();
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
     * response, where TenantContext isn't set. One read-only transaction for
     * the whole export, so every file reflects the same moment.
     */
    @Transactional(readOnly = true)
    public void writeExport(Long orgId, OutputStream out) throws IOException {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("Organizzazione non trovata"));

        List<ExportedFile> files = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            files.add(writeJson(zip, "data/organization.json", OrganizationExportDTO.from(org), 1));

            // Step 2: the other data/*.json files, each a JSON array, e.g.
            // List<ManagerExportDTO> managers = ...;
            // files.add(writeJson(zip, "data/managers.json", managers, managers.size()));

            writeJson(zip, MANIFEST_PATH,
                    new ExportManifest(FORMAT_VERSION, Instant.now(), org.getId(), org.getName(), files), 1);
        }
    }

    /**
     * Serialized in memory first: record data is small next to the documents,
     * and having the bytes makes size and digest trivial. The original files
     * (step 3) are the part that must be streamed.
     */
    private ExportedFile writeJson(ZipOutputStream zip, String path, Object value, int records) throws IOException {
        byte[] json = jsonWriter.writeValueAsBytes(value);
        zip.putNextEntry(new ZipEntry(path));
        zip.write(json);
        zip.closeEntry();
        return new ExportedFile(path, records, json.length, HexFormat.of().formatHex(sha256().digest(json)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is mandatory on every JVM", e);
        }
    }
}
