/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.ChangeTierRequest;
import com.donatodev.bcm_backend.dto.OrganizationDTO;
import com.donatodev.bcm_backend.security.InternalApiKeyGuard;
import com.donatodev.bcm_backend.service.OrganizationService;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * Ops-only endpoints, never called by the frontend: manual subscription tier
 * changes (support, refunds, a plan granted by hand). Hidden from Swagger
 * since no API consumer should depend on it. Access control is
 * {@link InternalApiKeyGuard} plus the reverse proxy — not JWT.
 */
@Hidden
@RestController
@RequestMapping("/internal/organizations")
public class InternalOrganizationController {

    private final OrganizationService organizationService;
    private final InternalApiKeyGuard internalApiKeyGuard;

    public InternalOrganizationController(OrganizationService organizationService,
            InternalApiKeyGuard internalApiKeyGuard) {
        this.organizationService = organizationService;
        this.internalApiKeyGuard = internalApiKeyGuard;
    }

    @PutMapping("/{id}/tier")
    public ResponseEntity<OrganizationDTO> changeTier(
            @RequestHeader(value = InternalApiKeyGuard.HEADER, required = false) String key,
            @PathVariable Long id,
            @Valid @RequestBody ChangeTierRequest request) {
        internalApiKeyGuard.verify(key);
        return ResponseEntity.ok(organizationService.changeTier(id, request.tier(), "manual"));
    }
}
