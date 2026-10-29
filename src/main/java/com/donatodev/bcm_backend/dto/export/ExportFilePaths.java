/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.util.regex.Pattern;

import com.donatodev.bcm_backend.entity.ContractDocument;
import com.donatodev.bcm_backend.entity.ElectronicInvoice;
import com.donatodev.bcm_backend.entity.SepaPaymentBatch;

/**
 * Where each original file goes inside the export archive:
 * {@code files/<kind>/<contractId>/<id>-<original name>}. The same path is
 * written in the file's metadata record ({@code archivePath}), so the JSON
 * points at the file.
 */
public final class ExportFilePaths {

    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N}._ -]");
    private static final int MAX_NAME_LENGTH = 120;

    private ExportFilePaths() {
    }

    public static String of(ContractDocument document) {
        return path("contracts", document.getContract().getId(), document.getId(), document.getFileName());
    }

    public static String of(ElectronicInvoice invoice) {
        return path("invoices", invoice.getContract().getId(), invoice.getId(), invoice.getFileName());
    }

    public static String of(SepaPaymentBatch batch) {
        return path("sepa", batch.getContract().getId(), batch.getId(), batch.getFileName());
    }

    private static String path(String kind, Long contractId, Long id, String fileName) {
        return "files/%s/%d/%d-%s".formatted(kind, contractId, id, safeFileName(fileName));
    }

    /**
     * The original name is whatever the uploader's browser sent. In an archive
     * entry, "/" or "\" would create folders and "../" could make an unzip tool
     * write outside the destination ("zip slip"), so only letters, digits and
     * {@code . _ -} and spaces survive, and the name can't start with a dot.
     * The id prefix keeps two files with the same name apart.
     */
    static String safeFileName(String fileName) {
        String safe = UNSAFE.matcher(fileName == null ? "" : fileName).replaceAll("_");
        safe = safe.replaceFirst("^\\.+", "_");
        if (safe.length() > MAX_NAME_LENGTH) {
            safe = safe.substring(safe.length() - MAX_NAME_LENGTH); // keep the extension
        }
        return safe.isBlank() ? "file" : safe;
    }
}
