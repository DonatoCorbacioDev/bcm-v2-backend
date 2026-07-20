package com.donatodev.bcm_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SemanticSearchRequestDTO(@NotBlank String query) {}
