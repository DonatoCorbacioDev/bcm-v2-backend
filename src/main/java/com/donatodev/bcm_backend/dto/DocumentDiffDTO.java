/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.dto;

import java.util.List;

public record DocumentDiffDTO(
        Long fromDocumentId,
        String fromFileName,
        Long toDocumentId,
        String toFileName,
        List<DiffLineDTO> lines
) {}
