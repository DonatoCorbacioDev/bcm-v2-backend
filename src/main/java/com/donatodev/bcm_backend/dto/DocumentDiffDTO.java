package com.donatodev.bcm_backend.dto;

import java.util.List;

public record DocumentDiffDTO(
        Long fromDocumentId,
        String fromFileName,
        Long toDocumentId,
        String toFileName,
        List<DiffLineDTO> lines
) {}
