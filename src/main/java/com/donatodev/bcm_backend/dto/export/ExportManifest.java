/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto.export;

import java.time.Instant;
import java.util.List;

/**
 * {@code manifest.json} of an organization export: what the archive holds and
 * how to check it. It is written last, so an archive cut short by a dropped
 * connection has no manifest and can't be mistaken for a complete one.
 */
public record ExportManifest(
        int formatVersion,
        Instant exportedAt,
        Long organizationId,
        String organizationName,
        List<ExportedFile> files) {

    /**
     * @param records number of records in a {@code data/} file; null for the
     *                original documents under {@code files/}
     * @param sha256  hex digest of the file as stored in the archive
     */
    public record ExportedFile(String path, Integer records, long sizeBytes, String sha256) {
    }
}
