/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.upload;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.entity.StoredFile;
import com.donatodev.bcm_backend.exception.MalwareScanUnavailableException;
import com.donatodev.bcm_backend.repository.ContractDocumentRepository;
import com.donatodev.bcm_backend.repository.ElectronicInvoiceRepository;
import com.donatodev.bcm_backend.security.SecurityEvents;
import com.donatodev.bcm_backend.service.LocalStorageService;

/**
 * Puts the files stored before the upload checks existed (contract PDFs and
 * FatturaPA invoices) through the same checks once: antivirus, and for a PDF
 * its structure and active content. Report only: nothing is changed, moved or
 * deleted. The summary and each finding (by id, never by file name) go to
 * the log, and each finding is also recorded as an {@code UNSAFE_UPLOAD}
 * security event, so it reaches the weekly report.
 * <p>
 * Off unless {@code app.upload-rescan.enabled=true}: switched on for one
 * start, then off again. It refuses to run with the antivirus turned off, and
 * stops at the first file ClamAV cannot answer for, rather than call files clean
 * that were never scanned. SEPA files are not rechecked: the application
 * writes them itself.
 */
@Component
public class StoredFileRescan {

    private static final Logger logger = LoggerFactory.getLogger(StoredFileRescan.class);

    /** What a run found. {@code complete} is false when it stopped before the end. */
    public record Report(int checked, int clean, int suspicious, int missing, boolean complete) {
    }

    private final boolean enabled;
    private final UploadChecks checks;
    private final ContractDocumentRepository documents;
    private final ElectronicInvoiceRepository invoices;
    private final LocalStorageService storage;
    private final SecurityEvents securityEvents;

    public StoredFileRescan(@Value("${app.upload-rescan.enabled:false}") boolean enabled,
                            UploadChecks checks,
                            ContractDocumentRepository documents,
                            ElectronicInvoiceRepository invoices,
                            LocalStorageService storage,
                            SecurityEvents securityEvents) {
        this.enabled = enabled;
        this.checks = checks;
        this.documents = documents;
        this.invoices = invoices;
        this.storage = storage;
        this.securityEvents = securityEvents;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            run();
        }
    }

    /** Rechecks every stored document and invoice; see the class comment. */
    public Report run() {
        if (!checks.scanning()) {
            logger.warn("Stored file recheck not run: malware scanning is turned off");
            return new Report(0, 0, 0, 0, false);
        }
        logger.info("Stored file recheck started");
        Tally tally = new Tally();
        try {
            documents.findAll().forEach(file -> check(file, "ContractDocument", checks::recheckPdf, tally));
            invoices.findAll().forEach(file -> check(file, "ElectronicInvoice", checks::recheckFatturaPa, tally));
        } catch (MalwareScanUnavailableException e) {
            logger.warn("Stored file recheck stopped: ClamAV did not answer ({} checked so far)", tally.checked);
            return tally.report(false);
        }
        Report report = tally.report(true);
        logger.info("Stored file recheck done: {} checked, {} clean, {} suspicious, {} missing on disk",
                report.checked(), report.clean(), report.suspicious(), report.missing());
        return report;
    }

    private void check(StoredFile file, String entityType, Function<byte[], Optional<String>> recheck, Tally tally) {
        byte[] bytes;
        try {
            bytes = storage.readDocument(file.getStoragePath());
        } catch (RuntimeException e) {
            tally.missing++;
            if (logger.isWarnEnabled()) {
                logger.warn("Stored file recheck: {} {} could not be read ({})",
                        entityType, oneLine(String.valueOf(file.getId())), e.getClass().getSimpleName());
            }
            return;
        }
        tally.checked++;
        Optional<String> finding = recheck.apply(bytes);
        if (finding.isEmpty()) {
            tally.clean++;
            return;
        }
        tally.suspicious++;
        UUID id = file.getId();
        if (logger.isWarnEnabled()) {
            logger.warn("Stored file recheck: {} {} of organization {} would be refused today: {}",
                    entityType, oneLine(String.valueOf(id)), oneLine(String.valueOf(file.getOrgId())),
                    oneLine(finding.get()));
        }
        securityEvents.recordAbout(SecurityEvents.Type.UNSAFE_UPLOAD, entityType, id, file.getOrgId(),
                "stored file: " + finding.get());
    }

    /** The finding comes from ClamAV and the PDF: one log line whatever it holds. */
    private static String oneLine(String value) {
        return value.replace('\r', '_').replace('\n', '_');
    }

    private static final class Tally {
        private int checked;
        private int clean;
        private int suspicious;
        private int missing;

        private Report report(boolean complete) {
            return new Report(checked, clean, suspicious, missing, complete);
        }
    }
}
